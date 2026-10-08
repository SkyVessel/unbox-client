use crate::{managed_mod_names, profile_dir, Runtime};
use serde_json::{json, Value};
use std::{fs, io::Read, path::Path};
pub fn folder(kind: &str) -> Result<&'static str, String> {
    match kind {
        "Mods" => Ok("mods"),
        "Packs" => Ok("resourcepacks"),
        "Shaders" => Ok("shaderpacks"),
        _ => Err("Unknown content type".into()),
    }
}
fn metadata(path: &Path, kind: &str) -> Result<Value, String> {
    let mut zip = zip::ZipArchive::new(fs::File::open(path).map_err(|e| e.to_string())?)
        .map_err(|_| "Not a valid JAR/ZIP file")?;
    if zip.len() > 50000 {
        return Err("Archive contains too many entries".into());
    }
    let names = zip.file_names().map(str::to_owned).collect::<Vec<_>>();
    if kind == "Mods"
        && !names.iter().any(|n| {
            [
                "fabric.mod.json",
                "META-INF/mods.toml",
                "META-INF/neoforge.mods.toml",
                "quilt.mod.json",
            ]
            .contains(&n.as_str())
        })
    {
        return Err("No mod metadata found".into());
    }
    if kind == "Packs" && !names.iter().any(|n| n == "pack.mcmeta") {
        return Err("Resource pack needs pack.mcmeta at its root".into());
    }
    if kind == "Shaders" && !names.iter().any(|n| n.starts_with("shaders/")) {
        return Err("Shader pack needs a shaders folder".into());
    }
    let mut result = json!({});
    let mut icon = "pack.png".to_string();
    if kind == "Mods" {
        if let Ok(mut f) = zip.by_name("fabric.mod.json") {
            if f.size() < 262144 {
                let mut s = String::new();
                let _ = f.read_to_string(&mut s);
                if let Ok(m) = serde_json::from_str::<Value>(&s) {
                    result["modId"] = m["id"].clone();
                    result["title"] = m["name"].clone();
                    if let Some(i) = m["icon"].as_str() {
                        icon = i.into()
                    }
                }
            }
        }
    }
    if let Ok(mut f) = zip.by_name(&icon) {
        if f.size() < 131072 {
            let mut bytes = Vec::new();
            f.read_to_end(&mut bytes).map_err(|_| "Cannot read icon")?;
            if bytes.len() >= 24
                && bytes.starts_with(b"\x89PNG\r\n\x1a\n")
                && (1..=1024).contains(&u32::from_be_bytes(bytes[16..20].try_into().unwrap()))
                && (1..=1024).contains(&u32::from_be_bytes(bytes[20..24].try_into().unwrap()))
            {
                use base64::Engine;
                result["icon"] = json!(format!(
                    "data:image/png;base64,{}",
                    base64::engine::general_purpose::STANDARD.encode(bytes)
                ));
            }
        }
    }
    Ok(result)
}
pub fn list(rt: &Runtime, id: &str, kind: &str) -> Result<Value, String> {
    let game = profile_dir(rt, id)?;
    let dir = game.join(folder(kind)?);
    if !dir.exists() {
        return Ok(json!([]));
    }
    let hidden = managed_mod_names(&game);
    let mut files = Vec::new();
    for e in fs::read_dir(dir).map_err(|e| e.to_string())?.flatten() {
        let name = e.file_name().to_string_lossy().into_owned();
        if name.starts_with('.')
            || !e.path().is_file()
            || (kind == "Mods" && hidden.contains(&name))
        {
            continue;
        }
        let mut v = metadata(&e.path(), kind).unwrap_or(json!({}));
        v["name"] = json!(name);
        v["size"] = json!(e.metadata().map(|m| m.len()).unwrap_or(0));
        files.push(v)
    }
    files.sort_by(|a, b| a["name"].as_str().cmp(&b["name"].as_str()));
    Ok(json!(files))
}
pub fn import(rt: &Runtime, id: &str, kind: &str, paths: Vec<String>) -> Result<Value, String> {
    if paths.is_empty() || paths.len() > 100 {
        return Err("Drop between 1 and 100 files".into());
    }
    let job = rt.job.lock().unwrap();
    if job["busy"] == true && job["profileId"] == id {
        return Err("Close this profile's game before changing its files".into());
    }
    let game = profile_dir(rt, id)?;
    if !game.join("profile.json").exists() {
        return Err("Select an existing profile".into());
    }
    let dir = game.join(folder(kind)?);
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    let mut imported = Vec::new();
    let mut rejected = Vec::new();
    for p in paths {
        let path = Path::new(&p);
        let name = path
            .file_name()
            .and_then(|n| n.to_str())
            .unwrap_or("file")
            .to_owned();
        let result = (|| -> Result<(), String> {
            let meta = fs::symlink_metadata(path).map_err(|_| "File could not be read")?;
            if !meta.is_file() || meta.file_type().is_symlink() {
                return Err("Drop regular files, not folders or shortcuts".into());
            }
            if meta.len() > 1024 * 1024 * 1024 {
                return Err("File exceeds 1 GB".into());
            }
            let ext = path
                .extension()
                .and_then(|s| s.to_str())
                .unwrap_or("")
                .to_ascii_lowercase();
            if (kind == "Mods" && ext != "jar") || (kind != "Mods" && ext != "zip") {
                return Err(if kind == "Mods" {
                    "Choose a .jar mod"
                } else {
                    "Choose a .zip pack"
                }
                .into());
            }
            let info = metadata(path, kind)?;
            if kind == "Mods"
                && (managed_mod_names(&game).contains(&name)
                    || [
                        "unbox",
                        "fabric-api",
                        "sodium",
                        "lithium",
                        "ferritecore",
                        "entityculling",
                        "immediatelyfast",
                        "dynamic_fps",
                        "e4mc",
                    ]
                    .contains(&info["modId"].as_str().unwrap_or("")))
            {
                return Err("This client mod is managed by Unbox".into());
            }
            let target = dir.join(&name);
            if target.exists() {
                return Err("A file with this name already exists; nothing was replaced".into());
            }
            // Copy into a temporary file, then link without replacing any concurrent destination.
            let temp = dir.join(format!(".import-{}", uuid::Uuid::new_v4()));
            let copied = fs::copy(path, &temp).map_err(|e| e.to_string());
            let res = copied.and_then(|_| {
                fs::hard_link(&temp, &target)
                    .map_err(|_| "Destination exists or cannot be written".into())
            });
            let _ = fs::remove_file(&temp);
            res?;
            Ok(())
        })();
        match result {
            Ok(()) => imported.push(name),
            Err(e) => rejected.push(json!({"name":name,"reason":e})),
        }
    }
    Ok(json!({"imported":imported,"rejected":rejected}))
}
#[tauri::command]
pub async fn import_content(
    rt: tauri::State<'_, Runtime>,
    id: String,
    kind: String,
    paths: Vec<String>,
) -> Result<Value, String> {
    let rt = rt.inner().clone();
    tauri::async_runtime::spawn_blocking(move || import(&rt, &id, &kind, paths))
        .await
        .map_err(|_| "Import failed")?
}
#[tauri::command]
pub fn open_content_folder(
    rt: tauri::State<Runtime>,
    id: String,
    kind: String,
) -> Result<(), String> {
    let dir = profile_dir(&rt, &id)?.join(folder(&kind)?);
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    #[cfg(target_os = "macos")]
    let command = "open";
    #[cfg(target_os = "windows")]
    let command = "explorer";
    #[cfg(target_os = "linux")]
    let command = "xdg-open";
    std::process::Command::new(command)
        .arg(dir)
        .spawn()
        .map_err(|e| e.to_string())?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::{
        io::Write,
        sync::{Arc, Mutex},
    };
    struct Fixture {
        rt: Runtime,
        id: String,
    }
    impl Fixture {
        fn new() -> Self {
            let root =
                std::env::temp_dir().join(format!("unbox-import-test-{}", uuid::Uuid::new_v4()));
            let rt = Runtime {
                root: root.clone(),
                resources: root.clone(),
                job: Arc::new(Mutex::new(json!({"busy":false}))),
                auth: Arc::default(),
                social: Arc::default(),
            };
            let id = uuid::Uuid::new_v4().to_string();
            let dir = profile_dir(&rt, &id).unwrap();
            fs::create_dir_all(&dir).unwrap();
            fs::write(dir.join("profile.json"), b"{}").unwrap();
            Self { rt, id }
        }
        fn archive(&self, name: &str, entries: &[(&str, &[u8])]) -> String {
            let p = self.rt.root.join(name);
            let mut z = zip::ZipWriter::new(fs::File::create(&p).unwrap());
            for (n, b) in entries {
                z.start_file(*n, zip::write::SimpleFileOptions::default())
                    .unwrap();
                z.write_all(b).unwrap();
            }
            z.finish().unwrap();
            p.to_string_lossy().into_owned()
        }
    }
    impl Drop for Fixture {
        fn drop(&mut self) {
            let _ = fs::remove_dir_all(&self.rt.root);
        }
    }
    #[test]
    fn imported_files_stay_in_selected_profile_and_duplicates_never_replace() {
        let f = Fixture::new();
        let a = f.archive(
            "demo.jar",
            &[("fabric.mod.json", br#"{"id":"demo","name":"Demo"}"#)],
        );
        let first = import(&f.rt, &f.id, "Mods", vec![a.clone()]).unwrap();
        assert_eq!(first["imported"][0], "demo.jar");
        let dest = profile_dir(&f.rt, &f.id).unwrap().join("mods/demo.jar");
        let before = fs::read(&dest).unwrap();
        let again = import(&f.rt, &f.id, "Mods", vec![a]).unwrap();
        assert_eq!(again["rejected"].as_array().unwrap().len(), 1);
        assert_eq!(fs::read(dest).unwrap(), before);
        assert_eq!(list(&f.rt, &f.id, "Mods").unwrap()[0]["title"], "Demo");
    }
    #[test]
    fn packs_shaders_and_partial_rejection() {
        let f = Fixture::new();
        let pack = f.archive("pack.zip", &[("pack.mcmeta", b"{}")]);
        let wrong = f.archive("wrong.zip", &[("nested/pack.mcmeta", b"{}")]);
        let r = import(&f.rt, &f.id, "Packs", vec![pack, wrong]).unwrap();
        assert_eq!(r["imported"].as_array().unwrap().len(), 1);
        assert_eq!(r["rejected"].as_array().unwrap().len(), 1);
        let shader = f.archive("shader.zip", &[("shaders/a.vsh", b"shader")]);
        assert_eq!(
            import(&f.rt, &f.id, "Shaders", vec![shader]).unwrap()["imported"][0],
            "shader.zip"
        );
        assert!(folder("../../").is_err());
    }
    #[test]
    fn rejects_managed_mods_symlinks_and_running_profile() {
        let f = Fixture::new();
        let a = f.archive("renamed.jar", &[("fabric.mod.json", br#"{"id":"e4mc"}"#)]);
        assert_eq!(
            import(&f.rt, &f.id, "Mods", vec![a]).unwrap()["imported"],
            json!([])
        );
        let b = f.archive("valid.jar", &[("fabric.mod.json", br#"{"id":"demo"}"#)]);
        #[cfg(unix)]
        {
            let link = f.rt.root.join("link.jar");
            std::os::unix::fs::symlink(&b, &link).unwrap();
            assert_eq!(
                import(
                    &f.rt,
                    &f.id,
                    "Mods",
                    vec![link.to_string_lossy().into_owned()]
                )
                .unwrap()["imported"],
                json!([])
            );
        }
        *f.rt.job.lock().unwrap() = json!({"busy":true,"profileId":f.id});
        assert!(import(&f.rt, &f.id, "Mods", vec![b]).is_err());
    }
}
