use crate::{atomic, profile_dir, Runtime};
use serde_json::{json, Value};
use std::{
    fs,
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};

const META: &str = ".unbox-world.json";

fn safe_name(name: &str) -> Result<&str, String> {
    if name.is_empty()
        || name.len() > 255
        || name == "."
        || name == ".."
        || name.contains(['/', '\\'])
    {
        return Err("Invalid world folder".into());
    }
    Ok(name)
}

fn same_file(a: &Path, b: &Path) -> Result<bool, String> {
    use sha2::{Digest, Sha256};
    let digest = |p: &Path| -> Result<Vec<u8>, String> {
        let bytes = fs::read(p).map_err(|e| e.to_string())?;
        Ok(Sha256::digest(bytes).to_vec())
    };
    Ok(fs::metadata(a).map_err(|e| e.to_string())?.len()
        == fs::metadata(b).map_err(|e| e.to_string())?.len()
        && digest(a)? == digest(b)?)
}

fn same_entry(a: &Path, b: &Path) -> Result<bool, String> {
    let am = fs::symlink_metadata(a).map_err(|e| e.to_string())?;
    let bm = fs::symlink_metadata(b).map_err(|e| e.to_string())?;
    if am.file_type().is_symlink() || bm.file_type().is_symlink() {
        return Ok(false);
    }
    if am.is_file() && bm.is_file() {
        return same_file(a, b);
    }
    if !am.is_dir() || !bm.is_dir() {
        return Ok(false);
    }
    let mut names = fs::read_dir(a)
        .map_err(|e| e.to_string())?
        .map(|e| e.map(|v| v.file_name()).map_err(|e| e.to_string()))
        .collect::<Result<Vec<_>, _>>()?;
    let mut other = fs::read_dir(b)
        .map_err(|e| e.to_string())?
        .map(|e| e.map(|v| v.file_name()).map_err(|e| e.to_string()))
        .collect::<Result<Vec<_>, _>>()?;
    names.sort();
    other.sort();
    if names != other {
        return Ok(false);
    }
    for name in names {
        if !same_entry(&a.join(&name), &b.join(name))? {
            return Ok(false);
        }
    }
    Ok(true)
}

fn remove_entry(path: &Path) {
    if path.is_dir() {
        let _ = fs::remove_dir_all(path);
    } else {
        let _ = fs::remove_file(path);
    }
}
fn cleanup(entries: &[(PathBuf, String)]) {
    for (path, _) in entries {
        remove_entry(path)
    }
}
fn cleanup_paths(entries: &[PathBuf]) {
    for path in entries {
        remove_entry(path)
    }
}
fn conflict_name(base: &str, suffix: &str, n: usize) -> String {
    let path = Path::new(base);
    match (path.file_stem(), path.extension()) {
        (Some(stem), Some(ext)) => format!(
            "{}.{}-{}.{}",
            stem.to_string_lossy(),
            suffix,
            n,
            ext.to_string_lossy()
        ),
        _ => format!("{base}.{suffix}-{n}"),
    }
}

fn copy_tree(source: &Path, target: &Path, allow_identical: bool) -> Result<(), String> {
    fs::create_dir_all(target).map_err(|e| e.to_string())?;
    for entry in fs::read_dir(source).map_err(|e| e.to_string())? {
        let entry = entry.map_err(|e| e.to_string())?;
        let from = entry.path();
        let to = target.join(entry.file_name());
        let meta = fs::symlink_metadata(&from).map_err(|e| e.to_string())?;
        if meta.file_type().is_symlink() {
            return Err(format!("Cannot migrate shortcut {}", from.display()));
        }
        if meta.is_dir() {
            if to.exists() && !to.is_dir() {
                return Err(format!("Library conflict: {}", to.display()));
            }
            copy_tree(&from, &to, allow_identical)?;
        } else if meta.is_file() {
            if to.exists() {
                if allow_identical && to.is_file() && same_file(&from, &to)? {
                    continue;
                }
                return Err(format!("Library conflict: {} already exists", to.display()));
            }
            fs::copy(&from, &to).map_err(|e| e.to_string())?;
        }
    }
    Ok(())
}

