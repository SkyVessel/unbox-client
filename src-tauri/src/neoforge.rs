use crate::{Runtime,atomic};
use serde_json::{Value,json};
use std::{fs,path::{Path,PathBuf},time::Duration};
// FML 12's SDL early window can block macOS before Minecraft starts rendering.
pub fn prepare_profile_config(game:&Path,version:&str)->Result<(),String>{
 if !cfg!(target_os="macos")||version!="26.3"{return Ok(())}
 let path=game.join("config/fml.toml");
 let current=match fs::read_to_string(&path){Ok(v)=>v,Err(e) if e.kind()==std::io::ErrorKind::NotFound=>String::new(),Err(e)=>return Err(e.to_string())};
 let updated=disable_early_window(&current)?;
 if updated!=current{atomic(&path,updated.as_bytes())?}Ok(())
}
fn disable_early_window(config:&str)->Result<String,String>{
 let mut offset=0;
 for line in config.split_inclusive('\n'){
  if line.trim_start().starts_with('['){break}
  if let Some((key,value))=line.split_once('='){
   if matches!(key.trim(),"earlyWindowControl"|"\"earlyWindowControl\""|"'earlyWindowControl'"){
    let trimmed=value.trim_start();
    let length=if trimmed.starts_with("true"){4}else if trimmed.starts_with("false"){5}else{return Err("Invalid earlyWindowControl value in NeoForge configuration".into())};
    if !trimmed[length..].trim_start().is_empty()&&!trimmed[length..].trim_start().starts_with('#'){return Err("Invalid earlyWindowControl value in NeoForge configuration".into())}
    let start=offset+key.len()+1+value.len()-trimmed.len();
    let mut updated=config.to_owned();updated.replace_range(start..start+length,"false");return Ok(updated)
   }
  }
  offset+=line.len();
 }
 Ok(format!("earlyWindowControl = false\n{config}"))
}
#[cfg(test)]mod early_window_tests{
 use super::*;
 #[test]fn empty_config_disables_early_window(){assert_eq!(disable_early_window("").unwrap(),"earlyWindowControl = false\n");}
 #[test]fn existing_value_preserves_comments_and_other_keys(){let source="# Early display\r\nearlyWindowControl = true  # user comment\r\nversionCheck = false\r\n[dependencyOverrides]\r\nexample = [\"+other\"]\r\n";assert_eq!(disable_early_window(source).unwrap(),source.replacen("= true","= false",1));}
 #[test]fn absent_root_key_is_inserted_before_tables(){let source="# Keep this\nversionCheck = true\n[other]\nearlyWindowControl = true\n";let updated=disable_early_window(source).unwrap();assert_eq!(updated,format!("earlyWindowControl = false\n{source}"));assert_eq!(disable_early_window(&updated).unwrap(),updated);}
 #[test]fn other_game_versions_do_not_write_configuration(){let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());prepare_profile_config(&root,"26.1").unwrap();prepare_profile_config(&root,"26.1.2").unwrap();assert!(!root.exists());}
}
// Use the upstream installer so its patching and library verification stay authoritative.
pub async fn prepare(rt:&Runtime,java:&Path,client:&reqwest::Client,meta:&Value)->Result<Value,String>{
 let minecraft=meta["id"].as_str().ok_or("Missing Minecraft version")?;
 let (version,installer_sha1)=crate::versions::neoforge(minecraft).ok_or("Unsupported Minecraft version for NeoForge")?;
 let root=rt.root.join("runtime/neoforge").join(version);
 let output=root.join("versions").join(format!("neoforge-{version}/neoforge-{version}.json"));
 let ready=fs::read(&output).ok().and_then(|b|serde_json::from_slice::<Value>(&b).ok()).is_some_and(|v|
  v["libraries"].as_array().is_some_and(|libs|libs.iter().all(|l|l["downloads"]["artifact"]["path"].as_str().and_then(|p|crate::launcher::safe_path(&root.join("libraries"),p).ok()).is_some_and(|p|p.exists())))&&root.join(format!("libraries/net/neoforged/minecraft-client-patched/{version}/minecraft-client-patched-{version}.jar")).exists());
 if !ready{
  fs::create_dir_all(&root).map_err(|e|e.to_string())?;
  let vanilla=root.join(format!("versions/{minecraft}"));fs::create_dir_all(&vanilla).map_err(|e|e.to_string())?;
  fs::copy(rt.root.join(format!("runtime/{minecraft}.jar")),vanilla.join(format!("{minecraft}.jar"))).map_err(|e|e.to_string())?;
  atomic(&vanilla.join(format!("{minecraft}.json")),&serde_json::to_vec(meta).unwrap())?;
  atomic(&root.join("launcher_profiles.json"),br#"{"profiles":{}}"#)?;
  let installer=root.join("installer.jar");
  crate::launcher::download(client,&format!("https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-installer.jar"),&installer,Some(installer_sha1)).await?;
  let log=fs::File::create(root.join("install.log")).map_err(|e|e.to_string())?;
  let mut command=crate::process::async_command(java);command.arg("-jar").arg(&installer).arg("--installClient").arg(&root).current_dir(&root).stdout(log.try_clone().map_err(|e|e.to_string())?).stderr(log).kill_on_drop(true);
  let status=tokio::time::timeout(Duration::from_secs(600),command.status()).await.map_err(|_|"NeoForge installation timed out. Retry to resume verified downloads.")?.map_err(|e|e.to_string())?;
  if !status.success(){return Err(format!("NeoForge installation failed. See {}",root.join("install.log").display()))}
 }
 let mut v:Value=serde_json::from_slice(&fs::read(output).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
 if v["inheritsFrom"]!=minecraft||v["id"]!=format!("neoforge-{version}"){return Err("Unexpected NeoForge metadata".into())}
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
