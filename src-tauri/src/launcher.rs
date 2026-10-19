use crate::{Runtime,atomic,profile_dir};
use serde_json::{Value,json};
use sha1::{Sha1,Digest};
use std::{fs,path::{Path,PathBuf},time::Duration};
use tauri::Manager;

fn performance_lock(loader:&str,version:&str)->Result<Value,String>{
    let text=match(loader,version){
        ("fabric","26.1")=>include_str!("../resources/performance-lock.json"),
        ("fabric","26.1.2")=>include_str!("../resources/performance-lock-26.1.2.json"),
        ("fabric","26.3")=>include_str!("../resources/performance-lock-26.3.json"),
        ("neoforge","26.1")=>include_str!("../resources/neoforge-performance-lock.json"),
        ("neoforge","26.1.2")=>include_str!("../resources/neoforge-performance-lock-26.1.2.json"),
        ("neoforge","26.3")=>include_str!("../resources/neoforge-performance-lock-26.3.json"),
        _=>return Err("Unsupported native performance environment".into()),
    };
    serde_json::from_str(text).map_err(|e|e.to_string())
}
fn phase(rt:&Runtime,name:&str,progress:usize){let mut j=rt.job.lock().unwrap();j["stage"]=json!(name);j["progress"]=json!(progress);}
fn verified(path:&Path,hash:&str)->bool{fs::read(path).map(|b|format!("{:x}",Sha1::digest(b))==hash).unwrap_or(false)}
async fn json_get(client:&reqwest::Client,url:&str)->Result<Value,String>{client.get(url).send().await.map_err(|e|e.to_string())?.error_for_status().map_err(|e|e.to_string())?.json().await.map_err(|e|e.to_string())}
pub(crate) async fn download(client:&reqwest::Client,url:&str,path:&Path,hash:Option<&str>)->Result<(),String>{
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
pub(crate) fn safe_path(root:&Path,relative:&str)->Result<PathBuf,String>{let p=Path::new(relative);if p.has_root()||p.components().any(|c|matches!(c,std::path::Component::ParentDir|std::path::Component::Prefix(_))){return Err("Unsafe download path".into())}Ok(root.join(p))}
pub fn args(values:&Value,vars:&std::collections::HashMap<&str,String>)->Vec<String>{
    let mut out=Vec::new();if let Some(values)=values.as_array(){for v in values{if !allowed(v){continue}let items=if let Some(s)=v.as_str(){vec![s.to_string()]}else if let Some(s)=v["value"].as_str(){vec![s.to_string()]}else{v["value"].as_array().map(|a|a.iter().filter_map(|v|v.as_str().map(str::to_owned)).collect()).unwrap_or_default()};for mut s in items{for(k,v)in vars{s=s.replace(&format!("${{{k}}}"),v)}out.push(s)}}}out
}
fn java()->Result<PathBuf,String>{
    let mut candidates=Vec::new();
    #[cfg(target_os="macos")] {if let Ok(out)=crate::process::command("/usr/libexec/java_home").args(["-v","25"]).output(){if out.status.success(){candidates.push(PathBuf::from(String::from_utf8_lossy(&out.stdout).trim()).join("bin/java"));}}}
    if let Some(home)=std::env::var_os("JAVA_HOME"){candidates.push(PathBuf::from(home).join(if cfg!(windows){"bin/java.exe"}else{"bin/java"}));}
    candidates.push(PathBuf::from("java"));
    for p in candidates{if let Ok(out)=crate::process::command(&p).arg("-version").output(){let s=String::from_utf8_lossy(&out.stderr);if out.status.success()&&s.contains("version \"25"){return Ok(p)}}}
    Err("Java 25 is required. Install Temurin 25, then retry.".into())
}
pub async fn run(rt:&Runtime,profile:&Value,username:&str,memory:u32,prepare_only:bool,verification:bool,app:&tauri::AppHandle)->Result<(),String>{
    let version=profile["version"].as_str().unwrap_or("");
    if !crate::versions::supported(version)||!["fabric","vanilla","neoforge"].contains(&profile["loader"].as_str().unwrap_or("")){return Err("This game version or mod loader is not supported by Unbox yet".into())}
    let (session,local_name)=if prepare_only||(verification&&cfg!(debug_assertions)){(None,username.to_owned())}else{crate::auth::launch_account(rt).await?};
    let java=java()?;
    let client=reqwest::Client::builder().no_proxy().user_agent("UnboxClient/0.1 (local desktop launcher)").connect_timeout(Duration::from_secs(15)).timeout(Duration::from_secs(180)).build().map_err(|e|e.to_string())?;
    let id=profile["id"].as_str().ok_or("Missing profile")?;let game=profile_dir(rt,id)?;crate::sharing::recover(&game)?;let shared=rt.root.join("runtime");
    let cache=shared.join(format!("{version}.json"));
    let meta:Value=if cache.exists(){serde_json::from_slice(&fs::read(&cache).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?}else{
        let manifest=json_get(&client,"https://piston-meta.mojang.com/mc/game/version_manifest_v2.json").await?;
        let ver=manifest["versions"].as_array().ok_or("Invalid Minecraft manifest")?.iter().find(|v|v["id"]==version).ok_or("Minecraft version is unavailable")?;
        download(&client,ver["url"].as_str().ok_or("Missing metadata URL")?,&cache,ver["sha1"].as_str()).await?;
        serde_json::from_slice(&fs::read(&cache).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?
    };
    if meta["id"]!=version{return Err("Unexpected game version".into())}
    phase(rt,&format!("Downloading Minecraft {version}"),5);
    let jar=shared.join(format!("{version}.jar"));download(&client,meta["downloads"]["client"]["url"].as_str().ok_or("Missing client URL")?,&jar,meta["downloads"]["client"]["sha1"].as_str()).await?;
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
    let mut main=meta["mainClass"].as_str().ok_or("Missing entry point")?.to_string();let mut extra_jvm=vec![];let mut extra_game=Value::Null;let mut library_root=libs.clone();
    if profile["loader"]=="fabric"{
        phase(rt,"Installing Fabric",78);let loader_path=shared.join(format!("fabric-{version}-0.19.5.json"));
        let fabric:Value=if loader_path.exists(){serde_json::from_slice(&fs::read(&loader_path).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?}else{let v=json_get(&client,&format!("https://meta.fabricmc.net/v2/versions/loader/{version}/0.19.5/profile/json")).await?;atomic(&loader_path,&serde_json::to_vec(&v).unwrap())?;v};
        main=fabric["mainClass"].as_str().ok_or("Invalid Fabric metadata")?.into();
        for lib in fabric["libraries"].as_array().ok_or("Missing Fabric libraries")?{
            let coords=lib["name"].as_str().ok_or("Invalid Maven coordinate")?.split(':').collect::<Vec<_>>();if coords.len()!=3{return Err("Unsupported Maven coordinate".into())}
            let rel=format!("{}/{}/{}/{}-{}.jar",coords[0].replace('.',"/"),coords[1],coords[2],coords[1],coords[2]);let base=lib["url"].as_str().unwrap_or("https://maven.fabricmc.net/");let url=format!("{}{rel}",base.trim_end_matches('/').to_owned()+"/");let path=safe_path(&libs,&rel)?;
            let checksum_path=path.with_extension("sha1");let hash=if checksum_path.exists(){fs::read_to_string(&checksum_path).map_err(|e|e.to_string())?}else{let h=client.get(format!("{url}.sha1")).send().await.map_err(|e|e.to_string())?.error_for_status().map_err(|e|e.to_string())?.text().await.map_err(|e|e.to_string())?;atomic(&checksum_path,h.as_bytes())?;h};
            let hash=hash.split_whitespace().next().ok_or("Missing checksum")?;download(&client,&url,&path,Some(hash)).await?;classpath.push(path.to_string_lossy().to_string());
        }
        if let Some(a)=fabric["arguments"]["jvm"].as_array(){extra_jvm.extend(a.iter().filter_map(|v|v.as_str().map(str::to_owned)));}
        let mut lock:Value=performance_lock("fabric",version)?;
        append_wick(&mut lock,"fabric",version)?;
        check_survival_shared_dependency(&game,&lock)?;
        fs::create_dir_all(game.join("mods")).map_err(|e|e.to_string())?;
        for m in lock.as_array().unwrap(){phase(rt,&format!("Installing {}",m["slug"].as_str().unwrap()),85);let path=safe_path(&game.join("mods"),m["filename"].as_str().ok_or("Missing mod filename")?)?;download(&client,m["url"].as_str().ok_or("Missing mod URL")?,&path,m["sha1"].as_str()).await?;}
        let own=rt.resources.join(crate::versions::client_resource("fabric",version)?);if own.exists(){atomic(&game.join("mods/unbox-client.jar"),&fs::read(own).map_err(|e|e.to_string())?)?;}else{return Err("Unbox in-game module is missing from this build".into())}
        migrate_managed_mods(&game,&lock)?;
        atomic(&game.join("unbox-performance-lock.json"),&serde_json::to_vec_pretty(&lock).unwrap())?;
    }
    if profile["loader"]=="neoforge"{
        crate::neoforge::prepare_profile_config(&game,version)?;
        phase(rt,&format!("Installing NeoForge {version}"),78);
        let neo=crate::neoforge::prepare(rt,&java,&client,&meta).await?;
        classpath=crate::neoforge::classpath(&meta,&neo,&libs)?;
        main=neo["mainClass"].as_str().ok_or("Missing NeoForge entry point")?.into();
        extra_jvm.extend(neo["arguments"]["jvm"].as_array().unwrap().iter().filter_map(|v|v.as_str().map(str::to_owned)));
        extra_game=neo["arguments"]["game"].clone();library_root=PathBuf::from(neo["libraryRoot"].as_str().unwrap());
        let mut lock:Value=performance_lock("neoforge",version)?;
        append_wick(&mut lock,"neoforge",version)?;
        for m in lock.as_array().unwrap(){phase(rt,&format!("Installing {}",m["slug"].as_str().unwrap()),85);download(&client,m["url"].as_str().unwrap(),&safe_path(&game.join("mods"),m["filename"].as_str().unwrap())?,m["sha1"].as_str()).await?;}
        let own=rt.resources.join(crate::versions::client_resource("neoforge",version)?);
        atomic(&game.join("mods/unbox-client.jar"),&fs::read(own).map_err(|_|"Unbox NeoForge module is missing from this build")?)?;
        migrate_managed_mods(&game,&lock)?;atomic(&game.join("unbox-performance-lock.json"),&serde_json::to_vec_pretty(&lock).unwrap())?;
    }
    if let Some(loader @ ("fabric" | "neoforge"))=profile["loader"].as_str(){prepare_survival_mods(rt,&client,&game,loader,version).await?;}
    if profile["loader"]!="neoforge"{classpath.push(jar.to_string_lossy().to_string());}
    for folder in ["resourcepacks","shaderpacks","logs"]{fs::create_dir_all(game.join(folder)).map_err(|e|e.to_string())?;}
    atomic(&game.join("unbox-ready.json"),&serde_json::to_vec(&json!({"version":version,"verified":true})).unwrap())?;
    if prepare_only{return Ok(())}
    let sep=if cfg!(windows){";"}else{":"};
    let digest=session.as_ref().map(|s|s.uuid.clone()).unwrap_or_else(||md5_offline(&local_name));
    let player=session.as_ref().map(|s|s.name.as_str()).unwrap_or(&local_name);
    let vars=std::collections::HashMap::from([
        ("library_directory",library_root.to_string_lossy().into_owned()),("natives_directory",natives.to_string_lossy().into_owned()),("launcher_name","Unbox Client".into()),("launcher_version",env!("CARGO_PKG_VERSION").into()),("classpath",classpath.join(sep)),
        ("auth_player_name",player.into()),("version_name",version.into()),("game_directory",game.to_string_lossy().into_owned()),("assets_root",assets.to_string_lossy().into_owned()),("assets_index_name",index_id.into()),("auth_uuid",digest),
        ("auth_access_token",session.as_ref().map(|s|s.token.clone()).unwrap_or("0".into())),("clientid",session.as_ref().map(|s|s.client_id.clone()).unwrap_or_default()),("auth_xuid",session.as_ref().map(|s|s.xuid.clone()).unwrap_or_default()),("user_type",if session.as_ref().is_some_and(|s|s.token!="0"){"msa"}else{"legacy"}.into()),("version_type","release".into())]);
    let mut argv=args(&meta["arguments"]["jvm"],&vars);argv.extend(args(&json!(extra_jvm),&vars));argv.push(format!("-Xmx{memory}M"));argv.push("-Xms512M".into());argv.push(format!("-Dunbox.sharedCache={}",rt.root.join("shared-cache").display()));argv.push(main);argv.extend(args(&meta["arguments"]["game"],&vars));argv.extend(args(&extra_game,&vars));
    if argv.iter().any(|s|s.contains("${")){return Err("Unresolved launch argument".into())}
    let log=fs::File::create(game.join("logs/unbox-launch.log")).map_err(|e|e.to_string())?;
    let mut child=crate::process::async_command(java).args(argv).current_dir(&game).stdout(log.try_clone().map_err(|e|e.to_string())?).stderr(log).spawn().map_err(|e|e.to_string())?;
    phase(rt,"Playing",100);
    let minimize=fs::read(rt.root.join("state.json")).ok().and_then(|b|serde_json::from_slice::<Value>(&b).ok()).is_none_or(|s|s["settings"]["minimize"]!=false);
    if minimize{if let Some(w)=app.get_webview_window("main"){let _=w.minimize();}}
    let result=child.wait().await.map_err(|e|e.to_string())?;if let Some(w)=app.get_webview_window("main"){let _=w.unminimize();}
    if !result.success(){return Err(format!("Minecraft exited with {}. Open the profile folder → logs/unbox-launch.log",result))}Ok(())
}
// Kept separate from performance mods: these include host-controlled gameplay modules.
const SURVIVAL_MANIFEST: &str = "unbox-survival-mods-lock.json";
fn survival_digest(path:&Path,entry:&Value)->bool{
    fs::symlink_metadata(path).is_ok_and(|m|m.is_file()&&!m.file_type().is_symlink())
        &&fs::read(path).is_ok_and(|b|entry["sha512"].as_str().is_some_and(|h|format!("{:x}",sha2::Sha512::digest(&b))==h)
            &&entry["sha1"].as_str().is_some_and(|h|format!("{:x}",Sha1::digest(&b))==h))
}
pub(crate) fn jar_mod_ids(path:&Path)->Result<Vec<String>,String>{
    use std::io::Read;
    let mut zip=zip::ZipArchive::new(fs::File::open(path).map_err(|e|e.to_string())?).map_err(|_|format!("Cannot inspect mod {}. Move this invalid JAR out of mods before retrying.",path.display()))?;
    let mut ids=Vec::new();
    if let Ok(f)=zip.by_name("fabric.mod.json"){
        if f.size()>262144{return Err("Mod metadata is too large".into())}
        let m:Value=serde_json::from_reader(f).map_err(|e|e.to_string())?;
        if let Some(id)=m["id"].as_str(){ids.push(id.to_owned())}
        if let Some(provides)=m["provides"].as_array(){ids.extend(provides.iter().filter_map(|v|v.as_str().map(str::to_owned)));}
    }
    if let Ok(mut f)=zip.by_name("META-INF/neoforge.mods.toml"){
        if f.size()>262144{return Err("Mod metadata is too large".into())}
        let mut text=String::new();f.read_to_string(&mut text).map_err(|e|e.to_string())?;
        let m:toml::Value=toml::from_str(&text).map_err(|e|e.to_string())?;
        if let Some(mods)=m.get("mods").and_then(|v|v.as_array()){ids.extend(mods.iter().filter_map(|v|v.get("modId").and_then(|v|v.as_str()).map(str::to_owned)));}
    }
    // Language containers such as Kotlin For Forge declare their descriptor in Jar-in-Jar.
    if ids.is_empty(){
        let index=if let Ok(f)=zip.by_name("META-INF/jarjar/metadata.json"){
            if f.size()>262144{return Err("Jar-in-Jar metadata is too large".into())}
            Some(serde_json::from_reader::<_,Value>(f).map_err(|e|e.to_string())?)
        }else{None};
        if let Some(index)=index{
            let jars=index["jars"].as_array().filter(|v|v.len()<=64).ok_or("Invalid Jar-in-Jar metadata")?;
            let mut total=0u64;
            for item in jars{
                let path=item["path"].as_str().ok_or("Missing embedded mod path")?;
                if !path.starts_with("META-INF/jarjar/")||path.contains("..")||!path.ends_with(".jar"){return Err("Invalid embedded mod path".into())}
                let f=zip.by_name(path).map_err(|_|"Missing embedded mod")?;total=total.saturating_add(f.size());
                if total>64*1024*1024{return Err("Embedded mod scan exceeds limit".into())}
                let mut data=Vec::new();f.take(64*1024*1024+1).read_to_end(&mut data).map_err(|e|e.to_string())?;
                if data.len()>64*1024*1024{return Err("Embedded mod is too large".into())}
                let mut nested=zip::ZipArchive::new(std::io::Cursor::new(data)).map_err(|e|e.to_string())?;
                if let Ok(mut f)=nested.by_name("META-INF/neoforge.mods.toml"){
                    if f.size()>262144{return Err("Embedded mod metadata is too large".into())}
                    let mut text=String::new();f.read_to_string(&mut text).map_err(|e|e.to_string())?;
                    let m:toml::Value=toml::from_str(&text).map_err(|e|e.to_string())?;
                    if let Some(mods)=m.get("mods").and_then(|v|v.as_array()){ids.extend(mods.iter().filter_map(|v|v.get("modId").and_then(|v|v.as_str()).map(str::to_owned)));}
                };
            }
        }
    }
    Ok(ids)
}
async fn prepare_survival_mods(rt:&Runtime,client:&reqwest::Client,game:&Path,loader:&str,version:&str)->Result<(),String>{
    let lock:Value=serde_json::from_str(include_str!("../resources/survival-mods-lock.json")).map_err(|e|e.to_string())?;
    let selected=lock["targets"].as_array().and_then(|a|a.iter().find(|v|v["loader"]==loader&&v["minecraft"]==version)).ok_or("Missing native survival dependency set")?;
    let entries=selected["mods"].as_array().ok_or("Invalid survival dependency set")?;
    prepare_survival_entries(rt,client,game,entries).await
}
async fn prepare_survival_entries(rt:&Runtime,client:&reqwest::Client,game:&Path,entries:&[Value])->Result<(),String>{
    let managed:Vec<Value>=entries.iter().filter(|m|m["providedBy"]!="performance-lock").cloned().collect();
    let old:Value=if game.join(SURVIVAL_MANIFEST).exists(){serde_json::from_slice(&fs::read(game.join(SURVIVAL_MANIFEST)).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?}else{json!([])};
    let old=old.as_array().ok_or("Invalid installed survival manifest")?;
    let mods=game.join("mods");
    let mut filenames=std::collections::HashSet::new();
    let mut ids=std::collections::HashSet::new();
    for entry in entries{
        let filename=entry["filename"].as_str().ok_or("Missing survival mod filename")?;
        if Path::new(filename).components().count()!=1||!filename.ends_with(".jar")||!filenames.insert(filename){return Err("Invalid or duplicate survival filename".into())}
        for id in entry["modIds"].as_array().ok_or("Missing survival mod IDs")?{ids.insert(id.as_str().ok_or("Invalid survival mod ID")?);}
        let path=safe_path(&mods,filename)?;
        if fs::symlink_metadata(&path).is_ok()&&!survival_digest(&path,entry)&&!old.iter().any(|o|o["filename"]==filename&&survival_digest(&path,o)){
            return Err(format!("{filename} differs from the bundled version. Your file was preserved; move it out of mods before retrying."))
        }
    }
    // Inspect IDs, not just filenames, so a renamed custom version cannot cause duplicate mods.
    if let Ok(files)=fs::read_dir(&mods){for file in files{
        let file=file.map_err(|e|e.to_string())?;let path=file.path();
        if path.extension().and_then(|v|v.to_str())!=Some("jar"){continue}
        let name=file.file_name();let name=name.to_string_lossy();
        if filenames.contains(name.as_ref()){continue}
        if old.iter().any(|o|o["filename"]==name.as_ref()&&survival_digest(&path,o)){continue}
        if let Some(conflict)=jar_mod_ids(&path)?.iter().find(|id|ids.contains(id.as_str())){
            return Err(format!("{} already provides {conflict}. Your custom mod was preserved; move the conflicting JAR out of mods before retrying.",name))
        }
    }}
    for entry in old{
        let path=safe_path(&mods,entry["filename"].as_str().ok_or("Missing installed survival filename")?)?;
        if fs::symlink_metadata(&path).is_ok()&&!survival_digest(&path,entry)&&!managed.iter().any(|m|m["filename"]==entry["filename"]&&survival_digest(&path,m)){
            return Err(format!("Managed survival mod {} was changed. Your file was preserved; move it out of mods before retrying.",path.display()))
        }
    }
    // Download and verify the whole set before changing any installed survival JAR.
    let cache=rt.root.join("runtime/survival-mods");
    for entry in &managed{
        let hash=entry["sha512"].as_str().ok_or("Missing survival checksum")?;
        if hash.len()!=128||!hash.bytes().all(|b|b.is_ascii_hexdigit()){return Err("Invalid survival checksum".into())}
        let cached=cache.join(format!("{hash}.jar"));
        phase(rt,&format!("Preparing {}",entry["slug"].as_str().unwrap_or("client feature")),88);
        if let Some(resource)=entry["resource"].as_str(){
            if Path::new(resource).components().count()!=1||!resource.ends_with(".jar"){return Err("Invalid bundled mod resource".into())}
            let source=rt.resources.join("resources").join(resource);
            if !survival_digest(&source,entry){return Err(format!("Bundled dependency checksum failed: {resource}"))}
            if !survival_digest(&cached,entry){atomic(&cached,&fs::read(source).map_err(|e|e.to_string())?)?;}
        }else{
            download(client,entry["url"].as_str().ok_or("Missing survival download URL")?,&cached,entry["sha1"].as_str()).await?;
        }
        if !survival_digest(&cached,entry){return Err(format!("Survival dependency checksum failed: {}",entry["slug"]))}
    }
    for entry in old{
        if managed.iter().any(|m|m["filename"]==entry["filename"]&&m["sha512"]==entry["sha512"]){continue}
        let name=entry["filename"].as_str().ok_or("Missing installed survival filename")?;let source=safe_path(&mods,name)?;
        if !source.exists(){continue}
        if !survival_digest(&source,entry){
            if managed.iter().any(|m|m["filename"]==name&&survival_digest(&source,m)){continue} // An interrupted update already installed the new version.
            return Err(format!("{name} changed while preparing dependencies. Your file was preserved; retry after moving it out of mods."))
        }
        let backup=safe_path(&game.join("unbox-backups/survival-mods"),name)?.with_extension(format!("{}.jar",entry["sha1"].as_str().ok_or("Missing old checksum")?));
        if backup.exists(){if !survival_digest(&backup,entry){return Err("Survival mod backup conflicts with an existing file".into())}}else{atomic(&backup,&fs::read(&source).map_err(|e|e.to_string())?)?;}
        fs::remove_file(source).map_err(|e|e.to_string())?;
    }
    for entry in &managed{
        let path=safe_path(&mods,entry["filename"].as_str().unwrap())?;
        if survival_digest(&path,entry){continue}
        // Link a fully written temporary file exclusively: a user-created destination is never replaced.
        let temporary=mods.join(format!(".survival-{}",uuid::Uuid::new_v4()));
        atomic(&temporary,&fs::read(cache.join(format!("{}.jar",entry["sha512"].as_str().unwrap()))).map_err(|e|e.to_string())?)?;
        let installed=fs::hard_link(&temporary,&path).map_err(|_|format!("Could not install {} without replacing an existing file. Your files were preserved; retry after checking mods.",entry["filename"]));
        let _=fs::remove_file(&temporary);installed?;
    }
    atomic(&game.join(SURVIVAL_MANIFEST),&serde_json::to_vec_pretty(&managed).map_err(|e|e.to_string())?)?;
    Ok(())
}
fn append_wick(lock: &mut Value, loader: &str, version: &str) -> Result<(), String> {
    let available: Value = serde_json::from_str(include_str!("../resources/wick-lock.json"))
        .map_err(|e| e.to_string())?;
    let mut item = available
        .as_array()
        .and_then(|items| {
            items.iter().find(|item| {
                item["loader"] == loader
                    && item["gameVersions"]
                        .as_array()
                        .is_some_and(|v| v.iter().any(|v| v == version))
            })
        })
        .cloned()
        .ok_or("Missing compatible dynamic lights dependency")?;
    item["slug"] = json!("wick");
    lock.as_array_mut()
        .ok_or("Invalid built-in mod lock")?
        .push(item);
    Ok(())
}
fn check_survival_shared_dependency(game:&Path,next:&Value)->Result<(),String>{
    let previous=game.join("unbox-performance-lock.json");
    let old:Value=if previous.exists(){serde_json::from_slice(&fs::read(previous).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?}else{json!([])};
    let old=old.as_array().ok_or("Invalid installed mod manifest")?;
    for entry in next.as_array().ok_or("Invalid built-in mod lock")?{
        if entry["slug"]!="fabric-api"{continue}
        let name=entry["filename"].as_str().ok_or("Missing built-in filename")?;let path=safe_path(&game.join("mods"),name)?;
        let Ok(metadata)=fs::symlink_metadata(&path)else{continue};
        let expected=metadata.is_file()&&!metadata.file_type().is_symlink()&&(verified(&path,entry["sha1"].as_str().unwrap_or(""))||old.iter().any(|o|o["filename"]==name&&verified(&path,o["sha1"].as_str().unwrap_or(""))));
        if !expected{return Err(format!("{name} differs from the managed version. Your file was preserved; move it out of mods before retrying."))}
    }
    Ok(())
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
    #[test]fn disallow_traversal(){assert!(safe_path(Path::new("/tmp/root"),"../escape.jar").is_err());assert!(safe_path(Path::new("/tmp/root"),"/absolute.jar").is_err());assert!(safe_path(Path::new("/tmp/root"),"group/mod.jar").is_ok());
        #[cfg(target_os="windows")]for path in [r"\absolute.jar",r"C:escape.jar",r"C:\absolute.jar",r"\\server\share\mod.jar"]{assert!(safe_path(Path::new("C:/root"),path).is_err(),"{path}");}
    }
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

#[cfg(test)]mod survival_tests{
    use super::*;
    use std::io::Write;
    struct Fixture{root:PathBuf,game:PathBuf,rt:Runtime}
    impl Fixture{
        fn new()->Self{
            let root=std::env::temp_dir().join(format!("unbox-survival-{}",uuid::Uuid::new_v4()));let game=root.join("game");fs::create_dir_all(game.join("mods")).unwrap();
            let rt=Runtime{root:root.clone(),resources:root.clone(),job:Default::default(),auth:Default::default(),social:Default::default()};Self{root,game,rt}
        }
        fn cached(&self,name:&str,id:&str,data:&[u8])->Value{
            let hash=format!("{:x}",sha2::Sha512::digest(data));let entry=json!({"filename":name,"slug":id,"modIds":[id],"sha1":format!("{:x}",Sha1::digest(data)),"sha512":hash,"url":"https://invalid.example/not-used.jar","providedBy":"survival-mods-lock"});
            atomic(&self.root.join(format!("runtime/survival-mods/{hash}.jar")),data).unwrap();entry
        }
        fn old(&self,entry:&Value,data:&[u8]){atomic(&self.game.join("mods").join(entry["filename"].as_str().unwrap()),data).unwrap();atomic(&self.game.join(SURVIVAL_MANIFEST),&serde_json::to_vec(&json!([entry])).unwrap()).unwrap();}
    }
    impl Drop for Fixture{fn drop(&mut self){let _=fs::remove_dir_all(&self.root);}}
    fn jar(path:&Path,metadata:&str,content:&[u8]){let mut z=zip::ZipWriter::new(fs::File::create(path).unwrap());z.start_file(metadata,zip::write::SimpleFileOptions::default()).unwrap();z.write_all(content).unwrap();z.finish().unwrap();}
    fn offline_client()->reqwest::Client{reqwest::Client::builder().no_proxy().timeout(Duration::from_millis(20)).build().unwrap()}
    #[tokio::test]async fn cached_install_and_retry_keep_personal_files_and_manifest(){
        let f=Fixture::new();let entry=f.cached("feature.jar","feature",b"pinned artifact");
        jar(&f.game.join("mods/personal.jar"),"fabric.mod.json",br#"{"id":"personal"}"#);let personal=fs::read(f.game.join("mods/personal.jar")).unwrap();
        for _ in 0..2{prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[entry.clone()]).await.unwrap();}
        assert_eq!(fs::read(f.game.join("mods/feature.jar")).unwrap(),b"pinned artifact");assert_eq!(fs::read(f.game.join("mods/personal.jar")).unwrap(),personal);
        let installed:Value=serde_json::from_slice(&fs::read(f.game.join(SURVIVAL_MANIFEST)).unwrap()).unwrap();assert_eq!(installed,json!([entry]));
        assert!(crate::managed_mod_names(&f.game).contains("feature.jar"));assert!(!crate::managed_mod_names(&f.game).contains("personal.jar"));
    }
    #[tokio::test]async fn same_name_and_renamed_native_conflicts_preserve_user_files(){
        for (name,metadata,content) in [("feature.jar","fabric.mod.json",br#"{"id":"feature"}"#.as_slice()),("renamed.jar","fabric.mod.json",br#"{"id":"other","provides":["feature"]}"#.as_slice()),("renamed.jar","META-INF/neoforge.mods.toml",b"[[mods]]\nmodId='feature'\n".as_slice())]{
            let f=Fixture::new();let entry=f.cached("feature.jar","feature",b"pinned artifact");let path=f.game.join("mods").join(name);jar(&path,metadata,content);let original=fs::read(&path).unwrap();
            assert!(prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[entry]).await.unwrap_err().contains("preserved"));assert_eq!(fs::read(&path).unwrap(),original);assert!(!f.game.join(SURVIVAL_MANIFEST).exists());
        }
    }
    #[tokio::test]async fn renamed_jar_in_jar_dependency_is_detected_without_changing_user_files(){
        let f=Fixture::new();let embedded=f.root.join("embedded.jar");jar(&embedded,"META-INF/neoforge.mods.toml",b"[[mods]]\nmodId='kotlinforforge'\n");
        let path=f.game.join("mods/renamed-language.jar");
        let mut z=zip::ZipWriter::new(fs::File::create(&path).unwrap());
        z.start_file("META-INF/jarjar/metadata.json",zip::write::SimpleFileOptions::default()).unwrap();
        z.write_all(br#"{"jars":[{"path":"META-INF/jarjar/language.jar"}]}"#).unwrap();
        z.start_file("META-INF/jarjar/language.jar",zip::write::SimpleFileOptions::default()).unwrap();z.write_all(&fs::read(embedded).unwrap()).unwrap();z.finish().unwrap();
        let original=fs::read(&path).unwrap();assert_eq!(jar_mod_ids(&path).unwrap(),vec!["kotlinforforge"]);
        let entry=f.cached("kff.jar","kotlinforforge",b"pinned language");
        assert!(prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[entry]).await.unwrap_err().contains("preserved"));assert_eq!(fs::read(path).unwrap(),original);
    }
    #[tokio::test]async fn upgrade_backs_up_old_mod_and_refuses_changed_managed_file(){
        let f=Fixture::new();let old=f.cached("old.jar","feature",b"old version");f.old(&old,b"old version");let next=f.cached("new.jar","feature",b"new version");
        prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[next.clone()]).await.unwrap();assert!(!f.game.join("mods/old.jar").exists());
        let backup=f.game.join("unbox-backups/survival-mods").join(format!("old.{}.jar",old["sha1"].as_str().unwrap()));assert_eq!(fs::read(backup).unwrap(),b"old version");
        fs::write(f.game.join("mods/new.jar"),b"user edit").unwrap();assert!(prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[next]).await.is_err());assert_eq!(fs::read(f.game.join("mods/new.jar")).unwrap(),b"user edit");
    }
    #[tokio::test]async fn removed_managed_mods_are_backed_up_and_custom_conflicts_preserved(){
        let f=Fixture::new();let old=f.cached("veinminer.jar","veinminer",b"old version");f.old(&old,b"old version");
        prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[]).await.unwrap();
        assert!(!f.game.join("mods/veinminer.jar").exists());
        let backup=f.game.join("unbox-backups/survival-mods").join(format!("veinminer.{}.jar",old["sha1"].as_str().unwrap()));assert!(survival_digest(&backup,&old));
        let path=f.game.join("mods/user-renamed.jar");jar(&path,"META-INF/neoforge.mods.toml",b"[[mods]]\nmodId='veinminer'\n");let original=fs::read(&path).unwrap();
        let next=f.cached("veinminer.jar","veinminer",b"new native port");
        assert!(prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[next]).await.unwrap_err().contains("preserved"));assert_eq!(fs::read(path).unwrap(),original);
    }
    #[tokio::test]async fn bundled_ports_are_verified_before_installation(){
        let f=Fixture::new();let mut entry=f.cached("native.jar","native",b"native port");entry["resource"]=json!("native.jar");
        atomic(&f.root.join("resources/native.jar"),b"native port").unwrap();
        prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[entry.clone()]).await.unwrap();assert_eq!(fs::read(f.game.join("mods/native.jar")).unwrap(),b"native port");
        fs::write(f.root.join("resources/native.jar"),b"corrupt").unwrap();
        assert!(prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[entry.clone()]).await.unwrap_err().contains("checksum"));
        entry["resource"]=json!("../native.jar");assert!(prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[entry]).await.unwrap_err().contains("Invalid bundled"));
    }
    #[tokio::test]async fn sha512_mismatch_stops_install_before_existing_mods_change(){
        let f=Fixture::new();let old=f.cached("old.jar","feature",b"old version");f.old(&old,b"old version");let mut next=f.cached("new.jar","feature",b"new version");
        let wrong="0".repeat(128);next["sha512"]=json!(wrong);atomic(&f.root.join(format!("runtime/survival-mods/{wrong}.jar")),b"new version").unwrap();
        assert!(prepare_survival_entries(&f.rt,&offline_client(),&f.game,&[next]).await.unwrap_err().contains("checksum"));assert_eq!(fs::read(f.game.join("mods/old.jar")).unwrap(),b"old version");assert!(!f.game.join("mods/new.jar").exists());
    }
    #[test]fn pinned_targets_have_complete_native_dependencies_and_share_the_fabric_api_pin(){
        let lock:Value=serde_json::from_str(include_str!("../resources/survival-mods-lock.json")).unwrap();let targets=lock["targets"].as_array().unwrap();assert_eq!(targets.len(),crate::versions::SUPPORTED.len()*2);
        for loader in ["fabric","neoforge"]{for version in crate::versions::SUPPORTED{
            assert_eq!(targets.iter().filter(|t|t["loader"]==loader&&t["minecraft"]==*version).count(),1);
            let performance=performance_lock(loader,version).unwrap();let mods=performance.as_array().unwrap();assert!(!mods.is_empty());
            if *version=="26.3"{assert!(mods.iter().all(|m|m["gameVersions"].as_array().unwrap().contains(&json!(version))));}
        }}
        assert!(performance_lock("fabric","26.2").is_err());assert!(performance_lock("forge","26.3").is_err());
        for target in targets{
            let entries=target["mods"].as_array().unwrap();let mut names=std::collections::HashSet::new();let mut ids=std::collections::HashSet::new();
            for e in entries{assert!(names.insert(e["filename"].as_str().unwrap()));assert_eq!(e["loader"],target["loader"]);assert!(e["gameVersions"].as_array().unwrap().contains(&target["minecraft"]));for id in e["modIds"].as_array().unwrap(){assert!(ids.insert(id.as_str().unwrap()));}
                for dependency in e["dependencies"].as_array().unwrap().iter().filter(|d|d["type"]=="required"){assert!(entries.iter().any(|d|d["projectId"]==dependency["projectId"]),"missing dependency for {}: {}",e["slug"],dependency);}
            }
            if target["loader"]=="fabric"{let performance:Value=performance_lock("fabric",target["minecraft"].as_str().unwrap()).unwrap();let api=entries.iter().find(|e|e["slug"]=="fabric-api").unwrap();let existing=performance.as_array().unwrap().iter().find(|e|e["slug"]=="fabric-api").unwrap();assert_eq!(api["sha512"],existing["sha512"]);}
            for id in ["inventoryprofilesnext","libipn","veinminer","veinminer_client","mousetweaks","easyshulkerboxes","chiseledbookshelfvisualizer","resourcify","effortlessbuilding","colourscroller"]{assert!(ids.contains(id),"missing {id}: {} {}",target["loader"],target["minecraft"]);}
            assert!(ids.contains(if target["loader"]=="fabric"{"status-effect-bars"}else{"status_effect_bars"}));
            assert!(ids.contains(if target["loader"]=="fabric"{"spatial-gui"}else{"spatial_gui"}));
            assert!(target["unavailableFeatures"].as_array().is_none_or(|a|a.is_empty()));
        }
    }
    #[test]fn shared_fabric_api_preflight_preserves_custom_but_accepts_known_upgrade(){
        let f=Fixture::new();let old=f.cached("fabric-api.jar","fabric-api",b"old API");let next=f.cached("fabric-api.jar","fabric-api",b"new API");fs::write(f.game.join("mods/fabric-api.jar"),b"old API").unwrap();
        assert!(check_survival_shared_dependency(&f.game,&json!([next])).is_err());assert_eq!(fs::read(f.game.join("mods/fabric-api.jar")).unwrap(),b"old API");atomic(&f.game.join("unbox-performance-lock.json"),&serde_json::to_vec(&json!([old])).unwrap()).unwrap();assert!(check_survival_shared_dependency(&f.game,&json!([next])).is_ok());
    }
}
