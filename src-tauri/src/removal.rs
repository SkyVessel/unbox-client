//! Reversible removals. All bytes, including worlds, stay in the local recovery folder.
use crate::{atomic,profile_dir,Runtime};
use serde_json::{json,Value};
use std::{fs,path::{Path,PathBuf}};
fn state(rt:&Runtime)->Result<Value,String>{serde_json::from_slice(&fs::read(rt.root.join("state.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())}
fn save(rt:&Runtime,v:&Value)->Result<(),String>{atomic(&rt.root.join("state.json"),&serde_json::to_vec(v).unwrap())}
fn entry(dir:&Path,name:&str)->Result<PathBuf,String>{
 if name.is_empty()||name=="."||name==".."||name.contains(['/', '\\', ':']){return Err("Invalid file name".into())}
 let p=dir.join(name);let m=fs::symlink_metadata(&p).map_err(|_|"File not found")?;
 if !m.is_file()||m.file_type().is_symlink(){return Err("Only regular files can be removed".into())}Ok(p)
}
fn available<'a>(rt:&'a Runtime,id:&str)->Result<std::sync::MutexGuard<'a,Value>,String>{let job=rt.job.lock().unwrap();if job["busy"]==true&&job["profileId"]==id{return Err("Close this profile’s game before removing files".into())}Ok(job)}
fn archive(rt:&Runtime,source:&Path,record:Value)->Result<String,String>{
 let token=uuid::Uuid::new_v4().to_string();let target=rt.root.join("removed").join(&token);fs::create_dir_all(&target).map_err(|e|e.to_string())?;
 atomic(&target.join("record.json"),&serde_json::to_vec(&record).unwrap())?;
 fs::rename(source,target.join("data")).map_err(|e|e.to_string())?;Ok(token)
}
pub fn remove_file(rt:&Runtime,id:&str,kind:&str,name:&str)->Result<Value,String>{
 let _job=available(rt,id)?;let dir=profile_dir(rt,id)?;let folder=crate::content::folder(kind)?;
 if !dir.join("profile.json").is_file(){return Err("Profile not found".into())}
 if kind=="Mods"&&crate::managed_mod_names(&dir).contains(name){return Err("Built-in client modules are managed by Unbox".into())}
 if fs::symlink_metadata(dir.join(folder)).map_err(|_|"Content folder not found")?.file_type().is_symlink(){return Err("Linked content folders cannot be modified".into())}
 let source=entry(&dir.join(folder),name)?;let token=archive(rt,&source,json!({"id":id,"kind":kind,"name":name}))?;Ok(json!({"token":token}))
}
#[tauri::command]
pub fn remove_content(rt:tauri::State<Runtime>,id:String,kind:String,name:String)->Result<Value,String>{remove_file(&rt,&id,&kind,&name)}
pub fn remove_profile_inner(rt:&Runtime,id:&str)->Result<Value,String>{
 let _job=available(rt,id)?;let dir=profile_dir(rt,id)?;if fs::symlink_metadata(&dir).map_err(|_|"Profile not found")?.file_type().is_symlink(){return Err("Linked profiles cannot be removed".into())}
 let mut v=state(rt)?;let p=v["profiles"].as_array().and_then(|ps|ps.iter().find(|p|p["id"]==id)).cloned().ok_or("Profile not found")?;
 let token=archive(rt,&dir,json!({"id":id,"profile":p,"modules":v["modules"][id]}))?;
 v["profiles"].as_array_mut().unwrap().retain(|p|p["id"]!=id);if v["selected"]==id{v["selected"]=v["profiles"].get(0).map(|p|p["id"].clone()).unwrap_or(Value::Null)}
 if let Some(m)=v["modules"].as_object_mut(){m.remove(id);}
 if let Err(e)=save(rt,&v){let _=fs::rename(rt.root.join("removed").join(&token).join("data"),&dir);return Err(e)}Ok(json!({"token":token}))
}
#[tauri::command]
pub async fn remove_profile(rt:tauri::State<'_,Runtime>,id:String)->Result<Value,String>{let _guard=rt.auth.lock().await;remove_profile_inner(&rt,&id)}
pub fn restore_inner(rt:&Runtime,token:&str)->Result<(),String>{
 uuid::Uuid::parse_str(token).map_err(|_|"Invalid recovery token")?;let dir=rt.root.join("removed").join(token);
 let r:Value=serde_json::from_slice(&fs::read(dir.join("record.json")).map_err(|_|"Removed item not found")?).map_err(|_|"Invalid recovery record")?;
 let id=r["id"].as_str().ok_or("Missing profile")?;let _job=available(rt,id)?;let profile=profile_dir(rt,id)?;
 let target=if r["profile"].is_object(){profile}else{if !profile.join("profile.json").is_file(){return Err("Restore the profile first".into())}let name=r["name"].as_str().ok_or("Missing file name")?;if name.contains(['/', '\\', ':'])||name=="."||name==".."{return Err("Invalid recovery record".into())}profile.join(crate::content::folder(r["kind"].as_str().unwrap_or(""))?).join(name)};
 if target.exists(){return Err("An item already exists at this location; it has not been replaced".into())}
 fs::create_dir_all(target.parent().unwrap()).map_err(|e|e.to_string())?;fs::rename(dir.join("data"),&target).map_err(|e|e.to_string())?;
 if r["profile"].is_object(){let result=(||{let mut v=state(rt)?;let ps=v["profiles"].as_array_mut().ok_or("Invalid profile list")?;if !ps.iter().any(|p|p["id"]==id){ps.push(r["profile"].clone())}if v["selected"].is_null(){v["selected"]=json!(id)}if r["modules"].is_object(){if !v["modules"].is_object(){v["modules"]=json!({})}v["modules"][id]=r["modules"].clone()}save(rt,&v)})();if let Err(e)=result{let _=fs::rename(&target,dir.join("data"));return Err(e)}}
 let _=fs::remove_file(dir.join("record.json"));let _=fs::remove_dir(dir);Ok(())
}
#[tauri::command]
pub async fn restore_removed(rt:tauri::State<'_,Runtime>,token:String)->Result<(),String>{let _guard=rt.auth.lock().await;restore_inner(&rt,&token)}
#[cfg(test)]mod tests{
 use super::*;
 #[test]fn removals_are_reversible_and_preserve_worlds(){
 let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());let rt=Runtime{root:root.clone(),resources:root.clone(),job:Default::default(),auth:Default::default(),social:Default::default()};let id=uuid::Uuid::new_v4().to_string();let dir=profile_dir(&rt,&id).unwrap();fs::create_dir_all(dir.join("mods")).unwrap();fs::create_dir_all(dir.join("saves/World")).unwrap();fs::write(dir.join("saves/World/level.dat"),"world").unwrap();fs::write(dir.join("profile.json"),"{}").unwrap();fs::write(dir.join("mods/test.jar"),"jar").unwrap();save(&rt,&json!({"profiles":[{"id":id}],"selected":id})).unwrap();
 assert!(remove_file(&rt,&id,"Mods","../profile.json").is_err());assert!(remove_file(&rt,&id,"Mods","unbox-client.jar").is_err());
 *rt.job.lock().unwrap()=json!({"busy":true,"profileId":id});assert!(remove_profile_inner(&rt,&id).is_err());*rt.job.lock().unwrap()=json!({});
 let token=remove_file(&rt,&id,"Mods","test.jar").unwrap()["token"].as_str().unwrap().to_owned();assert!(!dir.join("mods/test.jar").exists());restore_inner(&rt,&token).unwrap();assert!(dir.join("mods/test.jar").exists());
 let token=remove_profile_inner(&rt,&id).unwrap()["token"].as_str().unwrap().to_owned();assert!(!dir.exists());assert_eq!(state(&rt).unwrap()["profiles"],json!([]));restore_inner(&rt,&token).unwrap();assert_eq!(fs::read_to_string(dir.join("saves/World/level.dat")).unwrap(),"world");assert_eq!(state(&rt).unwrap()["selected"],id);fs::remove_dir_all(root).unwrap();
 }
}
