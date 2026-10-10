use crate::{Runtime,atomic};
use serde_json::{Value,json};
use std::{fs,path::{Path,PathBuf},time::Duration};
const VERSION:&str="26.1.0.19-beta";
const INSTALLER_SHA1:&str="7d9f928b8d34fcbb1ea7bad30f6c4849bab4aef7";
// Use the upstream installer so its patching and library verification stay authoritative.
pub async fn prepare(rt:&Runtime,java:&Path,client:&reqwest::Client,meta:&Value)->Result<Value,String>{
 let root=rt.root.join("runtime/neoforge").join(VERSION);
 let output=root.join("versions").join(format!("neoforge-{VERSION}/neoforge-{VERSION}.json"));
 let ready=fs::read(&output).ok().and_then(|b|serde_json::from_slice::<Value>(&b).ok()).is_some_and(|v|
  v["libraries"].as_array().is_some_and(|libs|libs.iter().all(|l|l["downloads"]["artifact"]["path"].as_str().and_then(|p|crate::launcher::safe_path(&root.join("libraries"),p).ok()).is_some_and(|p|p.exists())))&&root.join(format!("libraries/net/neoforged/minecraft-client-patched/{VERSION}/minecraft-client-patched-{VERSION}.jar")).exists());
 if !ready{
  fs::create_dir_all(&root).map_err(|e|e.to_string())?;
  let vanilla=root.join("versions/26.1");fs::create_dir_all(&vanilla).map_err(|e|e.to_string())?;
  fs::copy(rt.root.join("runtime/26.1.jar"),vanilla.join("26.1.jar")).map_err(|e|e.to_string())?;
  atomic(&vanilla.join("26.1.json"),&serde_json::to_vec(meta).unwrap())?;
  atomic(&root.join("launcher_profiles.json"),br#"{"profiles":{}}"#)?;
  let installer=root.join("installer.jar");
  crate::launcher::download(client,&format!("https://maven.neoforged.net/releases/net/neoforged/neoforge/{VERSION}/neoforge-{VERSION}-installer.jar"),&installer,Some(INSTALLER_SHA1)).await?;
  let log=fs::File::create(root.join("install.log")).map_err(|e|e.to_string())?;
  let mut command=tokio::process::Command::new(java);command.arg("-jar").arg(&installer).arg("--installClient").arg(&root).current_dir(&root).stdout(log.try_clone().map_err(|e|e.to_string())?).stderr(log).kill_on_drop(true);
  let status=tokio::time::timeout(Duration::from_secs(600),command.status()).await.map_err(|_|"NeoForge installation timed out. Retry to resume verified downloads.")?.map_err(|e|e.to_string())?;
  if !status.success(){return Err(format!("NeoForge installation failed. See {}",root.join("install.log").display()))}
 }
 let mut v:Value=serde_json::from_slice(&fs::read(output).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
 if v["inheritsFrom"]!="26.1"||v["id"]!=format!("neoforge-{VERSION}"){return Err("Unexpected NeoForge metadata".into())}
 v["libraryRoot"]=json!(root.join("libraries"));Ok(v)
}
pub fn classpath(base:&Value,neo:&Value,base_root:&Path)->Result<Vec<String>,String>{
 let neo_libs=neo["libraries"].as_array().ok_or("Invalid NeoForge libraries")?;
 let identity=|v:&Value|v["name"].as_str().unwrap_or("").split(':').take(2).collect::<Vec<_>>().join(":");
 let replaced:std::collections::HashSet<_>=neo_libs.iter().map(identity).collect();
 let mut cp=Vec::new();
 for lib in base["libraries"].as_array().ok_or("Invalid Minecraft libraries")?{if !crate::launcher::allowed(lib)||replaced.contains(&identity(lib)){continue}if let Some(p)=lib["downloads"]["artifact"]["path"].as_str(){cp.push(crate::launcher::safe_path(base_root,p)?.to_string_lossy().into_owned());}}
 let root=PathBuf::from(neo["libraryRoot"].as_str().ok_or("Missing NeoForge libraries")?);
 for lib in neo_libs{if !crate::launcher::allowed(lib){continue}let rel=lib["downloads"]["artifact"]["path"].as_str().ok_or("Missing NeoForge library")?;let path=crate::launcher::safe_path(&root,rel)?;if !path.exists(){return Err("NeoForge library missing; remove the incomplete runtime version and retry".into())}cp.push(path.to_string_lossy().into_owned());}
 Ok(cp)
}
#[cfg(test)]mod tests {use super::*;#[test]fn neoforge_replaces_old_library_versions(){let temp=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());fs::create_dir_all(&temp).unwrap();fs::write(temp.join("new.jar"),b"").unwrap();let base=json!({"libraries":[{"name":"g:a:1","downloads":{"artifact":{"path":"old.jar"}}},{"name":"g:b:1","downloads":{"artifact":{"path":"keep.jar"}}}]});let neo=json!({"libraryRoot":temp,"libraries":[{"name":"g:a:2","downloads":{"artifact":{"path":"new.jar"}}}]});let cp=classpath(&base,&neo,&temp).unwrap();assert_eq!(cp.len(),2);assert!(!cp.iter().any(|p|p.ends_with("old.jar")));fs::remove_dir_all(temp).unwrap();}}