fn link_dir(target: &Path, link: &Path) -> Result<(), String> {
    #[cfg(unix)]
    {
        std::os::unix::fs::symlink(target, link).map_err(|e| e.to_string())
    }
    #[cfg(windows)]
    {
        let status = crate::process::command("cmd")
            .args(["/C", "mklink", "/J"])
            .arg(link)
            .arg(target)
            .status()
            .map_err(|e| e.to_string())?;
        if status.success() {
            Ok(())
        } else {
            Err("Could not connect the shared library to this profile".into())
        }
    }
}

fn migrate_worlds(profile: &Path, library: &Path, suffix: &str) -> Result<(), String> {
    fs::create_dir_all(library).map_err(|e| e.to_string())?;
    let current = profile.join("saves");
    if let Ok(meta) = fs::symlink_metadata(&current) {
        if meta.file_type().is_symlink() {
            return Ok(());
        }
        if !meta.is_dir() {
            return Err("Profile saves is not a folder".into());
        }
        let kept = profile.join("saves.unbox-profile-copy");
        if kept.exists() {
            return Err(format!("Migration copy already exists: {}", kept.display()));
        }
        let mut staged = Vec::new();
        for entry in fs::read_dir(&current).map_err(|e| e.to_string())?.flatten() {
            if !entry.path().is_dir() {
                continue;
            }
            let base = entry.file_name().to_string_lossy().into_owned();
            let mut name = base.clone();
            let mut n = 1;
            while library.join(&name).exists()
                || staged.iter().any(|(_, v): &(PathBuf, String)| v == &name)
            {
                name = format!("{base} · {suffix}-{n}");
                n += 1
            }
            let temp = library.join(format!(".migrate-{}", uuid::Uuid::new_v4()));
            if let Err(e) = copy_tree(&entry.path(), &temp, false) {
                remove_entry(&temp);
                cleanup(&staged);
                return Err(e);
            }
            staged.push((temp, name));
        }
        let mut committed: Vec<PathBuf> = Vec::new();
        for (temp, name) in &staged {
            let dest = library.join(name);
            if let Err(e) = fs::rename(temp, &dest) {
                for (t, _) in &staged {
                    let _ = fs::remove_dir_all(t);
                }
                for d in committed {
                    let _ = fs::remove_dir_all(d);
                }
                return Err(e.to_string());
            }
            committed.push(dest)
        }
        if let Err(e) = fs::rename(&current, &kept) {
            cleanup_paths(&committed);
            return Err(e.to_string());
        }
        if let Err(e) = link_dir(library, &current) {
            let _ = fs::rename(&kept, &current);
            cleanup_paths(&committed);
            return Err(e);
        }
    } else {
        link_dir(library, &current)?
    }
    Ok(())
}
fn migrate_packs(profile: &Path, library: &Path, suffix: &str) -> Result<(), String> {
    fs::create_dir_all(library).map_err(|e| e.to_string())?;
    let current = profile.join("resourcepacks");
    if let Ok(meta) = fs::symlink_metadata(&current) {
        if meta.file_type().is_symlink() {
            return Ok(());
        }
        if !meta.is_dir() {
            return Err("Profile resourcepacks is not a folder".into());
        }
        let kept = profile.join("resourcepacks.unbox-profile-copy");
        if kept.exists() {
            return Err(format!("Migration copy already exists: {}", kept.display()));
        }
        let mut staged: Vec<(PathBuf, String)> = Vec::new();
        for entry in fs::read_dir(&current).map_err(|e| e.to_string())?.flatten() {
            let path = entry.path();
            let meta = fs::symlink_metadata(&path).map_err(|e| e.to_string())?;
            if meta.file_type().is_symlink() {
                cleanup(&staged);
                return Err(format!("Cannot migrate shortcut {}", path.display()));
            }
            if !meta.is_file() && !meta.is_dir() {
                continue;
            }
            let base = entry.file_name().to_string_lossy().into_owned();
            let existing = library.join(&base);
            if existing.exists() && same_entry(&path, &existing)? {
                continue;
            }
            let mut name = base.clone();
            let mut n = 1;
            while library.join(&name).exists() || staged.iter().any(|(_, v)| v == &name) {
                name = conflict_name(&base, suffix, n);
                n += 1
            }
            let temp = library.join(format!(".migrate-{}", uuid::Uuid::new_v4()));
            let copied = if meta.is_dir() {
                copy_tree(&path, &temp, false)
            } else {
                fs::copy(&path, &temp)
                    .map(|_| ())
                    .map_err(|e| e.to_string())
            };
            if let Err(e) = copied {
                remove_entry(&temp);
                cleanup(&staged);
                return Err(e);
            }
            staged.push((temp, name))
        }
        let mut committed = Vec::new();
        for (temp, name) in &staged {
            let dest = library.join(name);
            if let Err(e) = fs::rename(temp, &dest) {
                cleanup(&staged);
                cleanup_paths(&committed);
                return Err(e.to_string());
            }
            committed.push(dest)
        }
        if let Err(e) = fs::rename(&current, &kept) {
            cleanup_paths(&committed);
            return Err(e.to_string());
        }
        if let Err(e) = link_dir(library, &current) {
            let _ = fs::rename(&kept, &current);
            cleanup_paths(&committed);
            return Err(e);
        }
    } else {
        link_dir(library, &current)?
    }
    Ok(())
}

