use crate::{Runtime,atomic,profile_dir};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::{fs,io::Read,path::Path};
fn digest(path:&Path)->Result<String,String>{let mut f=fs::File::open(path).map_err(|_|"Missing cached mod")?;let mut sha=Sha256::new();let mut b=[0u8;65536];loop{let n=f.read(&mut b).map_err(|_|"Cannot read cached mod")?;if n==0{break}sha.update(&b[..n]);}Ok(format!("{:x}",sha.finalize()))}
fn entries(manifest:&Value)->Result<Vec<(String,u64)>,String>{
 if manifest["schema"]!=1||manifest["minecraft"]!="26.1"||manifest["loader"]!="fabric"||manifest["loaderVersion"]!="0.19.5"{return Err("Unsupported shared environment".into())}
 let files=manifest["files"].as_array().ok_or("Invalid shared mod list")?;if files.len()>512{return Err("Too many shared mods".into())}
 let mut out=Vec::new();let mut ids=std::collections::HashSet::new();let mut hashes=std::collections::HashSet::new();let mut total=0;
 for e in files{let hash=e["hash"].as_str().ok_or("Missing mod checksum")?;let id=e["id"].as_str().ok_or("Missing mod id")?;let size=e["size"].as_u64().ok_or("Invalid mod size")?;if hash.len()!=64||!hash.bytes().all(|c|c.is_ascii_digit()||(b'a'..=b'f').contains(&c))||size==0||size>512*1024*1024||id.len()<2||id.len()>64||!id.bytes().all(|c|c.is_ascii_lowercase()||c.is_ascii_digit()||c==b'_'||c==b'-')||["unbox","fabric-api","sodium","lithium","ferritecore","entityculling","immediatelyfast","dynamic_fps","e4mc","freelook"].contains(&id)||!hashes.insert(hash)||!ids.insert(id){return Err("Invalid or duplicate shared mod".into())}total+=size;if total>4*1024*1024*1024u64{return Err("Shared environment exceeds 4 GB".into())}out.push((hash.to_owned(),size));}Ok(out)
}
/// Called only after the guest game exits cleanly. Source profile is never edited.
pub fn apply(rt:&Runtime,source:&Value,request:&Value)->Result<Value,String>{
 let host=request["hostId"].as_str().ok_or("Missing friend identity")?;let host_uuid=uuid::Uuid::parse_str(host).map_err(|_|"Invalid friend identity")?;
 let manifest=&request["manifest"];let files=entries(manifest)?;let cache=rt.root.join("shared-cache");
 // Validate every cached file before changing the active environment.
 for(hash,size)in &files{let p=cache.join(format!("{hash}.jar"));let meta=fs::symlink_metadata(&p).map_err(|_|"Shared mod download is incomplete")?;if !meta.is_file()||meta.file_type().is_symlink()||meta.len()!=*size||digest(&p)?!=*hash{return Err("Shared mod checksum failed; join again to retry".into())}
  let mut zip=zip::ZipArchive::new(fs::File::open(&p).map_err(|_|"Cannot read downloaded mod")?).map_err(|_|"Invalid downloaded JAR")?;let metadata=zip.by_name("fabric.mod.json").map_err(|_|"Downloaded mod is not for Fabric")?;if metadata.size()>262144{return Err("Mod metadata is too large".into())}let m:Value=serde_json::from_reader(metadata).map_err(|_|"Invalid mod metadata")?;let expected=manifest["files"].as_array().unwrap().iter().find(|e|e["hash"]==*hash).unwrap();if m["id"]!=expected["id"]||m["environment"]=="client"||m["environment"]=="server"{return Err("Downloaded mod metadata does not match the shared list".into())}
 }
 let id=uuid::Uuid::new_v3(&host_uuid,b"Unbox shared Fabric 26.1").to_string();let dir=profile_dir(rt,&id)?;let original=profile_dir(rt,source["id"].as_str().ok_or("Missing source profile")?)?;
 fs::create_dir_all(&dir).map_err(|e|e.to_string())?;let stage=dir.join(format!(".mods-{}",uuid::Uuid::new_v4()));fs::create_dir(&stage).map_err(|e|e.to_string())?;
 let result=(||->Result<(),String>{
  // Keep only personal client-side mods. Gameplay is exactly the host manifest.
  let personal=if dir.join("profile.json").exists(){&dir}else{&original};
  let managed=crate::managed_mod_names(personal);
  if let Ok(list)=fs::read_dir(personal.join("mods")){for e in list.flatten(){let p=e.path();if managed.contains(&e.file_name().to_string_lossy().into_owned()) {continue;}if client_only(&p){fs::copy(&p,stage.join(e.file_name())).map_err(|e|e.to_string())?;}}}
  for(hash,size)in &files{let name=format!("{hash}.jar");let old=dir.join("mods").join(&name);let dest=stage.join(&name);let reuse=fs::symlink_metadata(&old).is_ok_and(|m|m.is_file()&&m.len()==*size)&&digest(&old).is_ok_and(|h|h==*hash);if !reuse||fs::hard_link(&old,&dest).is_err(){fs::copy(cache.join(&name),&dest).map_err(|e|e.to_string())?;}}
  // Journal the directory swap so interruption never leaves an empty active environment.
  let mods=dir.join("mods");let backup=dir.join(".mods-previous");recover(&dir)?;
  if mods.exists(){fs::rename(&mods,&backup).map_err(|e|e.to_string())?;}
  if let Err(e)=fs::rename(&stage,&mods){if backup.exists(){let _=fs::rename(&backup,&mods);}return Err(e.to_string())}
  if backup.exists(){fs::remove_dir_all(backup).map_err(|e|e.to_string())?;}
  Ok(())})();if result.is_err(){let _=fs::remove_dir_all(&stage);}result?;
 if !dir.join("profile.json").exists(){for rel in ["options.txt","config/unbox.properties"]{let from=original.join(rel);let to=dir.join(rel);if from.is_file(){if let Some(p)=to.parent(){fs::create_dir_all(p).map_err(|e|e.to_string())?;}fs::copy(from,to).map_err(|e|e.to_string())?;}}}
 let profile=json!({"id":id,"name":format!("Friend · {}",&host[..8]),"version":"26.1","loader":"fabric","icon":"grass_block","sharedHost":host});
 atomic(&dir.join("profile.json"),&serde_json::to_vec(&profile).unwrap())?;atomic(&dir.join("unbox-shared-manifest.json"),&serde_json::to_vec(manifest).unwrap())?;
 Ok(profile)
}
fn client_only(p:&Path)->bool{( ||->Option<bool>{if !fs::symlink_metadata(p).ok()?.is_file(){return None}let mut zip=zip::ZipArchive::new(fs::File::open(p).ok()?).ok()?;let f=zip.by_name("fabric.mod.json").ok()?;if f.size()>262144{return None}let m:Value=serde_json::from_reader(f).ok()?;Some(m["environment"]=="client")})().unwrap_or(false)}
pub fn recover(dir:&Path)->Result<(),String>{let backup=dir.join(".mods-previous");let mods=dir.join("mods");if backup.exists(){if mods.exists(){fs::remove_dir_all(backup).map_err(|e|e.to_string())?}else{fs::rename(backup,mods).map_err(|e|e.to_string())?}}Ok(())}
pub async fn resume(rt:&Runtime,source:&Value)->Result<Option<Value>,String>{
 let dir=profile_dir(rt,source["id"].as_str().ok_or("Missing profile")?)?;let path=dir.join("unbox-sync-request.json");if !path.exists(){return Ok(None)}let bytes=fs::read(&path).map_err(|e|e.to_string())?;fs::remove_file(path).map_err(|e|e.to_string())?;if bytes.len()>200000{return Err("Shared manifest is too large".into())}let request:Value=serde_json::from_slice(&bytes).map_err(|_|"Invalid sync completion")?;
 let now=std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis()as u64;if request["at"].as_u64().is_none_or(|n|now.saturating_sub(n)>120000){return Err("Sync completion expired; join again".into())}
 let _guard=rt.auth.lock().await;let account=crate::auth::restore_account(rt,&Value::Null)?;let uuid=account["uuid"].as_str().unwrap_or("").replace('-',"");if request["accountUuid"].as_str().unwrap_or("").replace('-',"")!=uuid||uuid.is_empty(){return Err("Account changed during synchronization".into())}
 {let state=rt.social.lock().await;if !state.view["invites"].as_array().is_some_and(|list|list.iter().any(|i|i["id"]==request["inviteId"]&&i["sender"]==request["hostId"])){return Err("Invitation is no longer available. Downloaded mods remain cached.".into())}}
 let copy=rt.clone();let source=source.clone();let r=request.clone();let profile=tauri::async_runtime::spawn_blocking(move||apply(&copy,&source,&r)).await.map_err(|_|"Could not prepare shared mods")??;
 let mut saved:Value=serde_json::from_slice(&fs::read(rt.root.join("state.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;let profiles=saved["profiles"].as_array_mut().ok_or("Invalid profile list")?;if !profiles.iter().any(|p|p["id"]==profile["id"]){profiles.push(profile.clone());}saved["selected"]=profile["id"].clone();atomic(&rt.root.join("state.json"),&serde_json::to_vec(&saved).unwrap())?;
 let target=profile_dir(rt,profile["id"].as_str().unwrap())?;atomic(&target.join("unbox-rejoin.json"),&serde_json::to_vec(&json!({"id":request["inviteId"],"at":now})).unwrap())?;Ok(Some(profile))
}
#[cfg(test)]mod tests{
 use super::*;use std::io::Write;
 fn jar(p:&Path,id:&str,environment:&str){let mut z=zip::ZipWriter::new(fs::File::create(p).unwrap());z.start_file("fabric.mod.json",zip::write::SimpleFileOptions::default()).unwrap();write!(z,"{}",json!({"id":id,"environment":environment})).unwrap();z.finish().unwrap();}
 #[test]fn shared_environment_is_exact_cached_and_never_overwrites_source(){
  let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());let rt=Runtime{root:root.clone(),resources:root.clone(),job:std::sync::Arc::default(),auth:std::sync::Arc::default(),social:std::sync::Arc::default()};let source=json!({"id":uuid::Uuid::new_v4().to_string()});let dir=profile_dir(&rt,source["id"].as_str().unwrap()).unwrap();fs::create_dir_all(dir.join("mods")).unwrap();fs::create_dir_all(root.join("shared-cache")).unwrap();jar(&dir.join("mods/personal.jar"),"personal","client");jar(&dir.join("mods/old-gameplay.jar"),"old","*");fs::write(dir.join("options.txt"),"personal settings").unwrap();
  let input=root.join("fixture.jar");jar(&input,"example","*");let hash=digest(&input).unwrap();let size=fs::metadata(&input).unwrap().len();fs::copy(&input,root.join("shared-cache").join(format!("{hash}.jar"))).unwrap();let mut request=json!({"hostId":uuid::Uuid::new_v4().to_string(),"manifest":{"schema":1,"minecraft":"26.1","loader":"fabric","loaderVersion":"0.19.5","files":[{"hash":hash,"size":size,"id":"example"}]}});
  let p=apply(&rt,&source,&request).unwrap();let shared=profile_dir(&rt,p["id"].as_str().unwrap()).unwrap();assert!(shared.join(format!("mods/{hash}.jar")).exists());assert!(shared.join("mods/personal.jar").exists());assert!(!shared.join("mods/old-gameplay.jar").exists());assert!(dir.join("mods/old-gameplay.jar").exists());assert_eq!(fs::read_to_string(shared.join("options.txt")).unwrap(),"personal settings");
  fs::write(shared.join("options.txt"),"changed locally").unwrap();request["manifest"]["files"]=json!([]);assert_eq!(apply(&rt,&source,&request).unwrap()["id"],p["id"]);assert!(!shared.join(format!("mods/{hash}.jar")).exists());assert!(root.join(format!("shared-cache/{hash}.jar")).exists());assert_eq!(fs::read_to_string(shared.join("options.txt")).unwrap(),"changed locally");
  request["manifest"]["files"]=json!([{"hash":hash,"size":size,"id":"example"}]);fs::write(root.join(format!("shared-cache/{hash}.jar")),"corrupt").unwrap();assert!(apply(&rt,&source,&request).is_err());assert!(shared.join("mods/personal.jar").exists());
  request["manifest"]["files"]=json!([{"hash":"../escape","size":1,"id":"bad"}]);assert!(apply(&rt,&source,&request).is_err());assert!(shared.join("mods/personal.jar").exists());fs::rename(shared.join("mods"),shared.join(".mods-previous")).unwrap();recover(&shared).unwrap();assert!(shared.join("mods/personal.jar").exists());fs::remove_dir_all(root).unwrap();
 }
}
