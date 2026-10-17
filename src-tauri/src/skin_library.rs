use crate::{atomic, auth, profile_dir, Runtime};
use base64::{engine::general_purpose::STANDARD, Engine};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use sha2::{Digest, Sha256};
use std::{
    collections::BTreeMap,
    fs,
    path::{Path, PathBuf},
    sync::{Mutex, MutexGuard, OnceLock},
    time::{Duration, SystemTime, UNIX_EPOCH},
};

const MAX_SKINS: usize = 64;
#[derive(Clone, Serialize, Deserialize)]
struct Entry {
    id: String,
    name: String,
    model: String,
    created: u64,
}
#[derive(Default, Serialize, Deserialize)]
struct Index {
    skins: Vec<Entry>,
    #[serde(default)]
    selected: BTreeMap<String, String>,
    #[serde(default)]
    sequence: u64,
}
fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_millis() as u64
}
fn root(rt: &Runtime) -> PathBuf {
    rt.root.join("skin-library")
}
fn index(rt: &Runtime) -> Result<Index, String> {
    let p = root(rt).join("index.json");
    if !p.exists() {
        return Ok(Index::default());
    }
    serde_json::from_slice(&fs::read(p).map_err(|e| e.to_string())?)
        .map_err(|_| "Skin library index is damaged".into())
}
fn save(rt: &Runtime, v: &Index) -> Result<(), String> {
    atomic(
        &root(rt).join("index.json"),
        &serde_json::to_vec_pretty(v).map_err(|e| e.to_string())?,
    )
}
fn mutation_lock() -> MutexGuard<'static, ()> {
    static LOCK: OnceLock<Mutex<()>> = OnceLock::new();
    LOCK.get_or_init(|| Mutex::new(())).lock().unwrap()
}
fn file(rt: &Runtime, id: &str) -> Result<PathBuf, String> {
    if id.len() != 64 || !id.bytes().all(|b| b.is_ascii_hexdigit()) {
        return Err("Invalid skin ID".into());
    }
    Ok(root(rt).join(format!("{id}.png")))
}
fn public(rt: &Runtime, e: &Entry) -> Result<Value, String> {
    let bytes = fs::read(file(rt, &e.id)?).map_err(|_| "A saved skin file is missing")?;
    Ok(
        json!({"id":e.id,"name":e.name,"model":e.model,"created":e.created,"skin":format!("data:image/png;base64,{}",STANDARD.encode(bytes))}),
    )
}
fn list(rt: &Runtime) -> Result<Value, String> {
    let v = index(rt)?;
    Ok(Value::Array(
        v.skins.iter().filter_map(|e| public(rt, e).ok()).collect(),
    ))
}