fn personal_profile(path: &Path) -> bool {
    fs::read(path.join("profile.json"))
        .ok()
        .and_then(|b| serde_json::from_slice::<Value>(&b).ok())
        .is_some_and(|p| !p["sharedHost"].is_string())
}

pub fn ensure(rt: &Runtime, id: &str) -> Result<(), String> {
    let job = rt.job.lock().unwrap();
    if job["busy"] == true {
        return Err("Close Minecraft before changing the shared library".into());
    }
    drop(job);
    let profile = profile_dir(rt, id)?;
    if !personal_profile(&profile) {
        return Ok(());
    }
    let library = rt.root.join("library");
    migrate_worlds(&profile, &library.join("worlds"), &id[..8])?;
    migrate_packs(&profile, &library.join("resourcepacks"), &id[..8])?;
    Ok(())
}

#[tauri::command]
pub fn set_profile_environment(
    rt: tauri::State<Runtime>,
    id: String,
    version: String,
    loader: String,
) -> Result<Value, String> {
    if !crate::versions::supported(&version) || !["fabric", "neoforge"].contains(&loader.as_str()) {
        return Err("Choose Minecraft 26.1, 26.1.2 or 26.3 with Fabric or NeoForge".into());
    }
    let path = profile_dir(&rt, &id)?.join("profile.json");
    let mut profile: Value = serde_json::from_slice(&fs::read(&path).map_err(|e| e.to_string())?)
        .map_err(|_| "Invalid profile")?;
    if profile["sharedHost"].is_string() {
        return Err("A friend's managed environment cannot be changed".into());
    }
    if profile["version"] != version || profile["loader"] != loader {
        return Err("Create a separate profile for each Minecraft version and loader".into());
    }
    profile["version"] = json!(version);
    profile["loader"] = json!(loader);
    atomic(&path, &serde_json::to_vec_pretty(&profile).unwrap())?;
    Ok(profile)
}

fn ensure_all(rt: &Runtime) -> Result<(), String> {
    let profiles = rt.root.join("profiles");
    if !profiles.exists() {
        fs::create_dir_all(rt.root.join("library/worlds")).map_err(|e| e.to_string())?;
        return Ok(());
    }
    for entry in fs::read_dir(profiles).map_err(|e| e.to_string())?.flatten() {
        let id = entry.file_name().to_string_lossy().into_owned();
        if uuid::Uuid::parse_str(&id).is_ok() && personal_profile(&entry.path()) {
            ensure(rt, &id)?;
        }
    }
    Ok(())
}

