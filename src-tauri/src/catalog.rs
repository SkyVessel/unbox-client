//! Modrinth browsing and staged, profile-bound installation. No JAR hot loading.
use crate::{atomic, content, profile_dir, Runtime};
use futures_util::StreamExt;
use serde_json::{json, Value};
use sha2::{Digest, Sha512};
use std::{
    collections::{BTreeMap, HashMap, HashSet, VecDeque},
    fs,
    path::Path,
    sync::{Mutex, OnceLock},
    time::{Duration, SystemTime, UNIX_EPOCH},
};

const API: &str = "https://api.modrinth.com/v2";
const INDEX: &str = ".unbox-modrinth.json";
fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}
fn field<'a>(v: &'a Value, k: &str) -> Result<&'a str, String> {
    v[k].as_str()
        .ok_or_else(|| format!("Modrinth response is missing {k}"))
}
fn identifier(s: &str) -> Result<(), String> {
    if s.is_empty()
        || s.len() > 128
        || !s
            .bytes()
            .all(|b| b.is_ascii_alphanumeric() || b == b'-' || b == b'_')
    {
        Err("Invalid Modrinth identifier".into())
    } else {
        Ok(())
    }
}
fn client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .user_agent(concat!(
            "SkyVessel/UnboxClient/",
            env!("CARGO_PKG_VERSION"),
            " (https://github.com/SkyVessel/unbox-client)"
        ))
        .connect_timeout(Duration::from_secs(15))
        .timeout(Duration::from_secs(120))
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .map_err(|e| e.to_string())
}
async fn response(r: reqwest::Response) -> Result<Value, String> {
    if !r.status().is_success() {
        return Err(if r.status().as_u16() == 429 {
            "Modrinth is busy. Please retry in a minute.".into()
        } else {
            format!("Modrinth returned {}. Please retry.", r.status())
        });
    }
    let mut b = Vec::new();
    let mut stream = r.bytes_stream();
    while let Some(chunk) = stream.next().await {
        let chunk = chunk.map_err(|e| e.to_string())?;
        if b.len() + chunk.len() > 16 * 1024 * 1024 {
            return Err("Modrinth response is too large".into());
        }
        b.extend_from_slice(&chunk);
    }
    serde_json::from_slice(&b).map_err(|_| "Invalid Modrinth response".into())
}
async fn get(c: &reqwest::Client, path: &str, query: &[(&str, String)]) -> Result<Value, String> {
    response(
        c.get(format!("{API}/{path}"))
            .query(query)
            .send()
            .await
            .map_err(|_| "Could not reach Modrinth. Check your connection and retry.")?,
    )
    .await
}
fn project_type(kind: &str) -> Result<&'static str, String> {
    match kind {
        "Mods" => Ok("mod"),
        "Packs" => Ok("resourcepack"),
        "Shaders" => Ok("shader"),
        _ => Err("Unknown content type".into()),
    }
}
fn profile(rt: &Runtime, id: &str) -> Result<Value, String> {
    let p = profile_dir(rt, id)?.join("profile.json");
    serde_json::from_slice(&fs::read(p).map_err(|_| "Profile no longer exists")?)
        .map_err(|_| "Invalid profile".into())
}
pub(crate) fn writable(rt: &Runtime, id: &str) -> Result<Value, String> {
    let p = profile(rt, id)?;
    if p["sharedHost"].is_string() {
        return Err("Friend profiles are read-only. The host manages their content.".into());
    }
    Ok(p)
}
fn compatible(v: &Value, p: &Value, kind: &str) -> bool {
    v["game_versions"]
        .as_array()
        .is_some_and(|a| a.contains(&p["version"]))
        && (kind != "Mods"
            || v["loaders"]
                .as_array()
                .is_some_and(|a| a.contains(&p["loader"])))
        && (kind != "Shaders"
            || v["loaders"]
                .as_array()
                .is_some_and(|a| a.iter().any(|x| x == "iris")))
}
fn safe_image(v: &Value) -> Value {
    v.as_str()
        .filter(|s| cdn(s).is_ok())
        .map(|s| json!(s))
        .unwrap_or(Value::Null)
}
fn cdn(s: &str) -> Result<reqwest::Url, String> {
    let u = reqwest::Url::parse(s).map_err(|_| "Invalid download address")?;
    if u.scheme() != "https"
        || u.host_str() != Some("cdn.modrinth.com")
        || u.port().is_some()
        || !u.username().is_empty()
        || u.password().is_some()
    {
        return Err("Untrusted Modrinth download address".into());
    }
    Ok(u)
}
fn facets(p: &Value, kind: &str, category: &str) -> Result<Value, String> {
    let mut v = vec![
        json!([format!("project_type:{}", project_type(kind)?)]),

    ];
    if let Some(version) = p["version"].as_str() {
        v.push(json!([format!("versions:{version}")]))
    }
    if kind == "Mods" && p["loader"].is_string() {
        v.push(json!([format!("categories:{}", field(p, "loader")?)]))
    }
    if kind == "Shaders" {
        v.push(json!(["categories:iris"]))
    }
    if category.len() > 1024 || category.split(',').count() > 12 {
        return Err("Too many categories".into());
    }
    for selected in category.split(',').filter(|s| !s.is_empty()) {
        identifier(selected)?;
        v.push(json!([format!("categories:{selected}")]))
    }
    Ok(json!(v))
}
#[tauri::command]
pub async fn catalog_search(
    rt: tauri::State<'_, Runtime>,
    id: String,
    kind: String,
    query: String,
    category: String,
    sort: String,
    offset: u32,
) -> Result<Value, String> {
    let p = if id.is_empty() { Value::Null } else { profile(&rt, &id)? };
    if (kind == "Mods" || kind == "Shaders") && p["loader"] == "vanilla" {
        return Err("Choose a Fabric or NeoForge profile to browse mods or shaders.".into());
    }
    if query.chars().count() > 200 {
        return Err("Search is too long".into());
    }
    if !["relevance", "downloads", "updated", "newest"].contains(&sort.as_str()) {
        return Err("Invalid sort order".into());
    }
    let c = client()?;
    let mut v = get(
        &c,
        "search",
        &[
            ("query", query),
            ("facets", facets(&p, &kind, &category)?.to_string()),
            ("index", sort),
            ("limit", "20".into()),
            ("offset", offset.to_string()),
        ],
    )
    .await?;
    if let Some(hits) = v["hits"].as_array_mut() {
        for h in hits {
            h["icon_url"] = safe_image(&h["icon_url"]);
            h["featured_gallery"] = safe_image(&h["featured_gallery"]);
            if h["featured_gallery"].is_null() {
                h["featured_gallery"] = h["gallery"]
                    .as_array()
                    .and_then(|a| a.iter().map(safe_image).find(|v| !v.is_null()))
                    .unwrap_or(Value::Null)
            }
        }
    }
    Ok(v)
}
#[tauri::command]
pub async fn catalog_project(project: String) -> Result<Value, String> {
    identifier(&project)?;
    let mut v = get(&client()?, &format!("project/{project}"), &[]).await?;
    v["icon_url"] = safe_image(&v["icon_url"]);
    if let Some(gallery) = v["gallery"].as_array_mut() {
        gallery.retain(|g| !safe_image(&g["url"]).is_null());
        gallery.sort_by_key(|g| (!g["featured"].as_bool().unwrap_or(false), g["ordering"].as_i64().unwrap_or(0)));
    }
    // The frontend renders body as sanitized Markdown/HTML, never as a remote document.
    Ok(json!({"id":v["id"],"slug":v["slug"],"title":v["title"],"description":v["description"],"body":v["body"],"icon_url":v["icon_url"],"gallery":v["gallery"],"downloads":v["downloads"],"license":v["license"],"source_url":v["source_url"],"issues_url":v["issues_url"],"wiki_url":v["wiki_url"],"game_versions":v["game_versions"],"loaders":v["loaders"]}))
}
#[tauri::command]
pub async fn catalog_categories(kind: String) -> Result<Value, String> {
    let t = project_type(&kind)?;
    let v = get(&client()?, "tag/category", &[]).await?;
    Ok(json!(v
        .as_array()
        .ok_or("Invalid categories")?
        .iter()
        .filter(|c| c["project_type"] == t)
        .map(|c| json!({"name":c["name"],"header":c["header"]}))
        .collect::<Vec<_>>()))
}
pub(crate) fn index(dir: &Path) -> Value {
    fs::read(dir.join(INDEX))
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or(json!({}))
}
pub(crate) fn hash(path: &Path) -> Result<String, String> {
    use std::io::Read;
    let mut f = fs::File::open(path).map_err(|e| e.to_string())?;
    let mut h = Sha512::new();
    let mut b = [0u8; 65536];
    loop {
        let n = f.read(&mut b).map_err(|e| e.to_string())?;
        if n == 0 {
            break;
        }
        h.update(&b[..n]);
    }
    Ok(format!("{:x}", h.finalize()))
}
pub(crate) fn decorate(index: &Value, path: &Path, name: &str, item: &mut Value) {
    let e = &index[name];
    if e["sha512"]
        .as_str()
        .is_some_and(|expected| hash(path).is_ok_and(|digest| digest == expected))
    {
        for k in ["title", "icon", "projectId", "versionId"] {
            if !e[k].is_null() {
                item[k] = e[k].clone()
            }
        }
        item["source"] = json!("Modrinth");
    }
}
fn snapshot(game: &Path) -> Result<String, String> {
    let mut entries = BTreeMap::new();
    for k in ["Mods", "Packs", "Shaders"] {
        let dir = game.join(content::folder(k)?);
        if dir.is_dir() {
            for e in fs::read_dir(dir).map_err(|e| e.to_string())?.flatten() {
                let n = e.file_name().to_string_lossy().into_owned();
                if !n.starts_with('.') && e.path().is_file() {
                    entries.insert(format!("{k}/{n}"), hash(&e.path())?);
                }
            }
        }
    }
    Ok(format!(
        "{:x}",
        Sha512::digest(serde_json::to_vec(&entries).unwrap())
    ))
}
fn bundled(p: &Value) -> Result<Vec<Value>, String> {
    if p["loader"] == "vanilla" {
        return Ok(vec![]);
    }
    let mut v = crate::launcher::performance_lock(field(p, "loader")?, field(p, "version")?)?
        .as_array()
        .cloned()
        .unwrap_or_default();
    let lock: Value = serde_json::from_str(include_str!("../resources/survival-mods-lock.json"))
        .map_err(|e| e.to_string())?;
    if let Some(t) = lock["targets"].as_array().and_then(|a| {
        a.iter()
            .find(|t| t["minecraft"] == p["version"] && t["loader"] == p["loader"])
    }) {
        v.extend(t["mods"].as_array().cloned().unwrap_or_default())
    }
    Ok(v)
}
async fn installed(
    c: &reqwest::Client,
    game: &Path,
    p: &Value,
) -> Result<HashMap<String, Value>, String> {
    let mut map = HashMap::new();
    for m in bundled(p)? {
        if let Some(id) = m["projectId"].as_str() {
            map.insert(
                id.into(),
                json!({"version":m["id"],"managed":true,"title":m["slug"]}),
            );
        }
    }
    let mut hashes = vec![];
    for k in ["Mods", "Packs", "Shaders"] {
        let dir = game.join(content::folder(k)?);
        if !dir.is_dir() {
            continue;
        }
        for e in fs::read_dir(&dir).map_err(|e| e.to_string())?.flatten() {
            let n = e.file_name().to_string_lossy().into_owned();
            if !n.starts_with('.')
                && e.path().is_file()
                && (n.ends_with(".jar") || n.ends_with(".zip"))
            {
                hashes.push(hash(&e.path())?);
            }
        }
    }
    for batch in hashes.chunks(100) {
        let v = response(
            c.post(format!("{API}/version_files"))
                .json(&json!({"hashes":batch,"algorithm":"sha512"}))
                .send()
                .await
                .map_err(|e| e.to_string())?,
        )
        .await?;
        if let Some(values) = v.as_object() {
            for v in values.values() {
                if let Some(id) = v["project_id"].as_str() {
                    map.entry(id.into()).or_insert(json!({"version":v["id"],"managed":false,"title":v["name"],"dependencies":v["dependencies"],"game_versions":v["game_versions"],"loaders":v["loaders"]}));
                }
            }
        }
    }
    Ok(map)
}
async fn versions(c: &reqwest::Client, id: &str, p: &Value, kind: &str) -> Result<Value, String> {
    identifier(id)?;
    let mut q = vec![("game_versions", json!([p["version"]]).to_string())];
    if kind == "Mods" {
        q.push(("loaders", json!([p["loader"]]).to_string()))
    }
    if kind == "Shaders" {
        q.push(("loaders", json!(["iris"]).to_string()))
    }
    get(c, &format!("project/{id}/version"), &q).await
}
fn choose(versions: &Value, p: &Value, kind: &str) -> Result<Value, String> {
    let a = versions.as_array().ok_or("Invalid version list")?;
    a.iter()
        .filter(|v| compatible(v, p, kind))
        .min_by_key(|v| if v["version_type"] == "release" { 0 } else { 1 })
        .cloned()
        .ok_or("No compatible file for this profile's exact version and loader".into())
}
fn filename<'a>(f: &'a Value, kind: &str) -> Result<&'a str, String> {
    let n = field(f, "filename")?;
    if n.is_empty()
        || n.len() > 180
        || n.starts_with('.')
        || n.chars().any(char::is_control)
        || [
            "CON", "PRN", "AUX", "NUL", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7",
            "COM8", "COM9", "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
        ]
        .contains(
            &n.split('.')
                .next()
                .unwrap_or("")
                .to_ascii_uppercase()
                .as_str(),
        )
        || n.contains(['/', '\\', ':', '\0', '<', '>', '"', '|', '?', '*'])
        || n.ends_with([' ', '.'])
        || !n.ends_with(if kind == "Mods" { ".jar" } else { ".zip" })
    {
        return Err("Invalid download filename".into());
    }
    Ok(n)
}
fn file(v: &Value, kind: &str) -> Result<Value, String> {
    let files = v["files"].as_array().ok_or("Missing download files")?;
    let f = files
        .iter()
        .find(|f| f["primary"] == true)
        .or_else(|| files.first())
        .ok_or("No downloadable file")?
        .clone();
    filename(&f, kind)?;
    cdn(field(&f, "url")?)?;
    let h = field(&f["hashes"], "sha512")?;
    if h.len() != 128 || !h.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err("Missing SHA-512 checksum".into());
    }
    if !f["size"]
        .as_u64()
        .is_some_and(|n| n > 0 && n <= 512 * 1024 * 1024)
    {
        return Err("File exceeds the 512 MB download limit".into());
    }
    Ok(f)
}
async fn make_plan(rt: &Runtime, id: &str, kind: &str, project: &str) -> Result<Value, String> {
    let p = writable(rt, id)?;
    {
        let job = rt.job.lock().unwrap();
        if job["busy"] != true {
            recover(&profile_dir(rt, id)?)?;
        }
    }
    project_type(kind)?;
    identifier(project)?;
    if (kind == "Mods" || kind == "Shaders") && p["loader"] == "vanilla" {
        return Err("Choose a Fabric or NeoForge profile first".into());
    }
    let game = profile_dir(rt, id)?;
    let before = snapshot(&game)?;
    let c = client()?;
    let existing = installed(&c, &game, &p).await?;
    let mut queue = VecDeque::from([(project.to_owned(), None::<String>, kind.to_owned())]);
    let mut selected: HashMap<String, String> = HashMap::new();
    let mut entries = vec![];
    let mut provided = vec![];
    let mut incompatible = vec![];
    let mut optional = 0;
    while let Some((pid, pinned, requested_kind)) = queue.pop_front() {
        if selected.len() > 64 {
            return Err("Dependency tree exceeds 64 projects".into());
        }
        identifier(&pid)?;
        let project = get(&c, &format!("project/{pid}"), &[]).await?;
        let pid = field(&project, "id")?.to_owned();
        let kind = match project["project_type"].as_str() {
            Some("mod") => "Mods",
            Some("resourcepack") => "Packs",
            Some("shader") => "Shaders",
            _ => return Err("Unsupported dependency type".into()),
        };
        if entries.is_empty() && provided.is_empty() && kind != requested_kind {
            return Err("Project type does not match this content list".into());
        }
        if let Some(prev) = selected.get(&pid) {
            if pinned.as_ref().is_some_and(|v| v != prev) {
                return Err("Dependencies require conflicting versions".into());
            }
            continue;
        }
        if let Some(e) = existing.get(&pid) {
            if e["managed"] != true && !compatible(e, &p, kind) {
                return Err("An installed file of this project is for another environment. Remove that version before installing.".into());
            }
            let ver = field(e, "version")?;
            if pinned.as_deref().is_some_and(|x| x != ver) {
                return Err(format!("{} needs a different version of an installed or Unbox-managed dependency. Nothing was changed.",project["title"].as_str().unwrap_or(&pid)));
            }
            selected.insert(pid.clone(), ver.into());
            provided.push(json!({"title":project["title"],"managed":e["managed"]}));
            continue;
        }
        let version = if let Some(v) = pinned {
            identifier(&v)?;
            get(&c, &format!("version/{v}"), &[]).await?
        } else {
            choose(&versions(&c, &pid, &p, kind).await?, &p, kind)?
        };
        if version["project_id"] != pid || !compatible(&version, &p, kind) {
            return Err(format!(
                "{} has no compatible dependency for this profile",
                project["title"].as_str().unwrap_or(&pid)
            ));
        }
        selected.insert(pid.clone(), field(&version, "id")?.into());
        let f = file(&version, kind)?;
        for d in version["dependencies"].as_array().into_iter().flatten() {
            match d["dependency_type"].as_str() {
                Some("required") => {
                    let pin = d["version_id"].as_str().map(str::to_owned);
                    let dep = if let Some(x) = d["project_id"].as_str() {
                        x.into()
                    } else if let Some(v) = &pin {
                        identifier(v)?;
                        field(&get(&c, &format!("version/{v}"), &[]).await?, "project_id")?.into()
                    } else {
                        return Err("A required dependency has no project or version; install it manually first".into());
                    };
                    queue.push_back((dep, pin, kind.into()));
                }
                Some("incompatible") => incompatible.push(d.clone()),
                Some("optional") => optional += 1,
                _ => {}
            }
        }
        entries.push(json!({"projectId":pid,"versionId":version["id"],"version":version["version_number"],"releaseType":version["version_type"],"title":project["title"],"description":project["description"],"icon":safe_image(&project["icon_url"]),"kind":kind,"file":f,"sha512":f["hashes"]["sha512"],"serverSide":project["server_side"],"clientSide":project["client_side"]}));
    }
    if selected.len() > 64 {
        return Err("Dependency tree exceeds 64 projects".into());
    }
    for e in existing.values() {
        incompatible.extend(
            e["dependencies"]
                .as_array()
                .into_iter()
                .flatten()
                .filter(|d| d["dependency_type"] == "incompatible")
                .cloned(),
        );
    }
    for d in incompatible {
        let pid = d["project_id"].as_str();
        let vid = d["version_id"].as_str();
        if selected
            .iter()
            .any(|(p, v)| pid.is_none_or(|x| x == p) && vid.is_none_or(|x| x == v))
            || existing.iter().any(|(p, v)| {
                pid.is_none_or(|x| x == p) && vid.is_none_or(|x| Some(x) == v["version"].as_str())
            })
        {
            return Err("A selected mod declares an incompatibility with this profile. Nothing was changed.".into());
        }
    }
    let total: u64 = entries
        .iter()
        .map(|e| e["file"]["size"].as_u64().unwrap_or(0))
        .sum();
    if total > 1024 * 1024 * 1024 {
        return Err("Install exceeds the 1 GB transaction limit".into());
    }
    let token = uuid::Uuid::new_v4().to_string();
    let plan = json!({"token":token,"profileId":id,"profile":p,"kind":kind,"created":now(),"snapshot":before,"entries":entries,"provided":provided,"optional":optional,"total":total});
    atomic(
        &rt.root.join("catalog-plans").join(format!("{token}.json")),
        &serde_json::to_vec(&plan).unwrap(),
    )?;
    Ok(plan)
}
#[tauri::command]
pub async fn catalog_plan(
    rt: tauri::State<'_, Runtime>,
    id: String,
    kind: String,
    project: String,
) -> Result<Value, String> {
    make_plan(&rt, &id, &kind, &project).await
}
fn statuses() -> &'static Mutex<HashMap<String, Value>> {
    static S: OnceLock<Mutex<HashMap<String, Value>>> = OnceLock::new();
    S.get_or_init(Default::default)
}
fn progress(token: &str, v: Value) {
    let mut s = statuses().lock().unwrap();
    let cancel = s.get(token).is_some_and(|v| v["cancel"] == true);
    let mut v = v;
    v["cancel"] = json!(cancel);
    if s.len() > 64 {
        s.retain(|_, v| v["stage"] == "Downloading" || v["stage"] == "Installing");
    }
    s.insert(token.into(), v);
}
fn canceled(token: &str) -> bool {
    statuses()
        .lock()
        .unwrap()
        .get(token)
        .is_some_and(|v| v["cancel"] == true)
}
#[tauri::command]
pub fn catalog_status(token: String) -> Value {
    statuses()
        .lock()
        .unwrap()
        .get(&token)
        .cloned()
        .unwrap_or(json!({"stage":"Preparing"}))
}
#[tauri::command]
pub fn catalog_cancel(token: String) {
    let mut s = statuses().lock().unwrap();
    if let Some(v) = s.get_mut(&token) {
        if v["stage"] != "Installing" {
            v["cancel"] = json!(true)
        }
    }
}
async fn wait_cancel(token: &str) {
    while !canceled(token) {
        tokio::time::sleep(Duration::from_millis(100)).await;
    }
}
async fn download(
    c: &reqwest::Client,
    f: &Value,
    path: &Path,
    token: &str,
    completed: u64,
    total: u64,
) -> Result<(), String> {
    let expected = field(&f["hashes"], "sha512")?;
    if path.is_file() && hash(path)? == expected {
        return Ok(());
    }
    let request = c.get(cdn(field(f, "url")?)?);
    let res = tokio::select! {
        response = request.send() => response.map_err(|_| "Download failed. Check your connection and retry.")?,
        _ = wait_cancel(token) => return Err("Installation canceled. No profile files were changed.".into()),
    };
    if !res.status().is_success() {
        return Err(format!("Download returned {}", res.status()));
    }
    let temp = path.with_extension("part");
    let result = async {
        use tokio::io::AsyncWriteExt;
        let mut out = tokio::fs::File::create(&temp).await.map_err(|e|e.to_string())?;
        let mut stream = res.bytes_stream();
        let mut size = 0u64;
        let mut h = Sha512::new();
        loop {
            let chunk = tokio::select! {
                chunk = stream.next() => match chunk { Some(chunk) => chunk, None => break },
                _ = wait_cancel(token) => return Err("Installation canceled. No profile files were changed.".into()),
            };
            let b = chunk.map_err(|_|"Download interrupted. Retry the installation.")?;
            size += b.len() as u64;
            if size > f["size"].as_u64().unwrap_or(0) { return Err("Downloaded file is larger than expected".into()); }
            h.update(&b);
            out.write_all(&b).await.map_err(|e|e.to_string())?;
            progress(token,json!({"stage":"Downloading","file":f["filename"],"downloaded":completed+size,"total":total}));
        }
        out.sync_all().await.map_err(|e|e.to_string())?;
        if size != f["size"].as_u64().unwrap_or(0) || format!("{:x}",h.finalize()) != expected { return Err("Download checksum failed. Nothing was installed.".into()); }
        drop(out);
        fs::rename(&temp,path).map_err(|e|e.to_string())?;
        Ok(())
    }.await;
    let _ = fs::remove_file(temp);
    result
}
// Recovery removes only transaction-owned files with the expected hash. It never replaces user files.
pub(crate) fn recover(game: &Path) -> Result<(), String> {
    let journal = game.join(".unbox-catalog-transaction.json");
    if !journal.exists() {
        return Ok(());
    }
    let v: Value = serde_json::from_slice(&fs::read(&journal).map_err(|e| e.to_string())?)
        .map_err(|_| "Invalid installation recovery record")?;
    if v["complete"] != true {
        for e in v["entries"].as_array().ok_or("Invalid recovery entries")? {
            let dir = game.join(content::folder(field(e, "kind")?)?);
            let path = dir.join(filename(&e["file"], field(e, "kind")?)?);
            if path.exists() {
                if hash(&path)? != e["sha512"].as_str().unwrap_or("") {
                    return Err(
                        "A partially installed file changed. Review it before retrying.".into(),
                    );
                }
                fs::remove_file(path).map_err(|e| e.to_string())?;
            }
        }
        if let Some(indexes) = v["indexes"].as_object() {
            for (kind, old) in indexes {
                atomic(
                    &game.join(content::folder(kind)?).join(INDEX),
                    &serde_json::to_vec(old).unwrap(),
                )?;
            }
        }
    }
    fs::remove_file(journal).map_err(|e| e.to_string())
}
fn validate_archives(rt: &Runtime, game: &Path, entries: &[Value]) -> Result<(), String> {
    let mut ids = HashSet::new();
    let mut destinations = HashSet::new();
    let mods = game.join("mods");
    if mods.is_dir() {
        for e in fs::read_dir(mods).map_err(|e| e.to_string())?.flatten() {
            if e.path().extension().is_some_and(|x| x == "jar") {
                ids.extend(crate::launcher::jar_mod_ids(&e.path())?);
            }
        }
    }
    for e in entries {
        let k = field(e, "kind")?;
        let file = &e["file"];
        let path = rt.root.join("catalog-cache").join(field(e, "sha512")?);
        if hash(&path)? != field(e, "sha512")? {
            return Err("Cached download checksum failed. Retry the installation.".into());
        }
        content::metadata(&path, k)?;
        let target = game.join(content::folder(k)?).join(filename(file, k)?);
        if !destinations.insert(target.to_string_lossy().to_ascii_lowercase()) {
            return Err("Two projects use the same filename. Nothing was installed.".into());
        }
        if target.exists() {
            return Err(format!(
                "{} already exists. Nothing was replaced.",
                filename(file, k)?
            ));
        }
        if k == "Mods" {
            for id in crate::launcher::jar_mod_ids(&path)? {
                if crate::sharing::managed_id(&id) {
                    return Err(format!(
                        "{id} is managed by Unbox; this file cannot be installed"
                    ));
                }
                if !ids.insert(id.clone()) {
                    return Err(format!(
                        "Duplicate mod {id}. Remove the other version before installing."
                    ));
                }
            }
        }
    }
    Ok(())
}
fn commit(rt: &Runtime, plan: &Value) -> Result<(), String> {
    let id = field(plan, "profileId")?;
    let job = rt.job.lock().unwrap();
    if job["busy"] == true {
        return Err("Close Minecraft before installing content".into());
    }
    let p = writable(rt, id)?;
    if p["version"] != plan["profile"]["version"] || p["loader"] != plan["profile"]["loader"] {
        return Err("Profile environment changed. Review the installation again.".into());
    }
    let game = profile_dir(rt, id)?;
    recover(&game)?;
    if snapshot(&game)? != plan["snapshot"] {
        return Err("Profile files changed. Review the installation again.".into());
    }
    let entries = plan["entries"]
        .as_array()
        .ok_or("Invalid installation plan")?;
    validate_archives(rt, &game, entries)?;
    let mut indexes = json!({});
    for e in entries {
        let k = field(e, "kind")?;
        let dir = game.join(content::folder(k)?);
        fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
        indexes[k] = index(&dir);
    }
    let journal = game.join(".unbox-catalog-transaction.json");
    let mut record = json!({"entries":entries,"indexes":indexes});
    atomic(&journal, &serde_json::to_vec(&record).unwrap())?;
    let result = (|| {
        let mut next = indexes;
        for e in entries {
            let k = field(e, "kind")?;
            let name = filename(&e["file"], k)?;
            let dir = game.join(content::folder(k)?);
            let cache = rt.root.join("catalog-cache").join(field(e, "sha512")?);
            let staged = dir.join(format!(
                ".download-{}",
                plan["token"].as_str().unwrap_or("stage")
            ));
            fs::copy(cache, &staged).map_err(|e| e.to_string())?;
            let link = fs::hard_link(&staged, dir.join(name)).map_err(|e| e.to_string());
            let _ = fs::remove_file(staged);
            link?;
            next[k][name] = json!({"title":e["title"],"icon":e["icon"],"projectId":e["projectId"],"versionId":e["versionId"],"sha512":e["sha512"]});
        }
        for (k, v) in next.as_object().unwrap() {
            atomic(
                &game.join(content::folder(k)?).join(INDEX),
                &serde_json::to_vec(v).unwrap(),
            )?;
        }
        record["complete"] = json!(true);
        atomic(&journal, &serde_json::to_vec(&record).unwrap())?;
        Ok::<_, String>(())
    })();
    if let Err(e) = result {
        recover(&game).map_err(|r| format!("{e}. Recovery needs attention: {r}"))?;
        return Err(e);
    }
    let _ = fs::remove_file(journal);
    Ok(())
}
async fn install(rt: &Runtime, token: &str) -> Result<Value, String> {
    static LOCK: OnceLock<tokio::sync::Mutex<()>> = OnceLock::new();
    let _guard = LOCK
        .get_or_init(Default::default)
        .try_lock()
        .map_err(|_| "Another installation is running")?;
    uuid::Uuid::parse_str(token).map_err(|_| "Invalid installation token")?;
    let path = rt.root.join("catalog-plans").join(format!("{token}.json"));
    let plan: Value = serde_json::from_slice(
        &fs::read(&path).map_err(|_| "Installation plan expired; choose the project again")?,
    )
    .map_err(|_| "Invalid installation plan")?;
    if now().saturating_sub(plan["created"].as_u64().unwrap_or(0)) > 900 {
        return Err("Installation plan expired; choose the project again".into());
    }
    writable(rt, field(&plan, "profileId")?)?;
    {
        let job = rt.job.lock().unwrap();
        if job["busy"] == true {
            return Err("Close Minecraft before installing content".into());
        }
        recover(&profile_dir(rt, field(&plan, "profileId")?)?)?;
    }
    statuses().lock().unwrap().remove(token);
    progress(
        token,
        json!({"stage":"Downloading","downloaded":0,"total":plan["total"]}),
    );
    let c = client()?;
    let cache = rt.root.join("catalog-cache");
    fs::create_dir_all(&cache).map_err(|e| e.to_string())?;
    let mut done = 0;
    let result = async {
        for e in plan["entries"].as_array().ok_or("Invalid install plan")? {
            if canceled(token) {
                return Err("Installation canceled. No profile files were changed.".into());
            }
            download(
                &c,
                &e["file"],
                &cache.join(field(e, "sha512")?),
                token,
                done,
                plan["total"].as_u64().unwrap_or(0),
            )
            .await?;
            done += e["file"]["size"].as_u64().unwrap_or(0);
        }
        if canceled(token) {
            return Err("Installation canceled. No profile files were changed.".into());
        }
        progress(
            token,
            json!({"stage":"Installing","downloaded":done,"total":done}),
        );
        commit(rt, &plan)?;
        Ok(json!({"installed":plan["entries"].as_array().map(Vec::len).unwrap_or(0)}))
    }
    .await;
    progress(
        token,
        json!({"stage":if result.is_ok(){"Installed"}else{"Failed"},"error":result.as_ref().err()}),
    );
    if result.is_ok() {
        let _ = fs::remove_file(path);
    }
    result
}
#[tauri::command]
pub async fn catalog_install(
    rt: tauri::State<'_, Runtime>,
    token: String,
) -> Result<Value, String> {
    install(&rt, &token).await
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;
    struct Fixture {
        rt: Runtime,
        id: String,
    }
    impl Fixture {
        fn new() -> Self {
            let root = std::env::temp_dir().join(format!("unbox-catalog-{}", uuid::Uuid::new_v4()));
            let rt = Runtime {
                root: root.clone(),
                resources: root,
                job: Default::default(),
                auth: Default::default(),
                social: Default::default(),
            };
            let id = uuid::Uuid::new_v4().to_string();
            atomic(
                &profile_dir(&rt, &id).unwrap().join("profile.json"),
                &serde_json::to_vec(
                    &json!({"id":id,"name":"Catalog fixture","version":"26.1","loader":"fabric"}),
                )
                .unwrap(),
            )
            .unwrap();
            Self { rt, id }
        }
        fn entry(&self, name: &str, kind: &str, modid: &str) -> Value {
            let mut z = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
            let (path, bytes) = match kind {
                "Mods" => (
                    "fabric.mod.json",
                    format!("{{\"id\":\"{modid}\",\"name\":\"Demo\",\"version\":\"1\"}}"),
                ),
                "Packs" => ("pack.mcmeta", "{}".into()),
                _ => ("shaders/main.vsh", "shader".into()),
            };
            z.start_file(path, zip::write::SimpleFileOptions::default())
                .unwrap();
            z.write_all(bytes.as_bytes()).unwrap();
            let bytes = z.finish().unwrap().into_inner();
            let h = format!("{:x}", Sha512::digest(&bytes));
            atomic(&self.rt.root.join("catalog-cache").join(&h), &bytes).unwrap();
            json!({"kind":kind,"title":"Demo","projectId":"demo","versionId":"v1","icon":"https://cdn.modrinth.com/data/demo/icon.png","sha512":h,"file":{"filename":name,"hashes":{"sha512":h},"size":bytes.len(),"url":"https://cdn.modrinth.com/data/demo/file.jar"}})
        }
        fn plan(&self, entries: Vec<Value>) -> Value {
            json!({"token":uuid::Uuid::new_v4().to_string(),"profileId":self.id,"profile":profile(&self.rt,&self.id).unwrap(),"snapshot":snapshot(&profile_dir(&self.rt,&self.id).unwrap()).unwrap(),"entries":entries})
        }
    }
    impl Drop for Fixture {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.rt.root);
        }
    }
    #[test]
    fn browse_all_and_combined_filters_keep_profile_constraints() {
        assert_eq!(facets(&Value::Null, "Mods", "").unwrap(), json!([["project_type:mod"]]));
        assert_eq!(facets(&Value::Null, "Mods", "adventure,decoration").unwrap(), json!([["project_type:mod"],["categories:adventure"],["categories:decoration"]]));
        assert!(facets(&Value::Null,"Mods","bad:filter").is_err());
        assert!(facets(&Value::Null,"Mods",&vec!["a";13].join(",")).is_err());
    }
    #[tokio::test]
    #[ignore = "Explicit read-only public Modrinth API check"]
    async fn live_project_description_without_a_profile() {
        assert!(catalog_project("../search".into()).await.is_err());
        let project = catalog_project("searchables".into()).await.unwrap();
        assert_eq!(project["slug"], "searchables");
        assert!(!project["body"].as_str().unwrap().is_empty());
        assert!(!safe_image(&project["icon_url"]).is_null());
        assert!(project["gallery"].is_array());
    }
    #[test]
    fn exact_environment_filters_and_no_cross_loader_fallback() {
        let p = json!({"version":"26.3","loader":"neoforge"});
        let good = json!({"game_versions":["26.3"],"loaders":["neoforge"]});
        assert!(compatible(&good, &p, "Mods"));
        assert!(!compatible(
            &json!({"game_versions":["26.1"],"loaders":["neoforge"]}),
            &p,
            "Mods"
        ));
        assert!(!compatible(
            &json!({"game_versions":["26.3"],"loaders":["fabric"]}),
            &p,
            "Mods"
        ));
        assert!(compatible(
            &json!({"game_versions":["26.3"],"loaders":["minecraft"]}),
            &p,
            "Packs"
        ));
        assert!(!compatible(&good, &p, "Shaders"));
        assert_eq!(
            facets(&p, "Mods", "technology").unwrap(),
            json!([
                ["project_type:mod"],
                ["versions:26.3"],
                ["categories:neoforge"],
                ["categories:technology"]
            ])
        );
    }
    #[test]
    fn installs_all_three_kinds_and_keeps_catalog_icon() {
        let f = Fixture::new();
        let entries = vec![
            f.entry("mod.jar", "Mods", "demo"),
            f.entry("pack.zip", "Packs", ""),
            f.entry("shader.zip", "Shaders", ""),
        ];
        let p = f.plan(entries);
        commit(&f.rt, &p).unwrap();
        for k in ["Mods", "Packs", "Shaders"] {
            let list = content::list(&f.rt, &f.id, k).unwrap();
            assert_eq!(list[0]["title"], "Demo");
            assert_eq!(list[0]["projectId"], "demo");
            assert!(list[0]["icon"]
                .as_str()
                .unwrap()
                .contains("cdn.modrinth.com"));
        }
        assert!(!profile_dir(&f.rt, &f.id)
            .unwrap()
            .join(".unbox-catalog-transaction.json")
            .exists());
        assert!(commit(&f.rt, &p).is_err());
    }
    #[test]
    fn refuses_friend_running_changed_and_duplicate_profiles() {
        let f = Fixture::new();
        let game = profile_dir(&f.rt, &f.id).unwrap();
        let e = f.entry("demo.jar", "Mods", "demo");
        let plan = f.plan(vec![e.clone()]);
        *f.rt.job.lock().unwrap() = json!({"busy":true,"profileId":f.id});
        assert!(commit(&f.rt, &plan).is_err());
        *f.rt.job.lock().unwrap() = json!({});
        let mut p = profile(&f.rt, &f.id).unwrap();
        p["sharedHost"] = json!(uuid::Uuid::new_v4().to_string());
        atomic(&game.join("profile.json"), &serde_json::to_vec(&p).unwrap()).unwrap();
        assert!(commit(&f.rt, &plan).is_err());
        assert!(
            content::import(&f.rt, &f.id, "Mods", vec!["fixture.jar".into()])
                .unwrap_err()
                .contains("read-only")
        );
        assert!(
            crate::removal::remove_file(&f.rt, &f.id, "Mods", "demo.jar")
                .unwrap_err()
                .contains("read-only")
        );
        p.as_object_mut().unwrap().remove("sharedHost");
        atomic(&game.join("profile.json"), &serde_json::to_vec(&p).unwrap()).unwrap();
        atomic(
            &game.join("mods/existing.jar"),
            &fs::read(
                f.rt.root
                    .join("catalog-cache")
                    .join(field(&e, "sha512").unwrap()),
            )
            .unwrap(),
        )
        .unwrap();
        assert!(commit(&f.rt, &plan).unwrap_err().contains("changed"));
        let duplicate = f.plan(vec![e]);
        assert!(commit(&f.rt, &duplicate).unwrap_err().contains("Duplicate"));
        assert!(!game.join("mods/demo.jar").exists());
    }
    #[test]
    fn invalid_managed_archives_and_addresses_never_write_files() {
        let f = Fixture::new();
        for id in ["mousetweaks", "freecam"] {
            let managed = f.entry("renamed.jar", "Mods", id);
            assert!(commit(&f.rt, &f.plan(vec![managed]))
                .unwrap_err()
                .contains("managed"));
        }
        for url in [
            "http://cdn.modrinth.com/data/x",
            "https://evil.test/file.jar",
            "https://cdn.modrinth.com@evil.test/file.jar",
        ] {
            assert!(cdn(url).is_err());
        }
        for name in ["../a.jar", "C:\\a.jar", "/a.jar", ".hidden.jar", "a.zip"] {
            assert!(filename(&json!({"filename":name}), "Mods").is_err());
        }
    }
    #[test]
    fn interrupted_commit_recovers_files_and_metadata() {
        let f = Fixture::new();
        let e = f.entry("demo.jar", "Mods", "demo");
        let game = profile_dir(&f.rt, &f.id).unwrap();
        let bytes = fs::read(
            f.rt.root
                .join("catalog-cache")
                .join(field(&e, "sha512").unwrap()),
        )
        .unwrap();
        atomic(&game.join("mods/demo.jar"), &bytes).unwrap();
        atomic(&game.join("mods/keep.txt"), b"keep").unwrap();
        atomic(&game.join("mods").join(INDEX), b"{\"demo.jar\":{}}").unwrap();
        atomic(
            &game.join(".unbox-catalog-transaction.json"),
            &serde_json::to_vec(&json!({"entries":[e],"indexes":{"Mods":{}}})).unwrap(),
        )
        .unwrap();
        recover(&game).unwrap();
        assert!(!game.join("mods/demo.jar").exists());
        assert_eq!(fs::read(game.join("mods/keep.txt")).unwrap(), b"keep");
        assert_eq!(index(&game.join("mods")), json!({}));
    }
    #[test]
    fn corrupt_cache_and_duplicate_filenames_do_not_install_any_files() {
        let f = Fixture::new();
        let first = f.entry("same.jar", "Mods", "one");
        let second = f.entry("same.jar", "Mods", "two");
        assert!(commit(&f.rt, &f.plan(vec![first.clone(), second]))
            .unwrap_err()
            .contains("same filename"));
        let plan = f.plan(vec![first.clone()]);
        fs::write(
            f.rt.root
                .join("catalog-cache")
                .join(field(&first, "sha512").unwrap()),
            b"corrupt",
        )
        .unwrap();
        assert!(commit(&f.rt, &plan).unwrap_err().contains("checksum"));
        assert!(!profile_dir(&f.rt, &f.id)
            .unwrap()
            .join("mods/same.jar")
            .exists());
    }
    #[tokio::test]
    async fn cancellation_interrupts_waiting_download_without_touching_profile() {
        let token = uuid::Uuid::new_v4().to_string();
        progress(&token, json!({"stage":"Downloading"}));
        catalog_cancel(token.clone());
        tokio::time::timeout(Duration::from_secs(1), wait_cancel(&token))
            .await
            .unwrap();
        progress(&token, json!({"stage":"Downloading","downloaded":12}));
        assert!(canceled(&token));
        statuses().lock().unwrap().remove(&token);
    }
    #[tokio::test]
    #[ignore = "Explicit public-network installation test; isolated temporary profile only"]
    async fn live_modrinth_install() {
        let f = Fixture::new();
        let kind = std::env::var("UNBOX_CATALOG_KIND").unwrap_or("Mods".into());
        let project = std::env::var("UNBOX_CATALOG_PROJECT").unwrap_or("appleskin".into());
        let plan = make_plan(&f.rt, &f.id, &kind, &project).await.unwrap();
        println!(
            "plan: {}",
            json!({"project":project,"entries":plan["entries"].as_array().unwrap().iter().map(|e|json!({"title":e["title"],"kind":e["kind"],"version":e["version"]})).collect::<Vec<_>>(),"provided":plan["provided"]})
        );
        let result = install(&f.rt, field(&plan, "token").unwrap())
            .await
            .unwrap();
        assert!(result["installed"].as_u64().unwrap() > 0);
        assert!(content::list(&f.rt, &f.id, &kind)
            .unwrap()
            .as_array()
            .unwrap()
            .iter()
            .any(|e| e["projectId"].is_string()));
        let again = make_plan(&f.rt, &f.id, &kind, &project).await.unwrap();
        assert!(again["entries"].as_array().unwrap().is_empty());
        println!("verified installation and duplicate detection: {result}");
    }
}