#[tauri::command]
pub fn skin_library_list(rt: tauri::State<Runtime>) -> Result<Value, String> {
    list(&rt)
}
#[tauri::command]
pub fn skin_library_import(
    rt: tauri::State<Runtime>,
    name: String,
    png_base64: String,
    model: String,
) -> Result<Value, String> {
    if png_base64.len() > 1_400_000 {
        return Err("Choose a PNG skin under 1 MB".into());
    }
    let bytes = STANDARD
        .decode(png_base64)
        .map_err(|_| "Invalid PNG data")?;
    import_bytes(&rt, &name, bytes, &model)
}
fn import_bytes(rt: &Runtime, name: &str, bytes: Vec<u8>, model: &str) -> Result<Value, String> {
    auth::validate_png(&bytes, model)?;
    let _guard = mutation_lock();
    let mut v = index(&rt)?;
    let id = format!("{:x}", Sha256::digest(&bytes));
    if v.skins.len() >= MAX_SKINS && !v.skins.iter().any(|e| e.id == id) {
        return Err(format!("Skin library is limited to {MAX_SKINS} skins"));
    }
    let clean = name
        .trim()
        .trim_end_matches(".png")
        .chars()
        .take(48)
        .collect::<String>();
    let clean = if clean.is_empty() {
        "Imported skin".into()
    } else {
        clean
    };
    atomic(&file(&rt, &id)?, &bytes)?;
    if let Some(e) = v.skins.iter_mut().find(|e| e.id == id) {
        e.name = clean;
        e.model = model.to_owned()
    } else {
        v.skins.push(Entry {
            id,
            name: clean,
            model: model.to_owned(),
            created: now(),
        })
    }
    v.sequence = v.sequence.saturating_add(1);
    save(&rt, &v)?;
    list(&rt)
}
#[tauri::command]
pub fn skin_library_delete(rt: tauri::State<Runtime>, id: String) -> Result<Value, String> {
    delete(&rt, &id)
}
fn delete(rt: &Runtime, id: &str) -> Result<Value, String> {
    let path = file(&rt, &id)?;
    let _guard = mutation_lock();
    let mut v = index(&rt)?;
    if !v.skins.iter().any(|e| e.id == id) {
        return Err("Saved skin not found".into());
    }
    v.skins.retain(|e| e.id != id);
    v.selected.retain(|_, selected| selected != id);
    v.sequence = v.sequence.saturating_add(1);
    save(&rt, &v)?;
    if path.exists() {
        fs::remove_file(path).map_err(|e| e.to_string())?
    }
    list(&rt)
}
fn commit_selection(rt: &Runtime, account: &str, id: &str) -> Result<(), String> {
    let _guard = mutation_lock();
    let mut current = index(rt)?;
    if !current.skins.iter().any(|e| e.id == id) {
        return Err("Saved skin was removed while it was being applied".into());
    }
    current.selected.insert(account.to_owned(), id.to_owned());
    current.sequence = current.sequence.saturating_add(1);
    save(rt, &current)
}
#[tauri::command]
pub async fn skin_library_apply(
    rt: tauri::State<'_, Runtime>,
    account_id: String,
    id: String,
) -> Result<Value, String> {
    let e = index(&rt)?
        .skins
        .iter()
        .find(|e| e.id == id)
        .ok_or("Saved skin not found")?
        .clone();
    let bytes = fs::read(file(&rt, &id)?).map_err(|_| "A saved skin file is missing")?;
    let selected = auth::restore_account(&rt, &Value::Null)?;
    if account_key(&selected) != account_id {
        return Err("Selected account changed".into());
    }
    let result = if selected["type"] == "microsoft" {
        auth::apply_skin_bytes(
            &rt,
            selected["accountId"]
                .as_str()
                .ok_or("Microsoft account ID is missing")?,
            bytes,
            &e.model,
        )
        .await?
    } else {
        auth::validate_local_skin(&bytes)?;
        auth::account_snapshot(&rt)?
    };
    commit_selection(&rt, &account_key(&selected), &id)?;
    if let Some(g) = game(&rt) {
        publish(&rt, &g)?
    }
    Ok(result)
}

fn account_key(a: &Value) -> String {
    a["accountId"]
        .as_str()
        .filter(|s| !s.is_empty())
        .map(str::to_owned)
        .unwrap_or_else(|| {
            format!(
                "{}:{}",
                a["type"].as_str().unwrap_or("local"),
                a["uuid"]
                    .as_str()
                    .or_else(|| a["name"].as_str())
                    .unwrap_or("")
            )
        })
}