fn gameplay_mods(rt: &Runtime, profile: &Value) -> Vec<Value> {
    let Some(id) = profile["id"].as_str() else {
        return vec![];
    };
    let Ok(game) = profile_dir(rt, id) else {
        return vec![];
    };
    let hidden = crate::managed_mod_names(&game);
    let mut mods = Vec::new();
    if let Ok(entries) = fs::read_dir(game.join("mods")) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if name.ends_with(".jar") && !hidden.contains(&name) && entry.path().is_file() {
                mods.push(json!(name));
            }
        }
    }
    mods.sort_by(|a, b| a.as_str().cmp(&b.as_str()));
    mods
}
fn setup(rt: &Runtime, profile: &Value) -> Value {
    json!({"version":profile["version"],"loader":profile["loader"],"gameplayMods":gameplay_mods(rt,profile)})
}

fn read_meta(world: &Path) -> Value {
    fs::read(world.join(META))
        .ok()
        .and_then(|b| serde_json::from_slice(&b).ok())
        .unwrap_or_else(|| json!({}))
}

fn modified(path: &Path) -> u64 {
    fs::metadata(path)
        .and_then(|m| m.modified())
        .unwrap_or(SystemTime::UNIX_EPOCH)
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
}

#[tauri::command]
pub fn list_worlds(rt: tauri::State<Runtime>) -> Result<Value, String> {
    ensure_all(&rt)?;
    let root = rt.root.join("library/worlds");
    let mut worlds = Vec::new();
    for entry in fs::read_dir(&root).map_err(|e| e.to_string())?.flatten() {
        if !entry.path().is_dir() || !entry.path().join("level.dat").is_file() {
            continue;
        }
        let folder = entry.file_name().to_string_lossy().into_owned();
        let meta = read_meta(&entry.path());
        worlds.push(json!({
            "folder": folder,
            "name": meta["name"].as_str().unwrap_or(&entry.file_name().to_string_lossy()),
            "modified": modified(&entry.path().join("level.dat")),
            "version": meta["version"], "loader": meta["loader"],
            "gameplayMods": meta["gameplayMods"].as_array().cloned().unwrap_or_default()
        }));
    }
    worlds.sort_by_key(|w| std::cmp::Reverse(w["modified"].as_u64().unwrap_or(0)));
    Ok(json!(worlds))
}

fn version_rank(version: &str) -> Option<u8> {
    match version {
        "26.1" => Some(1),
        "26.1.2" => Some(2),
        "26.3" => Some(3),
        _ => None,
    }
}

#[tauri::command]
pub fn preflight_world(
    rt: tauri::State<Runtime>,
    id: String,
    folder: String,
    backup: bool,
) -> Result<Value, String> {
    preflight(rt.inner(), &id, &folder, backup)
}

