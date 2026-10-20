use crate::{Runtime,atomic,profile_dir};
use serde_json::{json,Value};
use sha2::{Digest,Sha256};
use std::{fs,io::Read,path::Path};
fn digest(path:&Path)->Result<String,String>{let mut f=fs::File::open(path).map_err(|_|"Missing cached mod")?;let mut sha=Sha256::new();let mut b=[0u8;65536];loop{let n=f.read(&mut b).map_err(|_|"Cannot read cached mod")?;if n==0{break}sha.update(&b[..n]);}Ok(format!("{:x}",sha.finalize()))}
fn managed_id(id:&str)->bool{["unbox","fabric-api","sodium","iris","lithium","ferritecore","entityculling","immediatelyfast","dynamic_fps","e4mc","freelook","wick","itemphysiclite","iteminteractions","creativecore","inventoryprofilesnext","libipn","fabric-language-kotlin","klf","veinminer","veinminer_client","fallingtree","anvianslib","chiseledbookshelfvisualizer","cloth-config","cloth_config","colourscroller","easyshulkerboxes","effortlessbuilding","forgeconfigapiport","kotlinforforge","modmenu","mousetweaks","placeholder-api","puzzleslib","resourcify","spatial-gui","spatial_gui","status-effect-bars","status_effect_bars","yet_another_config_lib_v3"].contains(&id)}
fn kind(e:&Value)->&str{e["kind"].as_str().unwrap_or("mods")}
fn safe_pack(name:&str)->bool{!name.is_empty()&&name.len()<=180&&!name.starts_with('.')&&!name.contains(['/', '\\', '\r', '\n', ':', '='])&&name.to_ascii_lowercase().ends_with(".zip")}
fn entries(manifest:&Value)->Result<Vec<(String,u64)>,String>{
 if ![json!(1),json!(2)].contains(&manifest["schema"])||!crate::versions::supported(manifest["minecraft"].as_str().unwrap_or(""))||!((manifest["loader"]=="fabric"&&manifest["loaderVersion"]=="0.19.5")||(manifest["loader"]=="neoforge"&&crate::versions::neoforge(manifest["minecraft"].as_str().unwrap_or("")).is_some_and(|(version,_)|manifest["loaderVersion"]==version))){return Err("Unsupported shared environment".into())}
 let files=manifest["files"].as_array().ok_or("Invalid shared file list")?;if files.len()>512{return Err("Too many shared files".into())}
 let mut out=Vec::new();let mut names=std::collections::HashSet::new();let mut total=0;
 for e in files{let hash=e["hash"].as_str().ok_or("Missing checksum")?;let id=e["id"].as_str().ok_or("Missing file id")?;let size=e["size"].as_u64().ok_or("Invalid file size")?;let k=kind(e);let name=if k=="mods"{id}else{e["name"].as_str().ok_or("Missing pack name")?};
 if !["mods","resourcepacks","shaderpacks"].contains(&k)||hash.len()!=64||!hash.bytes().all(|c|c.is_ascii_digit()||(b'a'..=b'f').contains(&c))||size==0||size>512*1024*1024||id.len()<2||id.len()>64||!id.bytes().all(|c|c.is_ascii_lowercase()||c.is_ascii_digit()||c==b'_'||c==b'-')||managed_id(id)||k!="mods"&&!safe_pack(name)||!names.insert(format!("{k}:{name}")){return Err("Invalid or duplicate shared file".into())}total+=size;if total>4*1024*1024*1024u64{return Err("Shared environment exceeds 4 GB".into())}out.push((hash.to_owned(),size));}
 if manifest["schema"]==2{let selected=manifest["resourcePacks"].as_array().ok_or("Missing selected packs")?;if selected.len()>512{return Err("Too many selected packs".into())}for p in selected{let name=p.as_str().and_then(|s|s.strip_prefix("file/")).ok_or("Invalid selected pack")?;if !names.contains(&format!("resourcepacks:{name}")){return Err("Selected resource pack missing".into())}}let shader=manifest["shaderPack"].as_str().ok_or("Missing shader selection")?;if !shader.is_empty()&&!names.contains(&format!("shaderpacks:{shader}")){return Err("Selected shader pack missing".into())}if !manifest["shadersEnabled"].is_boolean(){return Err("Invalid shader state".into())}}
 Ok(out)
}
/// Keep a single visible recent environment per friend; version-specific files remain isolated on disk.
pub fn normalize_profiles(state:&mut Value){
 let Some(profiles)=state["profiles"].as_array() else{return};
 let mut latest=std::collections::HashMap::new();
 for (index,p) in profiles.iter().enumerate(){if let Some(host)=p["sharedHost"].as_str(){let stamp=p["sharedUpdatedAt"].as_u64().unwrap_or(0);let entry=latest.entry(host.to_owned()).or_insert((stamp,index));if stamp>=entry.0{*entry=(stamp,index);}}}
 let selected_host=profiles.iter().find(|p|p["id"]==state["selected"]).and_then(|p|p["sharedHost"].as_str()).map(str::to_owned);
 let filtered:Vec<Value>=profiles.iter().enumerate().filter(|(index,p)|p["sharedHost"].as_str().is_none_or(|host|latest[host].1==*index)).map(|(_,p)|p.clone()).collect();
 if let Some(host)=selected_host{if let Some(p)=filtered.iter().find(|p|p["sharedHost"]==host){state["selected"]=p["id"].clone();}}
 state["profiles"]=json!(filtered);
}
pub fn merge_saved_profiles(state:&mut Value,saved:&Value){
 // Background joining owns shared profiles. A stale UI settings write cannot restore an older environment.
 let previous_host=state["profiles"].as_array().and_then(|ps|ps.iter().find(|p|p["id"]==state["selected"])).and_then(|p|p["sharedHost"].as_str()).map(str::to_owned);
 let profiles=state["profiles"].as_array_mut().unwrap();profiles.retain(|p|!p["sharedHost"].is_string());
 if let Some(existing)=saved["profiles"].as_array(){profiles.extend(existing.iter().filter(|p|p["sharedHost"].is_string()).cloned());}
 normalize_profiles(state);
 if let Some(host)=previous_host{if let Some(p)=state["profiles"].as_array().unwrap().iter().find(|p|p["sharedHost"]==host){state["selected"]=p["id"].clone();}}
}
fn select_shared(state:&mut Value,profile:&Value)->Result<(),String>{
 let profiles=state["profiles"].as_array_mut().ok_or("Invalid profile list")?;
 profiles.retain(|p|p["id"]!=profile["id"]&&p["sharedHost"]!=profile["sharedHost"]);profiles.push(profile.clone());state["selected"]=profile["id"].clone();Ok(())
}
fn describe_friend(profile:&mut Value,view:&Value,invite:&Value,now:u64){
 let friend=view["friends"].as_array().and_then(|fs|fs.iter().find(|f|f["id"]==profile["sharedHost"]));
 let name=friend.and_then(|f|f["name"].as_str()).or_else(||invite["senderName"].as_str()).map(str::to_owned).or_else(||profile["sharedHostName"].as_str().map(str::to_owned)).unwrap_or_else(||profile["sharedHost"].as_str().unwrap_or("Friend").chars().take(8).collect());
 profile["name"]=json!(format!("Last played · {name}"));profile["sharedHostName"]=json!(name);profile["sharedUpdatedAt"]=json!(now);
 if let Some(friend)=friend{profile["sharedHostFace"]=friend["face"].clone();}
 if invite["room"]["name"].is_string(){profile["sharedWorld"]=invite["room"]["name"].clone();}
}
/// Called only after the guest game exits cleanly. Source profile is never edited.
pub fn apply(rt:&Runtime,source:&Value,request:&Value)->Result<Value,String>{
 let host=request["hostId"].as_str().ok_or("Missing friend identity")?;let host_uuid=uuid::Uuid::parse_str(host).map_err(|_|"Invalid friend identity")?;
 let manifest=&request["manifest"];let files=entries(manifest)?;let cache=rt.root.join("shared-cache");
 // Validate every cached file before changing the active environment.
 for(index,(hash,size))in files.iter().enumerate(){let p=cache.join(format!("{hash}.jar"));let meta=fs::symlink_metadata(&p).map_err(|_|"Shared mod download is incomplete")?;if !meta.is_file()||meta.file_type().is_symlink()||meta.len()!=*size||digest(&p)?!=*hash{return Err("Shared mod checksum failed; join again to retry".into())}
  let mut zip=zip::ZipArchive::new(fs::File::open(&p).map_err(|_|"Cannot read downloaded mod")?).map_err(|_|"Invalid downloaded JAR")?;let expected=&manifest["files"].as_array().unwrap()[index];
  if kind(expected)!="mods"{let names:Vec<String>=zip.file_names().map(str::to_owned).collect();if kind(expected)=="resourcepacks"&&!names.iter().any(|n|n=="pack.mcmeta")||kind(expected)=="shaderpacks"&&!names.iter().any(|n|n.starts_with("shaders/")){return Err("Invalid downloaded pack".into())}continue;}
  if manifest["loader"]=="neoforge"{let metadata=zip.by_name("META-INF/neoforge.mods.toml").map_err(|_|"Downloaded mod is not for NeoForge")?;if metadata.size()>262144{return Err("Mod metadata is too large".into())}let mut text=String::new();metadata.take(262145).read_to_string(&mut text).map_err(|_|"Invalid mod metadata")?;let m:toml::Value=toml::from_str(&text).map_err(|_|"Invalid NeoForge metadata")?;let mods=m.get("mods").and_then(|v|v.as_array()).ok_or("Missing NeoForge mod entries")?;
   if mods.is_empty()||mods.len()>64||mods[0].get("modId").and_then(|v|v.as_str())!=expected["id"].as_str()||mods.iter().any(|v|v.get("modId").and_then(|v|v.as_str()).is_some_and(managed_id))||m.get("clientSideOnly").and_then(|v|v.as_bool())==Some(true)||mods.iter().all(|v|v.get("clientSideOnly").and_then(|v|v.as_bool())==Some(true)){return Err("Downloaded mod metadata does not match the shared list".into())}
  }else{let metadata=zip.by_name("fabric.mod.json").map_err(|_|"Downloaded mod is not for Fabric")?;if metadata.size()>262144{return Err("Mod metadata is too large".into())}let m:Value=serde_json::from_reader(metadata).map_err(|_|"Invalid mod metadata")?;if m["id"]!=expected["id"]||m["environment"]=="client"||m["environment"]=="server"{return Err("Downloaded mod metadata does not match the shared list".into())}}

 }
 let loader=manifest["loader"].as_str().unwrap();let version=manifest["minecraft"].as_str().unwrap();let namespace=crate::versions::shared_namespace(loader,version);let id=uuid::Uuid::new_v3(&host_uuid,namespace.as_bytes()).to_string();let dir=profile_dir(rt,&id)?;let original=profile_dir(rt,source["id"].as_str().ok_or("Missing source profile")?)?;
 fs::create_dir_all(&dir).map_err(|e|e.to_string())?;let stage=dir.join(format!(".mods-{}",uuid::Uuid::new_v4()));fs::create_dir(&stage).map_err(|e|e.to_string())?;
 let result=(||->Result<(),String>{
  // Keep only personal client-side mods. Gameplay is exactly the host manifest.
  let personal=if dir.join("profile.json").exists(){&dir}else{&original};
  let managed=crate::managed_mod_names(personal);
  if personal==&dir||(source["loader"]==loader&&source["version"]==version){if let Ok(list)=fs::read_dir(personal.join("mods")){for e in list.flatten(){let p=e.path();if managed.contains(&e.file_name().to_string_lossy().into_owned()) {continue;}if client_only(&p,loader){fs::copy(&p,stage.join(e.file_name())).map_err(|e|e.to_string())?;}}}}
  for(hash,size)in &files{if !manifest["files"].as_array().unwrap().iter().any(|e|e["hash"]==*hash&&kind(e)=="mods"){continue}let name=format!("{hash}.jar");let old=dir.join("mods").join(&name);let dest=stage.join(&name);let reuse=fs::symlink_metadata(&old).is_ok_and(|m|m.is_file()&&m.len()==*size)&&digest(&old).is_ok_and(|h|h==*hash);if !reuse||fs::hard_link(&old,&dest).is_err(){fs::copy(cache.join(&name),&dest).map_err(|e|e.to_string())?;}}
  // Journal the directory swap so interruption never leaves an empty active environment.
  let mods=dir.join("mods");let backup=dir.join(".mods-previous");recover(&dir)?;
  if mods.exists(){fs::rename(&mods,&backup).map_err(|e|e.to_string())?;}
  if let Err(e)=fs::rename(&stage,&mods){if backup.exists(){let _=fs::rename(&backup,&mods);}return Err(e.to_string())}
  if backup.exists(){fs::remove_dir_all(backup).map_err(|e|e.to_string())?;}
  Ok(())})();if result.is_err(){let _=fs::remove_dir_all(&stage);}result?;
 if !dir.join("profile.json").exists(){for rel in ["options.txt","config/unbox.properties"]{let from=original.join(rel);let to=dir.join(rel);if from.is_file(){if let Some(p)=to.parent(){fs::create_dir_all(p).map_err(|e|e.to_string())?;}fs::copy(from,to).map_err(|e|e.to_string())?;}}}
 if manifest["schema"]==2{
  for folder in ["resourcepacks","shaderpacks"]{let stage=dir.join(format!(".{folder}-{}",uuid::Uuid::new_v4()));fs::create_dir(&stage).map_err(|e|e.to_string())?;
   let prepared=(||->Result<(),String>{for e in manifest["files"].as_array().unwrap().iter().filter(|e|kind(e)==folder){fs::copy(cache.join(format!("{}.jar",e["hash"].as_str().unwrap())),stage.join(e["name"].as_str().unwrap())).map_err(|e|e.to_string())?;}Ok(())})();if let Err(e)=prepared{let _=fs::remove_dir_all(&stage);return Err(e)}
   let target=dir.join(folder);let backup=dir.join(format!(".{folder}-previous"));if backup.exists(){return Err("Pending pack recovery; retry joining".into())}if target.exists(){fs::rename(&target,&backup).map_err(|e|e.to_string())?;}if let Err(e)=fs::rename(&stage,&target){if backup.exists(){let _=fs::rename(&backup,&target);}return Err(e.to_string())}if backup.exists(){fs::remove_dir_all(&backup).map_err(|e|e.to_string())?;}
  }
  let options=dir.join("options.txt");let previous=fs::read_to_string(&options).unwrap_or_default();let mut lines:Vec<String>=previous.lines().filter(|l|!l.starts_with("resourcePacks:")&&!l.starts_with("incompatibleResourcePacks:")).map(str::to_owned).collect();let mut selected=vec![json!("vanilla")];selected.extend(manifest["resourcePacks"].as_array().unwrap().clone());lines.push(format!("resourcePacks:{}",json!(selected)));lines.push("incompatibleResourcePacks:[]".into());atomic(&options,(lines.join("\n")+"\n").as_bytes())?;
  let iris=dir.join("config/iris.properties");let previous=fs::read_to_string(&iris).unwrap_or_default();let mut lines:Vec<String>=previous.lines().filter(|l|!l.starts_with("shaderPack=")&&!l.starts_with("enableShaders=")).map(str::to_owned).collect();lines.push(format!("shaderPack={}",manifest["shaderPack"].as_str().unwrap()));lines.push(format!("enableShaders={}",manifest["shadersEnabled"].as_bool().unwrap()));atomic(&iris,(lines.join("\n")+"\n").as_bytes())?;
 }
 let mut profile:Value=fs::read(dir.join("profile.json")).ok().and_then(|b|serde_json::from_slice(&b).ok()).filter(Value::is_object).unwrap_or_else(||json!({"name":format!("Friend · {}",&host[..8]),"icon":"grass_block"}));
 profile["id"]=json!(id);profile["version"]=json!(version);profile["loader"]=json!(loader);profile["sharedHost"]=json!(host);
 atomic(&dir.join("profile.json"),&serde_json::to_vec(&profile).unwrap())?;atomic(&dir.join("unbox-shared-manifest.json"),&serde_json::to_vec(manifest).unwrap())?;
 Ok(profile)
}
fn client_only(p:&Path,loader:&str)->bool{( ||->Option<bool>{if !fs::symlink_metadata(p).ok()?.is_file(){return None}let mut zip=zip::ZipArchive::new(fs::File::open(p).ok()?).ok()?;if loader=="neoforge"{let mut f=zip.by_name("META-INF/neoforge.mods.toml").ok()?;if f.size()>262144{return None}let mut text=String::new();f.read_to_string(&mut text).ok()?;let m:toml::Value=toml::from_str(&text).ok()?;return Some(m.get("clientSideOnly").and_then(|v|v.as_bool())==Some(true)||m.get("mods")?.as_array()?.iter().all(|v|v.get("clientSideOnly").and_then(|v|v.as_bool())==Some(true)))}let f=zip.by_name("fabric.mod.json").ok()?;if f.size()>262144{return None}let m:Value=serde_json::from_reader(f).ok()?;Some(m["environment"]=="client")})().unwrap_or(false)}
pub fn recover(dir:&Path)->Result<(),String>{for folder in ["mods","resourcepacks","shaderpacks"]{let backup=dir.join(format!(".{folder}-previous"));let target=dir.join(folder);if backup.exists(){if target.exists(){fs::remove_dir_all(backup).map_err(|e|e.to_string())?}else{fs::rename(backup,target).map_err(|e|e.to_string())?}}}Ok(())}
/// Select an isolated native-loader profile before a game exists. Never use a personal gameplay folder.
pub fn invite_profile(rt:&Runtime,view:&Value,invite_id:&str,account_uuid:&str)->Result<Value,String>{
 let now=std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis()as u64;
 if view["connected"]!=true{return Err("Friends are offline. Retry when connected.".into())}
 let invite=view["invites"].as_array().and_then(|list|list.iter().find(|i|i["id"]==invite_id&&i["receiver"]==view["self"]["id"]&&i["expires"].as_u64().is_some_and(|n|n>now))).ok_or("Invitation expired; ask your friend to invite again")?;
 let host=invite["sender"].as_str().ok_or("Missing host")?;let host_uuid=uuid::Uuid::parse_str(host).map_err(|_|"Invalid host")?;
 let loader=invite["room"]["loader"].as_str().ok_or("Ask your friend to update Unbox and send a new invitation")?;
 if !["fabric","neoforge"].contains(&loader)||!crate::versions::supported(invite["room"]["minecraft"].as_str().unwrap_or("")){return Err("Unsupported host game version or loader".into())}
 let version=invite["room"]["minecraft"].as_str().unwrap();let namespace=crate::versions::shared_namespace(loader,version);
 let id=uuid::Uuid::new_v3(&host_uuid,namespace.as_bytes()).to_string();let dir=profile_dir(rt,&id)?;
 let mut saved:Value=serde_json::from_slice(&fs::read(rt.root.join("state.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
 let mut profile=if dir.join("profile.json").is_file(){serde_json::from_slice(&fs::read(dir.join("profile.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?}else{
  let p=json!({"id":id,"name":format!("Friend · {}",invite["senderName"].as_str().unwrap_or(&host[..8])),"version":version,"loader":loader,"icon":"grass_block","sharedHost":host});
  atomic(&dir.join("profile.json"),&serde_json::to_vec(&p).unwrap())?;
  if let Some(source)=saved["selected"].as_str().and_then(|s|profile_dir(rt,s).ok()){for rel in ["options.txt","config/unbox.properties"]{if source.join(rel).is_file(){atomic(&dir.join(rel),&fs::read(source.join(rel)).map_err(|e|e.to_string())?)?;}}}p
 };
 describe_friend(&mut profile,view,invite,now);atomic(&dir.join("profile.json"),&serde_json::to_vec(&profile).unwrap())?;select_shared(&mut saved,&profile)?;
 atomic(&rt.root.join("state.json"),&serde_json::to_vec(&saved).unwrap())?;
 atomic(&dir.join("unbox-rejoin.json"),&serde_json::to_vec(&json!({"id":invite_id,"at":now,"expires":invite["expires"],"accountUuid":account_uuid,"worldName":invite["room"]["name"]})).unwrap())?;
 let mut bridge=view.clone();bridge["updated"]=json!(now);bridge["seenInvites"]=json!([invite_id]);crate::social::atomic(&dir.join("unbox-social-view.json"),&serde_json::to_vec(&bridge).unwrap())?;Ok(profile)
}
pub async fn resume(rt:&Runtime,source:&Value)->Result<Option<Value>,String>{
 let dir=profile_dir(rt,source["id"].as_str().ok_or("Missing profile")?)?;
 let switch=dir.join("unbox-switch-request.json");if switch.exists(){let bytes=fs::read(&switch).map_err(|e|e.to_string())?;fs::remove_file(switch).map_err(|e|e.to_string())?;if bytes.len()>4096{return Err("Invalid invitation switch".into())}let r:Value=serde_json::from_slice(&bytes).map_err(|_|"Invalid invitation switch")?;let _guard=rt.auth.lock().await;let a=crate::auth::restore_account(rt,&Value::Null)?;let uuid=a["uuid"].as_str().unwrap_or("").replace('-',"");if uuid.is_empty()||r["accountUuid"].as_str().unwrap_or("").replace('-',"")!=uuid{return Err("Account changed before joining".into())}let view=rt.social.lock().await.view.clone();return invite_profile(rt,&view,r["id"].as_str().unwrap_or(""),&uuid).map(Some)}
 let path=dir.join("unbox-sync-request.json");if !path.exists(){return Ok(None)}let bytes=fs::read(&path).map_err(|e|e.to_string())?;fs::remove_file(path).map_err(|e|e.to_string())?;if bytes.len()>200000{return Err("Shared manifest is too large".into())}let request:Value=serde_json::from_slice(&bytes).map_err(|_|"Invalid sync completion")?;
 let now=std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap_or_default().as_millis()as u64;if request["at"].as_u64().is_none_or(|n|now.saturating_sub(n)>120000){return Err("Sync completion expired; join again".into())}
 let _guard=rt.auth.lock().await;let account=crate::auth::restore_account(rt,&Value::Null)?;let uuid=account["uuid"].as_str().unwrap_or("").replace('-',"");if request["accountUuid"].as_str().unwrap_or("").replace('-',"")!=uuid||uuid.is_empty(){return Err("Account changed during synchronization".into())}
 let view=rt.social.lock().await.view.clone();let invite=view["invites"].as_array().and_then(|list|list.iter().find(|i|i["id"]==request["inviteId"]&&i["sender"]==request["hostId"])).ok_or("Invitation is no longer available. Downloaded mods remain cached.")?;
 let copy=rt.clone();let source=source.clone();let r=request.clone();let mut profile=tauri::async_runtime::spawn_blocking(move||apply(&copy,&source,&r)).await.map_err(|_|"Could not prepare shared mods")??;
 let mut saved:Value=serde_json::from_slice(&fs::read(rt.root.join("state.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;describe_friend(&mut profile,&view,invite,now);atomic(&profile_dir(rt,profile["id"].as_str().unwrap())?.join("profile.json"),&serde_json::to_vec(&profile).unwrap())?;select_shared(&mut saved,&profile)?;atomic(&rt.root.join("state.json"),&serde_json::to_vec(&saved).unwrap())?;
 let target=profile_dir(rt,profile["id"].as_str().unwrap())?;atomic(&target.join("unbox-rejoin.json"),&serde_json::to_vec(&json!({"id":request["inviteId"],"at":now,"accountUuid":uuid})).unwrap())?;Ok(Some(profile))
}
#[cfg(test)]mod tests{
 use super::*;use std::io::Write;
 fn jar(p:&Path,id:&str,environment:&str){let mut z=zip::ZipWriter::new(fs::File::create(p).unwrap());z.start_file("fabric.mod.json",zip::write::SimpleFileOptions::default()).unwrap();write!(z,"{}",json!({"id":id,"environment":environment})).unwrap();z.finish().unwrap();}
 #[test]fn recent_friend_profiles_replace_by_host_and_survive_stale_ui_saves(){
  let personal=json!({"id":"personal","name":"My world"});
  let old=json!({"id":"old","sharedHost":"alice","sharedUpdatedAt":10,"sharedHostFace":"old-face"});
  let latest=json!({"id":"new","sharedHost":"alice","sharedUpdatedAt":20,"sharedHostFace":"new-face","version":"26.3","loader":"neoforge"});
  let bob=json!({"id":"bob","sharedHost":"bob","sharedUpdatedAt":15});
  let mut saved=json!({"profiles":[personal,latest,bob,old],"selected":"old"});normalize_profiles(&mut saved);
  assert_eq!(saved["profiles"].as_array().unwrap().len(),3);assert_eq!(saved["selected"],"new");assert_eq!(saved["profiles"][0],personal);
  let mut stale=json!({"profiles":[personal,old],"selected":"old","settings":{"memory":8192}});merge_saved_profiles(&mut stale,&saved);
  assert_eq!(stale["profiles"],saved["profiles"]);assert_eq!(stale["selected"],"new");assert_eq!(stale["settings"]["memory"],8192);
  let updated=json!({"id":"new","sharedHost":"alice","sharedUpdatedAt":30,"sharedHostFace":"updated-face"});select_shared(&mut saved,&updated).unwrap();
  assert_eq!(saved["profiles"].as_array().unwrap().len(),3);assert_eq!(saved["profiles"][2],updated);
  let mut legacy=json!({"profiles":[{"id":"one","sharedHost":"a"},{"id":"two","sharedHost":"a"}],"selected":"one"});normalize_profiles(&mut legacy);assert_eq!(legacy["profiles"].as_array().unwrap().len(),1);assert_eq!(legacy["selected"],"two");
 }
 #[test]fn invitations_and_cached_mods_select_each_exact_native_environment(){
  let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());let rt=Runtime{root:root.clone(),resources:root.clone(),job:Default::default(),auth:Default::default(),social:Default::default()};
  let source=json!({"id":uuid::Uuid::new_v4().to_string(),"loader":"fabric","version":"26.1"});let original=profile_dir(&rt,source["id"].as_str().unwrap()).unwrap();fs::create_dir_all(original.join("mods")).unwrap();fs::create_dir_all(root.join("shared-cache")).unwrap();jar(&original.join("mods/personal.jar"),"personal","client");atomic(&original.join("options.txt"),b"key_key.forward:key.keyboard.i\n").unwrap();
  atomic(&original.join("config/unbox.properties"),b"zoom.key=key.keyboard.r\nminimap.key=key.keyboard.m\nfreecam.key=key.mouse.4\nfreelook.key=341\n").unwrap();
  atomic(&root.join("state.json"),&serde_json::to_vec(&json!({"profiles":[source],"selected":source["id"]})).unwrap()).unwrap();
  let host=uuid::Uuid::new_v4().to_string();let now=std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis()as u64;let mut ids=std::collections::HashSet::new();
  for loader in ["fabric","neoforge"]{
   let input=root.join(format!("{loader}.jar"));if loader=="fabric"{jar(&input,"example","*")}else{let mut z=zip::ZipWriter::new(fs::File::create(&input).unwrap());z.start_file("META-INF/neoforge.mods.toml",zip::write::SimpleFileOptions::default()).unwrap();z.write_all(b"modLoader='javafml'\n[[mods]]\nmodId='example'\nversion='1.0'\n").unwrap();z.finish().unwrap();}
   let hash=digest(&input).unwrap();let size=fs::metadata(&input).unwrap().len();fs::copy(&input,root.join(format!("shared-cache/{hash}.jar"))).unwrap();
   for version in crate::versions::SUPPORTED{
    let loader_version=if loader=="fabric"{"0.19.5"}else{crate::versions::neoforge(version).unwrap().0};
    let mut request=json!({"hostId":host,"manifest":{"schema":1,"minecraft":version,"loader":loader,"loaderVersion":loader_version,"files":[{"hash":hash,"size":size,"id":"example"}]}});
    let profile=apply(&rt,&source,&request).unwrap();assert_eq!(profile["loader"],loader);assert_eq!(profile["version"],*version);assert!(ids.insert(profile["id"].as_str().unwrap().to_owned()));
    let target=profile_dir(&rt,profile["id"].as_str().unwrap()).unwrap();assert!(target.join(format!("mods/{hash}.jar")).exists());assert_eq!(target.join("mods/personal.jar").exists(),loader=="fabric"&&*version=="26.1");assert_eq!(fs::read_to_string(target.join("options.txt")).unwrap(),"key_key.forward:key.keyboard.i\n");assert_eq!(apply(&rt,&source,&request).unwrap(),profile);
    assert_eq!(fs::read(target.join("config/unbox.properties")).unwrap(),fs::read(original.join("config/unbox.properties")).unwrap());
    let view=json!({"connected":true,"self":{"id":"guest"},"invites":[{"id":"invite","sender":host,"receiver":"guest","expires":now+300000,"room":{"loader":loader,"minecraft":version}}]});assert_eq!(invite_profile(&rt,&view,"invite","account").unwrap()["id"],profile["id"]);
    request["manifest"]["loaderVersion"]=json!("unsupported");assert!(apply(&rt,&source,&request).is_err());assert!(target.join(format!("mods/{hash}.jar")).exists());
   }
  }
  assert_eq!(ids.len(),6);assert!(original.join("mods/personal.jar").exists());fs::remove_dir_all(root).unwrap();
 }
 #[test]fn packs_sync_selection_and_removal_without_personal_settings_loss(){
  let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());let rt=Runtime{root:root.clone(),resources:root.clone(),job:Default::default(),auth:Default::default(),social:Default::default()};let source=json!({"id":uuid::Uuid::new_v4().to_string(),"loader":"fabric","version":"26.1"});let dir=profile_dir(&rt,source["id"].as_str().unwrap()).unwrap();fs::create_dir_all(dir.join("mods")).unwrap();fs::create_dir_all(root.join("shared-cache")).unwrap();fs::write(dir.join("options.txt"),"key_key.forward:key.keyboard.i\nresourcePacks:[]\n").unwrap();
  let mut files=Vec::new();for(folder,name,entry)in [("resourcepacks","Host.zip","pack.mcmeta"),("shaderpacks","Light.zip","shaders/final.fsh")]{let path=root.join(name);let mut z=zip::ZipWriter::new(fs::File::create(&path).unwrap());z.start_file(entry,zip::write::SimpleFileOptions::default()).unwrap();z.write_all(b"fixture").unwrap();z.finish().unwrap();let hash=digest(&path).unwrap();fs::copy(&path,root.join(format!("shared-cache/{hash}.jar"))).unwrap();files.push(json!({"hash":hash,"size":fs::metadata(path).unwrap().len(),"id":format!("pack_{}",&hash[..24]),"name":name,"kind":folder}));}
  let mut r=json!({"hostId":uuid::Uuid::new_v4().to_string(),"manifest":{"schema":2,"minecraft":"26.1.2","loader":"fabric","loaderVersion":"0.19.5","files":files,"resourcePacks":["file/Host.zip"],"shaderPack":"Light.zip","shadersEnabled":true}});
  let p=apply(&rt,&source,&r).unwrap();assert_eq!(p["version"],"26.1.2");let target=profile_dir(&rt,p["id"].as_str().unwrap()).unwrap();assert!(target.join("resourcepacks/Host.zip").is_file());assert!(target.join("shaderpacks/Light.zip").is_file());let opts=fs::read_to_string(target.join("options.txt")).unwrap();assert!(opts.contains("key.keyboard.i"));assert!(opts.contains("file/Host.zip"));assert!(fs::read_to_string(target.join("config/iris.properties")).unwrap().contains("enableShaders=true"));assert!(!dir.join("resourcepacks/Host.zip").exists());assert_eq!(apply(&rt,&source,&r).unwrap(),p);
  r["manifest"]["files"][0]["name"]=json!("../escape.zip");assert!(apply(&rt,&source,&r).is_err());assert!(target.join("resourcepacks/Host.zip").exists());
  r["manifest"]["files"]=json!([]);r["manifest"]["resourcePacks"]=json!([]);r["manifest"]["shaderPack"]=json!("");r["manifest"]["shadersEnabled"]=json!(false);apply(&rt,&source,&r).unwrap();assert!(!target.join("resourcepacks/Host.zip").exists());assert!(!target.join("shaderpacks/Light.zip").exists());assert!(fs::read_to_string(target.join("options.txt")).unwrap().contains("key.keyboard.i"));fs::remove_dir_all(root).unwrap();
 }
 #[test]fn cold_invites_choose_native_isolated_profiles_and_reject_stale_or_foreign_invites(){
 let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());let rt=Runtime{root:root.clone(),resources:root.clone(),job:Default::default(),auth:Default::default(),social:Default::default()};fs::create_dir_all(&root).unwrap();atomic(&root.join("state.json"),br#"{"profiles":[],"selected":null}"#).unwrap();
 let now=std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis()as u64;
 let mut view=json!({"connected":true,"self":{"id":"guest"},"invites":[{"id":"invite","sender":uuid::Uuid::new_v4().to_string(),"receiver":"guest","senderName":"Host","expires":now+300000,"room":{"loader":"neoforge","minecraft":"26.1"}}]});
 let neo=invite_profile(&rt,&view,"invite","account").unwrap();assert_eq!(neo["loader"],"neoforge");let dir=profile_dir(&rt,neo["id"].as_str().unwrap()).unwrap();fs::create_dir_all(dir.join("mods")).unwrap();fs::write(dir.join("mods/keep.jar"),"cached").unwrap();assert_eq!(invite_profile(&rt,&view,"invite","account").unwrap()["id"],neo["id"]);assert!(dir.join("mods/keep.jar").exists());
 let pending:Value=serde_json::from_slice(&fs::read(dir.join("unbox-rejoin.json")).unwrap()).unwrap();assert_eq!(pending["accountUuid"],"account");
 #[cfg(unix)] {use std::os::unix::fs::PermissionsExt;assert_eq!(fs::metadata(dir.join("unbox-social-view.json")).unwrap().permissions().mode()&0o777,0o600);}

 view["invites"][0]["room"]["loader"]=json!("fabric");let fabric=invite_profile(&rt,&view,"invite","account").unwrap();assert_ne!(fabric["id"],neo["id"]);
 let mut ids=std::collections::HashSet::new();for loader in ["fabric","neoforge"]{for version in crate::versions::SUPPORTED{view["invites"][0]["room"]=json!({"loader":loader,"minecraft":version});let profile=invite_profile(&rt,&view,"invite","account").unwrap();assert_eq!(profile["loader"],loader);assert_eq!(profile["version"],*version);assert!(ids.insert(profile["id"].as_str().unwrap().to_owned()));}}assert_eq!(ids.len(),6);
 let host=view["invites"][0]["sender"].clone();let saved:Value=serde_json::from_slice(&fs::read(root.join("state.json")).unwrap()).unwrap();assert_eq!(saved["profiles"].as_array().unwrap().iter().filter(|p|p["sharedHost"]==host).count(),1);
 view["friends"]=json!([{"id":host,"name":"Builder","face":"data:image/png;base64,dGVzdA=="}]);view["invites"][0]["room"]["name"]=json!("New world");
 let latest=invite_profile(&rt,&view,"invite","account").unwrap();assert_eq!(latest["name"],"Last played · Builder");assert_eq!(latest["sharedHostFace"],view["friends"][0]["face"]);assert_eq!(latest["sharedWorld"],"New world");assert!(dir.join("mods/keep.jar").exists());
 view["friends"][0]["name"]=json!("Renamed");let refreshed=invite_profile(&rt,&view,"invite","account").unwrap();assert_eq!(refreshed["id"],latest["id"]);assert_eq!(refreshed["name"],"Last played · Renamed");
 view["invites"][0]["receiver"]=json!("other");assert!(invite_profile(&rt,&view,"invite","account").is_err());view["invites"][0]["receiver"]=json!("guest");view["invites"][0]["expires"]=json!(0);assert!(invite_profile(&rt,&view,"invite","account").is_err());fs::remove_dir_all(root).unwrap();
 }
 #[test]fn neoforge_sync_preserves_loader_and_rejects_wrong_metadata(){
  let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());let rt=Runtime{root:root.clone(),resources:root.clone(),job:std::sync::Arc::default(),auth:std::sync::Arc::default(),social:std::sync::Arc::default()};
  let source=json!({"id":uuid::Uuid::new_v4().to_string(),"loader":"neoforge"});fs::create_dir_all(profile_dir(&rt,source["id"].as_str().unwrap()).unwrap().join("mods")).unwrap();fs::create_dir_all(root.join("shared-cache")).unwrap();
  let jar=root.join("neo.jar");let mut z=zip::ZipWriter::new(fs::File::create(&jar).unwrap());z.start_file("META-INF/neoforge.mods.toml",zip::write::SimpleFileOptions::default()).unwrap();write!(z,"modLoader='javafml'\n[[mods]]\nmodId='example'\nversion='1.0'\n").unwrap();z.finish().unwrap();
  let hash=digest(&jar).unwrap();let size=fs::metadata(&jar).unwrap().len();fs::copy(&jar,root.join(format!("shared-cache/{hash}.jar"))).unwrap();
  let mut request=json!({"hostId":uuid::Uuid::new_v4().to_string(),"manifest":{"schema":1,"minecraft":"26.1","loader":"neoforge","loaderVersion":"26.1.0.19-beta","files":[{"hash":hash,"size":size,"id":"example"}]}});
  let profile=apply(&rt,&source,&request).unwrap();assert_eq!(profile["loader"],"neoforge");assert_eq!(apply(&rt,&source,&request).unwrap()["id"],profile["id"]);
  request["manifest"]["loader"]=json!("fabric");request["manifest"]["loaderVersion"]=json!("0.19.5");assert!(apply(&rt,&source,&request).is_err());request["manifest"]["files"]=json!([]);assert_ne!(apply(&rt,&source,&request).unwrap()["id"],profile["id"]);
  fs::remove_dir_all(root).unwrap();
 }
 #[test]fn shared_environment_is_exact_cached_and_never_overwrites_source(){
  let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());let rt=Runtime{root:root.clone(),resources:root.clone(),job:std::sync::Arc::default(),auth:std::sync::Arc::default(),social:std::sync::Arc::default()};let source=json!({"id":uuid::Uuid::new_v4().to_string(),"loader":"fabric","version":"26.1"});let dir=profile_dir(&rt,source["id"].as_str().unwrap()).unwrap();fs::create_dir_all(dir.join("mods")).unwrap();fs::create_dir_all(root.join("shared-cache")).unwrap();jar(&dir.join("mods/personal.jar"),"personal","client");jar(&dir.join("mods/old-gameplay.jar"),"old","*");fs::write(dir.join("options.txt"),"personal settings").unwrap();
  let input=root.join("fixture.jar");jar(&input,"example","*");let hash=digest(&input).unwrap();let size=fs::metadata(&input).unwrap().len();fs::copy(&input,root.join("shared-cache").join(format!("{hash}.jar"))).unwrap();let mut request=json!({"hostId":uuid::Uuid::new_v4().to_string(),"manifest":{"schema":1,"minecraft":"26.1","loader":"fabric","loaderVersion":"0.19.5","files":[{"hash":hash,"size":size,"id":"example"}]}});
  let p=apply(&rt,&source,&request).unwrap();let shared=profile_dir(&rt,p["id"].as_str().unwrap()).unwrap();assert!(shared.join(format!("mods/{hash}.jar")).exists());assert!(shared.join("mods/personal.jar").exists());assert!(!shared.join("mods/old-gameplay.jar").exists());assert!(dir.join("mods/old-gameplay.jar").exists());assert_eq!(fs::read_to_string(shared.join("options.txt")).unwrap(),"personal settings");
  fs::write(shared.join("options.txt"),"changed locally").unwrap();request["manifest"]["files"]=json!([]);assert_eq!(apply(&rt,&source,&request).unwrap()["id"],p["id"]);assert!(!shared.join(format!("mods/{hash}.jar")).exists());assert!(root.join(format!("shared-cache/{hash}.jar")).exists());assert_eq!(fs::read_to_string(shared.join("options.txt")).unwrap(),"changed locally");
  request["manifest"]["files"]=json!([{"hash":hash,"size":size,"id":"example"}]);fs::write(root.join(format!("shared-cache/{hash}.jar")),"corrupt").unwrap();assert!(apply(&rt,&source,&request).is_err());assert!(shared.join("mods/personal.jar").exists());
  request["manifest"]["files"]=json!([{"hash":"../escape","size":1,"id":"bad"}]);assert!(apply(&rt,&source,&request).is_err());assert!(shared.join("mods/personal.jar").exists());fs::rename(shared.join("mods"),shared.join(".mods-previous")).unwrap();recover(&shared).unwrap();assert!(shared.join("mods/personal.jar").exists());fs::remove_dir_all(root).unwrap();
 }
}