fn game(rt: &Runtime) -> Option<PathBuf> {
    let j = rt.job.lock().unwrap();
    if j["stage"] != "Playing" {
        return None;
    }
    profile_dir(rt, j["profileId"].as_str()?).ok()
}
fn publish(rt: &Runtime, g: &Path) -> Result<(), String> {
    let account = auth::restore_account(rt, &Value::Null)?;
    let account_id = account_key(&account);
    let library = index(rt)?;
    let active = library
        .selected
        .get(&account_key(&account))
        .and_then(|id| library.skins.iter().find(|e| &e.id == id));
    let entries = library
        .skins
        .iter()
        .into_iter()
        .map(|e| json!({"id":e.id,"name":e.name,"model":e.model,"created":e.created}))
        .collect::<Vec<_>>();
    let active_json = if let Some(e) = active {
        let bytes = fs::read(file(rt, &e.id)?).map_err(|_| "A saved skin file is missing")?;
        atomic(&g.join("unbox-local-skin.png"), &bytes)?;
        json!({"id":e.id,"sha256":format!("{:x}",Sha256::digest(&bytes)),"model":e.model})
    } else {
        Value::Null
    };
    atomic(&g.join("unbox-skin-library.json"),&serde_json::to_vec(&json!({"updated":now(),"sequence":library.sequence,"accountId":account_id,"accountUuid":account["uuid"],"accountName":account["name"],"active":active_json,"skins":entries})).unwrap())
}
pub fn start(rt: Runtime) {
    tauri::async_runtime::spawn(async move {
        let mut last = String::new();
        loop {
            tokio::time::sleep(Duration::from_secs(1)).await;
            let Some(g) = game(&rt) else { continue };
            let _ = publish(&rt, &g);
            let p = g.join("unbox-skin-command.json");
            let Ok(b) = fs::read(&p) else { continue };
            if b.len() > 4096 {
                continue;
            }
            let Ok(c) = serde_json::from_slice::<Value>(&b) else {
                continue;
            };
            let request = c["requestId"].as_str().unwrap_or("");
            if request.is_empty()
                || request == last
                || !c["at"]
                    .as_u64()
                    .is_some_and(|n| now().saturating_sub(n) < 15000)
            {
                continue;
            }
            last = request.into();
            let result = async {
                let account_id = c["accountId"].as_str().ok_or("Missing account")?;
                let selected = auth::restore_account(&rt, &Value::Null)?;
                if account_key(&selected) != account_id {
                    return Err("Game account changed".into());
                }
                let id = c["id"].as_str().ok_or("Missing skin")?;
                let e = index(&rt)?
                    .skins
                    .iter()
                    .find(|e| e.id == id)
                    .ok_or("Saved skin not found")?
                    .clone();
                let bytes = fs::read(file(&rt, id)?).map_err(|_| "A saved skin file is missing")?;
                if selected["type"] == "microsoft" {
                    auth::apply_skin_bytes(
                        &rt,
                        selected["accountId"]
                            .as_str()
                            .ok_or("Microsoft account ID is missing")?,
                        bytes,
                        &e.model,
                    )
                    .await?;
                } else {
                    auth::validate_local_skin(&bytes)?;
                }
                commit_selection(&rt, &account_key(&selected), id)?;
                publish(&rt, &g)
            }
            .await;
            let ack =
                json!({"requestId":request,"ok":result.is_ok(),"error":result.err(),"at":now()});
            let _ = atomic(
                &g.join("unbox-skin-ack.json"),
                &serde_json::to_vec(&ack).unwrap(),
            );
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;
    fn runtime() -> Runtime {
        Runtime {
            root: std::env::temp_dir().join(format!("unbox-skin-test-{}", uuid::Uuid::new_v4())),
            resources: PathBuf::new(),
            job: Default::default(),
            auth: Default::default(),
            social: Default::default(),
        }
    }
    fn png(color: u8) -> Vec<u8> {
        let mut bytes = Vec::new();
        {
            let mut encoder = png::Encoder::new(&mut bytes, 64, 64);
            encoder.set_color(png::ColorType::Rgba);
            encoder.set_depth(png::BitDepth::Eight);
            encoder
                .write_header()
                .unwrap()
                .write_image_data(&vec![color; 64 * 64 * 4])
                .unwrap();
        }
        bytes
    }
    #[test]
    fn ids_cannot_escape_library() {
        let rt = runtime();
        assert!(file(&rt, "../skin").is_err());
        assert!(file(&rt, &"a".repeat(64)).is_ok());
    }
    #[test]
    fn reimporting_changes_model_and_advances_sequence() {
        let rt = runtime();
        let bytes = png(90);
        import_bytes(&rt, "First", bytes.clone(), "classic").unwrap();
        import_bytes(&rt, "Renamed", bytes, "slim").unwrap();
        let saved = index(&rt).unwrap();
        assert_eq!(saved.sequence, 2);
        assert_eq!(saved.skins.len(), 1);
        assert_eq!(saved.skins[0].name, "Renamed");
        assert_eq!(saved.skins[0].model, "slim");
        fs::remove_dir_all(&rt.root).unwrap();
    }
    #[test]
    fn deleting_selected_skin_clears_every_account_and_advances_sequence() {
        let rt = runtime();
        let bytes = png(120);
        import_bytes(&rt, "Selected", bytes.clone(), "classic").unwrap();
        let id = format!("{:x}", Sha256::digest(bytes));
        commit_selection(&rt, "account-a", &id).unwrap();
        commit_selection(&rt, "account-b", &id).unwrap();
        let before = index(&rt).unwrap().sequence;
        delete(&rt, &id).unwrap();
        let saved = index(&rt).unwrap();
        assert_eq!(saved.sequence, before + 1);
        assert!(saved.selected.is_empty());
        assert!(saved.skins.is_empty());
        assert!(!file(&rt, &id).unwrap().exists());
        fs::remove_dir_all(&rt.root).unwrap();
    }
    #[test]
    fn selection_commit_reloads_index_and_rejects_removed_entry() {
        let rt = runtime();
        let first = png(30);
        let second = png(60);
        import_bytes(&rt, "First", first.clone(), "classic").unwrap();
        let first_id = format!("{:x}", Sha256::digest(first));
        let stale = index(&rt).unwrap();
        import_bytes(&rt, "Second", second.clone(), "classic").unwrap();
        commit_selection(&rt, "account", &first_id).unwrap();
        let current = index(&rt).unwrap();
        assert_eq!(current.skins.len(), 2);
        assert!(current.sequence > stale.sequence);
        let second_id = format!("{:x}", Sha256::digest(second));
        delete(&rt, &second_id).unwrap();
        assert!(commit_selection(&rt, "account", &second_id).is_err());
        assert_eq!(index(&rt).unwrap().selected["account"], first_id);
        fs::remove_dir_all(&rt.root).unwrap();
    }
}