fn preflight(rt: &Runtime, id: &str, folder: &str, backup: bool) -> Result<Value, String> {
    ensure(rt, id)?;
    safe_name(&folder)?;
    let profile: Value = serde_json::from_slice(
        &fs::read(profile_dir(rt, id)?.join("profile.json")).map_err(|e| e.to_string())?,
    )
    .map_err(|_| "Invalid profile")?;
    let world = rt.root.join("library/worlds").join(&folder);
    if !world.join("level.dat").is_file() {
        return Err("World is missing level.dat".into());
    }
    let old = read_meta(&world);
    let selected = setup(rt, &profile);
    let old_version = old["version"].as_str();
    let next_version = profile["version"]
        .as_str()
        .ok_or("Missing profile version")?;
    let old_loader = old["loader"].as_str();
    let gameplay = old["gameplayMods"].as_array().cloned().unwrap_or_default();
    if old_version
        .and_then(version_rank)
        .is_some_and(|v| v > version_rank(next_version).unwrap_or(0))
    {
        return Ok(
            json!({"status":"blocked","reason":"downgrade","savedSetup":old,"selectedSetup":selected}),
        );
    }
    if !gameplay.is_empty()
        && old_loader.is_some_and(|v| v != profile["loader"].as_str().unwrap_or(""))
    {
        return Ok(
            json!({"status":"blocked","reason":"loader","savedSetup":old,"selectedSetup":selected}),
        );
    }
    let needs_backup = old_version.is_none()
        || old_version != Some(next_version)
        || old_loader != profile["loader"].as_str();
    if needs_backup && !backup {
        return Ok(
            json!({"status":"backup-required","reason":if old_version.is_none(){"unknown"}else{"upgrade"},"savedSetup":old,"selectedSetup":selected}),
        );
    }
    let mut backup_path = Value::Null;
    if needs_backup {
        let stamp = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap_or_default()
            .as_secs();
        let target = rt.root.join("library/backups").join(&folder).join(format!(
            "{stamp}-{}-{}",
            old_version.unwrap_or("unknown"),
            old_loader.unwrap_or("unknown")
        ));
        if target.exists() {
            return Err("A backup with this timestamp already exists; retry".into());
        }
        copy_tree(&world, &target, false)?;
        backup_path = json!(target.to_string_lossy());
    }
    let recorded_gameplay = if gameplay.is_empty() {
        selected["gameplayMods"].clone()
    } else {
        json!(gameplay)
    };
    let meta = json!({"schema":1,"name":old["name"].as_str().unwrap_or(&folder),"version":next_version,"loader":profile["loader"],"gameplayMods":recorded_gameplay});
    atomic(
        &world.join(META),
        &serde_json::to_vec_pretty(&meta).unwrap(),
    )?;
    Ok(json!({"status":"ready","backup":backup_path,"savedSetup":meta,"selectedSetup":selected}))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    fn runtime() -> Runtime {
        let root = std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());
        Runtime {
            root: root.clone(),
            resources: root,
            job: Arc::default(),
            auth: Arc::default(),
            social: Arc::default(),
        }
    }
    fn profile(rt: &Runtime, version: &str, loader: &str) -> String {
        let id = uuid::Uuid::new_v4().to_string();
        let p = profile_dir(rt, &id).unwrap();
        fs::create_dir_all(p.join("saves/World")).unwrap();
        fs::write(p.join("saves/World/level.dat"), b"fixture").unwrap();
        atomic(
            &p.join("profile.json"),
            serde_json::to_vec(&json!({"id":id,"version":version,"loader":loader}))
                .unwrap()
                .as_slice(),
        )
        .unwrap();
        id
    }
    #[test]
    fn migration_keeps_original_copy_and_renames_world_conflicts() {
        let rt = runtime();
        let a = profile(&rt, "26.1", "fabric");
        ensure(&rt, &a).unwrap();
        let p = profile_dir(&rt, &a).unwrap();
        assert!(p.join("saves.unbox-profile-copy/World/level.dat").is_file());
        assert!(rt.root.join("library/worlds/World/level.dat").is_file());
        let b = profile(&rt, "26.1.2", "neoforge");
        ensure(&rt, &b).unwrap();
        assert!(profile_dir(&rt, &b)
            .unwrap()
            .join("saves.unbox-profile-copy/World/level.dat")
            .is_file());
        let count = fs::read_dir(rt.root.join("library/worlds"))
            .unwrap()
            .filter(|e| {
                e.as_ref()
                    .is_ok_and(|v| v.path().join("level.dat").is_file())
            })
            .count();
        assert_eq!(count, 2);
        fs::remove_dir_all(rt.root).unwrap();
    }
    #[test]
    fn version_order_prevents_downgrades() {
        assert_eq!(version_rank("26.1.2").unwrap(), 2);
        assert!(version_rank("26.1").unwrap() < version_rank("26.1.2").unwrap());
        assert!(version_rank("26.1.2").unwrap() < version_rank("26.3").unwrap());
    }
    #[test]
    fn backup_is_a_separate_copy_and_never_overwrites() {
        let rt = runtime();
        let source = rt.root.join("world");
        let backup = rt.root.join("backup");
        fs::create_dir_all(&source).unwrap();
        fs::write(source.join("level.dat"), b"before").unwrap();
        copy_tree(&source, &backup, false).unwrap();
        fs::write(source.join("level.dat"), b"after").unwrap();
        assert_eq!(fs::read(backup.join("level.dat")).unwrap(), b"before");
        assert!(copy_tree(&source, &backup, false).is_err());
        assert_eq!(fs::read(backup.join("level.dat")).unwrap(), b"before");
        fs::remove_dir_all(rt.root).unwrap();
    }
    #[test]
    fn preflight_blocks_downgrade_and_cross_loader_gameplay_mods() {
        let rt = runtime();
        let id = profile(&rt, "26.1", "fabric");
        ensure(&rt, &id).unwrap();
        let world = rt.root.join("library/worlds/World");
        atomic(
            &world.join(META),
            serde_json::to_vec(&json!({"version":"26.1.2","loader":"fabric","gameplayMods":[]}))
                .unwrap()
                .as_slice(),
        )
        .unwrap();
        let downgrade = preflight(&rt, &id, "World", false).unwrap();
        assert_eq!(downgrade["reason"], "downgrade");
        assert_eq!(downgrade["status"], "blocked");
        let profile_path = profile_dir(&rt, &id).unwrap().join("profile.json");
        atomic(
            &profile_path,
            serde_json::to_vec(&json!({"id":id,"version":"26.1.2","loader":"neoforge"}))
                .unwrap()
                .as_slice(),
        )
        .unwrap();
        atomic(
            &world.join(META),
            serde_json::to_vec(
                &json!({"version":"26.1.2","loader":"fabric","gameplayMods":["example"]}),
            )
            .unwrap()
            .as_slice(),
        )
        .unwrap();
        let loader = preflight(&rt, &id, "World", false).unwrap();
        assert_eq!(loader["reason"], "loader");
        assert_eq!(loader["status"], "blocked");
        atomic(
            &world.join(META),
            &serde_json::to_vec(&json!({"version":"26.3","loader":"neoforge","gameplayMods":[]})).unwrap(),
        ).unwrap();
        let downgrade = preflight(&rt, &id, "World", false).unwrap();
        assert_eq!(downgrade["reason"], "downgrade");
        assert_eq!(downgrade["status"], "blocked");
        fs::remove_dir_all(rt.root).unwrap();
    }
    #[test]
    fn upgrade_requires_backup_then_records_setup() {
        let rt = runtime();
        let id = profile(&rt, "26.1.2", "fabric");
        ensure(&rt, &id).unwrap();
        let world = rt.root.join("library/worlds/World");
        atomic(
            &world.join(META),
            serde_json::to_vec(&json!({"version":"26.1","loader":"fabric","gameplayMods":[]}))
                .unwrap()
                .as_slice(),
        )
        .unwrap();
        let check = preflight(&rt, &id, "World", false).unwrap();
        assert_eq!(check["status"], "backup-required");
        let ready = preflight(&rt, &id, "World", true).unwrap();
        assert_eq!(ready["status"], "ready");
        let backup = ready["backup"].as_str().unwrap();
        assert!(Path::new(backup).join("level.dat").is_file());
        assert_eq!(read_meta(&world)["version"], "26.1.2");
        fs::remove_dir_all(rt.root).unwrap();
    }
    #[test]
    fn playing_never_migrates_a_profile() {
        let rt = runtime();
        let id = profile(&rt, "26.1.2", "fabric");
        *rt.job.lock().unwrap() = json!({"busy":true,"stage":"Playing","profileId":id});
        assert!(ensure(&rt, &id).is_err());
        assert!(profile_dir(&rt, &id)
            .unwrap()
            .join("saves/World/level.dat")
            .is_file());
        assert!(!rt.root.join("library/worlds/World").exists());
        fs::remove_dir_all(rt.root).unwrap();
    }
    #[test]
    fn pack_conflicts_keep_the_zip_extension() {
        let rt = runtime();
        let profile = rt.root.join("pack-profile");
        let source = profile.join("resourcepacks");
        let library = rt.root.join("pack-library");
        fs::create_dir_all(&source).unwrap();
        fs::create_dir_all(&library).unwrap();
        fs::write(source.join("look.zip"), b"new").unwrap();
        fs::write(library.join("look.zip"), b"old").unwrap();
        migrate_packs(&profile, &library, "profile").unwrap();
        assert_eq!(
            fs::read(library.join("look.profile-1.zip")).unwrap(),
            b"new"
        );
        assert!(!library.join("look.zip.profile-1").exists());
        assert_eq!(
            fs::read(profile.join("resourcepacks.unbox-profile-copy/look.zip")).unwrap(),
            b"new"
        );
        fs::remove_dir_all(rt.root).unwrap();
    }
    #[test]
    fn directory_resource_packs_are_migrated_and_preserved() {
        let rt = runtime();
        let profile = rt.root.join("pack-profile");
        let source = profile.join("resourcepacks/Folder Pack/assets/example");
        let library = rt.root.join("pack-library");
        fs::create_dir_all(&source).unwrap();
        fs::write(profile.join("resourcepacks/Folder Pack/pack.mcmeta"), b"{}").unwrap();
        fs::write(source.join("texture.png"), b"fixture").unwrap();
        migrate_packs(&profile, &library, "profile").unwrap();
        assert_eq!(
            fs::read(library.join("Folder Pack/assets/example/texture.png")).unwrap(),
            b"fixture"
        );
        assert!(profile
            .join("resourcepacks.unbox-profile-copy/Folder Pack/pack.mcmeta")
            .is_file());
        fs::remove_dir_all(rt.root).unwrap();
    }
    #[cfg(unix)]
    #[test]
    fn failed_pack_copy_cleans_staging_and_keeps_the_profile_untouched() {
        use std::os::unix::fs::symlink;
        let rt = runtime();
        let profile = rt.root.join("pack-profile");
        let pack = profile.join("resourcepacks/Broken Pack");
        let library = rt.root.join("pack-library");
        fs::create_dir_all(&pack).unwrap();
        fs::write(pack.join("pack.mcmeta"), b"{}").unwrap();
        symlink("pack.mcmeta", pack.join("shortcut")).unwrap();
        assert!(migrate_packs(&profile, &library, "profile").is_err());
        assert!(profile
            .join("resourcepacks/Broken Pack/pack.mcmeta")
            .is_file());
        assert!(!profile.join("resourcepacks.unbox-profile-copy").exists());
        assert_eq!(fs::read_dir(&library).unwrap().count(), 0);
        fs::remove_dir_all(rt.root).unwrap();
    }
    #[cfg(unix)]
    #[test]
    fn failed_profile_switch_rolls_back_committed_packs() {
        use std::os::unix::fs::PermissionsExt;
        let rt = runtime();
        let profile = rt.root.join("pack-profile");
        let source = profile.join("resourcepacks");
        let library = rt.root.join("pack-library");
        fs::create_dir_all(&source).unwrap();
        fs::write(source.join("look.zip"), b"fixture").unwrap();
        fs::set_permissions(&profile, fs::Permissions::from_mode(0o555)).unwrap();
        let result = migrate_packs(&profile, &library, "profile");
        fs::set_permissions(&profile, fs::Permissions::from_mode(0o755)).unwrap();
        assert!(result.is_err());
        assert_eq!(fs::read(source.join("look.zip")).unwrap(), b"fixture");
        assert_eq!(fs::read_dir(&library).unwrap().count(), 0);
        assert!(!profile.join("resourcepacks.unbox-profile-copy").exists());
        fs::remove_dir_all(rt.root).unwrap();
    }
}
