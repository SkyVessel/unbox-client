use crate::{Runtime,atomic,profile_dir};
use serde_json::{Value,json};
use sha1::{Sha1,Digest};
use std::{fs,path::{Path,PathBuf},time::Duration};
use tauri::Manager;

fn phase(rt:&Runtime,name:&str,progress:usize){let mut j=rt.job.lock().unwrap();j["stage"]=json!(name);j["progress"]=json!(progress);}
fn verified(path:&Path,hash:&str)->bool{fs::read(path).map(|b|format!("{:x}",Sha1::digest(b))==hash).unwrap_or(false)}
async fn json_get(client:&reqwest::Client,url:&str)->Result<Value,String>{client.get(url).send().await.map_err(|e|e.to_string())?.error_for_status().map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())}
async fn download(client:&reqwest::Client,url:&str,path:&Path,hash:Option<&str>)->Result<(),String>{
    if let Some(hash)=hash {if verified(path,hash){return Ok(())}}
    let parsed=reqwest::Url::parse(url).map_err(|e|e.to_string())?;
    if parsed.scheme()!="https" {return Err("Downloads must use HTTPS".into())}
    let bytes=client.get(parsed).send().await.map_err(|e|e.to_string())?.error_for_status().map_err(|e|e.to_string())?.bytes().await.map_err(|e|e.to_string())?;
    if let Some(hash)=hash {if format!("{:x}",Sha1::digest(&bytes))!=hash{return Err(format!("Integrity check failed: {}",path.file_name().unwrap_or_default().to_string_lossy()))}}
    atomic(path,&bytes)
}
pub fn allowed(v:&Value)->bool {
    let Some(rules)=v["rules"].as_array() else{return true};
    let os=if cfg!(target_os="macos"){"osx"}else if cfg!(target_os="windows"){"windows"}else{"linux"};
    let mut pass=false;
    for rule in rules {
        if rule.get("features").is_some(){continue}
        if rule["os"]["name"].as_str().is_some_and(|n|n!=os){continue}
        if let Some(a)=rule["os"]["arch"].as_str(){let arch=std::env::consts::ARCH;if a!=arch && !(a=="arm64"&&arch=="aarch64"){continue}}
        if rule["os"].get("version").is_some(){continue}
        pass=rule["action"]=="allow";
    }pass
}
fn safe_path(root:&Path,relative:&str)->Result<PathBuf,String>{let p=Path::new(relative);if p.is_absolute()||p.components().any(|c|matches!(c,std::path::Component::ParentDir|std::path::Component::Prefix(_))){return Err("Unsafe download path".into())}Ok(root.join(p))}
pub fn args(values:&Value,vars:&std::collections::HashMap<&str,String>)->Vec<String>{
    let mut out=Vec::new();if let Some(values)=values.as_array(){for v in values{if !allowed(v){continue}let items=if let Some(s)=v.as_str(){vec![s.to_string()]}else if let Some(s)=v["value"].as_str(){vec![s.to_string()]}else{v["value"].as_array().map(|a|a.iter().filter_map(|v|v.as_str().map(str::to_owned)).collect()).unwrap_or_default()};for mut s in items{for(k,v)in vars{s=s.replace(&format!("${{{k}}}"),v)}out.push(s)}}}out
}
fn java()->Result<PathBuf,String>{
    let mut candidates=Vec::new();
    #[cfg(target_os="macos")] {if let Ok(out)=std::process::Command::new("/usr/libexec/java_home").args(["-v","25"]).output(){if out.status.success(){candidates.push(PathBuf::from(String::from_utf8_lossy(&out.stdout).trim()).join("bin/java"));}}}
    if let Some(home)=std::env::var_os("JAVA_HOME"){candidates.push(PathBuf::from(home).join(if cfg!(windows){"bin/java.exe"}else{"bin/java"}));}
    candidates.push(PathBuf::from("java"));
    for p in candidates{if let Ok(out)=std::process::Command::new(&p).arg("-version").output(){let s=String::from_utf8_lossy(&out.stderr);if out.status.success()&&s.contains("version \"25"){return Ok(p)}}}
    Err("Java 25 is required. Install Temurin 25, then retry.".into())
}
pub async fn run(rt:&Runtime,profile:&Value,username:&str,memory:u32,prepare_only:bool,verification:bool,app:&tauri::AppHandle)->Result<(),String>{
    if profile["version"]!="26.1"||!["fabric","vanilla"].contains(&profile["loader"].as_str().unwrap_or("")){return Err("This game version or mod loader is not supported by Unbox yet".into())}
    let (session,local_name)=if prepare_only||(verification&&cfg!(debug_assertions)){(None,username.to_owned())}else{crate::auth::launch_account(rt).await?};
    let java=java()?;
    let client=reqwest::Client::builder().no_proxy().user_agent("UnboxClient/0.1 (local desktop launcher)").connect_timeout(Duration::from_secs(15)).timeout(Duration::from_secs(180)).build().map_err(|e|e.to_string())?;
    let id=profile["id"].as_str().ok_or("Missing profile")?;let game=profile_dir(rt,id)?;crate::sharing::recover(&game)?;let shared=rt.root.join("runtime");
    let cache=shared.join("26.1.json");
    let meta:Value=if cache.exists(){serde_json::from_slice(&fs::read(&cache).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?}else{
        let manifest=json_get(&client,"https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").await?;
        let ver=manifest["versions"].as_array().ok_or("Invalid Minecraft manifest")?.iter().find(|v|v["id"]=="26.1").ok_or("Minecraft 26.1 is unavailable")?;
        download(&client,ver["url"].as_str().ok_or("Missing metadata URL")?,&cache,ver["sha1"].as_str()).await?;
        serde_json::from_slice(&fs::read(&cache).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?
    };
    if meta["id"]!="26.1"{return Err("Unexpected game version".into())}
    phase(rt,"Downloading Minecraft 26.1",5);
    let jar=shared.join("26.1.jar");download(&client,meta["downloads"]["client"]["url"].as_str().ok_or("Missing client URL")?,&jar,meta["downloads"]["client"]["sha1"].as_str()).await?;
    let libs=shared.join("libraries");let natives=shared.join("natives");fs::create_dir_all(&natives).map_err(|e|e.to_string())?;
    let mut classpath=Vec::new();
    let libraries=meta["libraries"].as_array().ok_or("Invalid library list")?;
    for(i,lib)in libraries.iter().enumerate(){if !allowed(lib){continue}let a=&lib["downloads"]["artifact"];if let (Some(p),Some(url))=(a["path"].as_str(),a["url"].as_str()){
        let path=safe_path(&libs,p)?;download(&client,url,&path,a["sha1"].as_str()).await?;classpath.push(path.to_string_lossy().to_string());
    }phase(rt,"Preparing game libraries",10+i*20/libraries.len());}
    let assets=shared.join("assets");let index_id=meta["assetIndex"]["id"].as_str().ok_or("Missing assets")?;let index=assets.join("indexes").join(format!("{index_id}.json"));
    download(&client,meta["assetIndex"]["url"].as_str().ok_or("Missing asset URL")?,&index,meta["assetIndex"]["sha1"].as_str()).await?;
    let asset_meta:Value=serde_json::from_slice(&fs::read(&index).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    let objects=asset_meta["objects"].as_object().ok_or("Invalid asset index")?;
    use futures_util::{stream,StreamExt,TryStreamExt};
    let total=objects.len();let count=std::sync::atomic::AtomicUsize::new(0);
    let hashes:Vec<String>=objects.values().map(|o|o["hash"].as_str().unwrap_or("").to_owned()).collect();
    stream::iter(hashes.into_iter().map(|hash|{let client=&client;let assets=&assets;let count=&count;async move{
        if hash.len()!=40||!hash.bytes().all(|b|b.is_ascii_hexdigit()){return Err("Invalid asset hash".into())}
        let rel=format!("{}/{}",&hash[..2],hash);download(client,&format!("https://resources.download.minecraft.net/{rel}"),&assets.join("objects").join(rel),Some(&hash)).await?;
        let n=count.fetch_add(1,std::sync::atomic::Ordering::Relaxed)+1;phase(rt,&format!("Game assets · {n}/{total}"),30+n*45/total);Ok::<(),String>(())
    }})).buffer_unordered(12).try_collect::<Vec<_>>().await?;
    let mut main=meta["mainClass"].as_str().ok_or("Missing entry point")?.to_string();let mut extra_jvm=vec![];
    if profile["loader"]=="fabric"{
        phase(rt,"Installing Fabric",78);let loader_path=shared.join("fabric-0.19.5.json");
        let fabric:Value=if loader_path.exists(){serde_json::from_slice(&fs::read(&loader_path).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?}else{let v=json_get(&client,"https://meta.fabricmc.net/v2/versions/loader/26.1/0.19.5/profile/json").await?;atomic(&loader_path,&serde_json::to_vec(&v).unwrap())?;v};
        main=fabric["mainClass"].as_str().ok_or("Invalid Fabric metadata")?.into();
        for lib in fabric["libraries"].as_array().ok_or("Missing Fabric libraries")?{
            let coords=lib["name"].as_str().ok_or("Invalid Maven coordinate")?.split(':').collect::<Vec<_>>();if coords.len()!=3{return Err("Unsupported Maven coordinate".into())}
            let rel=format!("{}/{}/{}/{}-{}.jar",coords[0].replace('.',"/"),coords[1],coords[2],coords[1],coords[2]);let base=lib["url"].as_str().unwrap_or("https://maven.fabricmc.net/");let url=format!("{}{rel}",base.trim_end_matches('/').to_owned()+"/");let path=safe_path(&libs,&rel)?;
            let checksum_path=path.with_extension("sha1");let hash=if checksum_path.exists(){fs::read_to_string(&checksum_path).map_err(|e|e.to_string())?}else{let h=client.get(format!("{url}.sha1")).send().await.map_err(|e|e.to_string())?.error_for_status().map_err(|e|e.to_string())?.text().await.map_err(|e|e.to_string())?;atomic(&checksum_path,h.as_bytes())?;h};
            let hash=hash.split_whitespace().next().ok_or("Missing checksum")?;download(&client,&url,&path,Some(hash)).await?;classpath.push(path.to_string_lossy().to_string());
        }
        if let Some(a)=fabric["arguments"]["jvm"].as_array(){extra_jvm.extend(a.iter().filter_map(|v|v.as_str().map(str::to_owned)));}
        let lock:Value=serde_json::from_str(include_str!("../resources/performance-lock.json")).map_err(|e|e.to_string())?;
        fs::create_dir_all(game.join("mods")).map_err(|e|e.to_string())?;
        for m in lock.as_array().unwrap(){phase(rt,&format!("Installing {}",m["slug"].as_str().unwrap()),85);let path=safe_path(&game.join("mods"),m["filename"].as_str().ok_or("Missing mod filename")?)?;download(&client,m["url"].as_str().ok_or("Missing mod URL")?,&path,m["sha1"].as_str()).await?;}
        let own=rt.resources.join("resources/unbox-client.jar");if own.exists(){atomic(&game.join("mods/unbox-client.jar"),&fs::read(own).map_err(|e|e.to_string())?)?;}else{return Err("Unbox in-game module is missing from this build".into())}
        migrate_managed_mods(&game,&lock)?;
        atomic(&game.join("unbox-performance-lock.json"),&serde_json::to_vec_pretty(&lock).unwrap())?;
    }
    classpath.push(jar.to_string_lossy().to_string());
    for folder in ["resourcepacks","shaderpacks","logs"]{fs::create_dir_all(game.join(folder)).map_err(|e|e.to_string())?;}
    atomic(&game.join("unbox-ready.json"),br#"{"version":"26.1","verified":true}"#)?;
    if prepare_only{return Ok(())}
    let sep=if cfg!(windows){";"}else{":"};
    let digest=session.as_ref().map(|s|s.uuid.clone()).unwrap_or_else(||md5_offline(&local_name));
    let player=session.as_ref().map(|s|s.name.as_str()).unwrap_or(&local_name);
    let vars=std::collections::HashMap::from([
        ("natives_directory",natives.to_string_lossy().into_owned()),("launcher_name","Unbox Client".into()),("launcher_version",env!("CARGO_PKG_VERSION").into()),("classpath",classpath.join(sep)),
        ("auth_player_name",player.into()),("version_name","26.1".into()),("game_directory",game.to_string_lossy().into_owned()),("assets_root",assets.to_string_lossy().into_owned()),("assets_index_name",index_id.into()),("auth_uuid",digest),
        ("auth_access_token",session.as_ref().map(|s|s.token.clone()).unwrap_or("0".into())),("clientid",session.as_ref().map(|s|s.client_id.clone()).unwrap_or_default()),("auth_xuid",session.as_ref().map(|s|s.xuid.clone()).unwrap_or_default()),("user_type",if session.as_ref().is_some_and(|s|s.token!="0"){"msa"}else{"legacy"}.into()),("version_type","release".into())]);
    let mut argv=args(&meta["arguments"]["jvm"],&vars);argv.extend(extra_jvm);argv.push(format!("-Xmx{memory}M"));argv.push("-Xms512M".into());argv.push(format!("-Dunbox.sharedCache={}",rt.root.join("shared-cache").display()));argv.push(main);argv.extend(args(&meta["arguments"]["game"],&vars));
    if argv.iter().any(|s|s.contains("${")){return Err("Unresolved launch argument".into())}
    let log=fs::File::create(game.join("logs/unbox-launch.log")).map_err(|e|e.to_string())?;
    let mut child=tokio::process::Command::new(java).args(argv).current_dir(&game).stdout(log.try_clone().map_err(|e|e.to_string())?).stderr(log).spawn().map_err(|e|e.to_string())?;
    phase(rt,"Playing",100);
    let minimize=fs::read(rt.root.join("state.json")).ok().and_then(|b|serde_json::from_slice::<Value>(&b).ok()).is_none_or(|s|s["settings"]["minimize"]!=false);
    if minimize{if let Some(w)=app.get_webview_window("main"){let _=w.minimize();}}
    let result=child.wait().await.map_err(|e|e.to_string())?;if let Some(w)=app.get_webview_window("main"){let _=w.unminimize();}
    if !result.success(){return Err(format!("Minecraft exited with {}. Open the profile folder → logs/unbox-launch.log",result))}Ok(())
}
fn migrate_managed_mods(game:&Path,next:&Value)->Result<(),String>{
    let path=game.join("unbox-performance-lock.json");
    if !path.exists(){return Ok(())}
    let old:Value=serde_json::from_slice(&fs::read(path).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    for entry in old.as_array().ok_or("Invalid installed mod manifest")?{
        let name=entry["filename"].as_str().ok_or("Missing installed filename")?;
        if next.as_array().unwrap().iter().any(|m|m["filename"]==name){continue}
        let source=safe_path(&game.join("mods"),name)?;
        if !source.exists(){continue}
        if !verified(&source,entry["sha1"].as_str().unwrap_or("")){return Err(format!("Managed mod {name} was changed. Move it out of mods before retrying the update."))}
        let backup=safe_path(&game.join("unbox-backups/mods"),name)?;
        fs::create_dir_all(backup.parent().unwrap()).map_err(|e|e.to_string())?;
        fs::rename(source,backup).map_err(|e|e.to_string())?;
    }
    Ok(())
}
fn md5_offline(name:&str)->String{
    // Java UUID.nameUUIDFromBytes hashes raw bytes, without a UUID namespace.
    use md5::{Md5,Digest};let mut bytes:[u8;16]=Md5::digest(format!("OfflinePlayer:{name}").as_bytes()).into();bytes[6]=(bytes[6]&0x0f)|0x30;bytes[8]=(bytes[8]&0x3f)|0x80;uuid::Uuid::from_bytes(bytes).simple().to_string()
}
#[cfg(test)]mod tests{
    use super::*;
    #[test]fn disallow_traversal(){assert!(safe_path(Path::new("/tmp/root"),"../escape.jar").is_err());assert!(safe_path(Path::new("/tmp/root"),"/absolute.jar").is_err());}
    #[test]fn optional_features_are_not_enabled(){assert!(!allowed(&json!({"rules":[{"action":"allow","features":{"is_demo_user":true}}]})));}
    #[test]fn offline_identity_matches_java(){assert_eq!(md5_offline("Notch"),"b50ad385829d3141a2167e7d7539ba7f");}
    #[test]fn managed_update_backs_up_only_verified_old_mods(){
        let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());fs::create_dir_all(root.join("mods")).unwrap();
        fs::write(root.join("mods/old.jar"),b"old").unwrap();fs::write(root.join("mods/player.jar"),b"player").unwrap();
        let hash=format!("{:x}",Sha1::digest(b"old"));let old=json!([{"filename":"old.jar","sha1":hash}]);
        fs::write(root.join("unbox-performance-lock.json"),serde_json::to_vec(&old).unwrap()).unwrap();
        migrate_managed_mods(&root,&json!([{"filename":"new.jar"}])).unwrap();
        assert!(!root.join("mods/old.jar").exists());assert!(root.join("unbox-backups/mods/old.jar").exists());assert!(root.join("mods/player.jar").exists());
        fs::write(root.join("mods/old.jar"),b"custom changes").unwrap();
        assert!(migrate_managed_mods(&root,&json!([])).is_err());assert_eq!(fs::read(root.join("mods/old.jar")).unwrap(),b"custom changes");
        fs::remove_dir_all(root).unwrap();
    }
}
