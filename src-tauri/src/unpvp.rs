//! Independent Forge 1.8.9 client, HUD and pinned performance pack. No expvp runtime.
use crate::{Runtime,atomic,launcher::{download,phase,safe_path}};
use serde_json::{Value,json};
use sha2::{Digest,Sha256};
use std::{fs,path::{Path,PathBuf},time::Duration};
use tauri::Manager;
pub fn clean_test(profile:&Value)->bool{profile["cleanTest"]==true&&profile["version"]=="1.8.9"&&profile["loader"]=="forge"}
pub fn clean_active(rt:&Runtime)->bool{let job=rt.job.lock().unwrap();job["busy"]==true&&job["cleanTest"]==true}
fn text<'a>(v:&'a Value,k:&str)->Result<&'a str,String>{v[k].as_str().ok_or_else(||format!("Missing UI runtime field: {k}"))}
fn copy_changed(source:&Path,target:&Path)->Result<(),String>{let bytes=fs::read(source).map_err(|e|format!("{}: {e}",source.display()))?;if fs::read(target).ok().as_deref()!=Some(bytes.as_slice()){atomic(target,&bytes)?;}Ok(())}
fn default_file(path:&Path,bytes:&[u8])->Result<(),String>{if !path.exists(){atomic(path,bytes)?;}Ok(())}
fn file_stamp(path:&Path)->Option<Value>{let m=fs::metadata(path).ok()?;if !m.is_file(){return None}let mut stamp=json!({"bytes":m.len(),"modified":m.modified().ok()?.duration_since(std::time::UNIX_EPOCH).ok()?.as_nanos().to_string()});
    #[cfg(unix)]{use std::os::unix::fs::MetadataExt;stamp["inode"]=json!(m.ino());stamp["changed"]=json!([m.ctime(),m.ctime_nsec()]);}
    #[cfg(not(unix))]{stamp["created"]=json!(m.created().ok().and_then(|t|t.duration_since(std::time::UNIX_EPOCH).ok()).map(|t|t.as_nanos().to_string()));}
    Some(stamp)
}
// Only reuse successful hash checks/copies while both the expected content and file stamps match.
struct RuntimeCache{path:PathBuf,records:std::sync::Mutex<serde_json::Map<String,Value>>}
impl RuntimeCache{
    fn open(shared:&Path)->Self{let path=shared.join("verified-runtime.json");let records=fs::read(&path).ok().and_then(|b|serde_json::from_slice::<Value>(&b).ok()).and_then(|v|v.as_object().cloned()).unwrap_or_default();Self{path,records:std::sync::Mutex::new(records)}}
    fn matches(&self,key:&str,value:&Value)->bool{self.records.lock().unwrap().get(key)==Some(value)}
    fn record(&self,key:String,value:Value){self.records.lock().unwrap().insert(key,value);}
    async fn download(&self,client:&reqwest::Client,url:&str,path:&Path,hash:&str)->Result<(),String>{
        let key=format!("sha1:{}",path.display());
        if let Some(stamp)=file_stamp(path){if self.matches(&key,&json!({"hash":hash,"stamp":stamp})){return Ok(())}}
        download(client,url,path,Some(hash)).await?;
        self.record(key,json!({"hash":hash,"stamp":file_stamp(path).ok_or("Verified runtime file disappeared")?}));Ok(())
    }
    fn copy(&self,source:&Path,target:&Path)->Result<(),String>{
        let key=format!("copy:{}",target.display());
        if let (Some(src),Some(dst))=(file_stamp(source),file_stamp(target)){if self.matches(&key,&json!({"source":source,"src":src,"dst":dst})){return Ok(())}}
        copy_changed(source,target)?;
        self.record(key,json!({"source":source,"src":file_stamp(source).ok_or("Source file disappeared")?,"dst":file_stamp(target).ok_or("Copied file disappeared")?}));Ok(())
    }
    fn save(&self)->Result<(),String>{let bytes=serde_json::to_vec(&*self.records.lock().unwrap()).map_err(|e|e.to_string())?;if fs::read(&self.path).ok().as_deref()!=Some(&bytes){atomic(&self.path,&bytes)?;}Ok(())}
}
fn optifine_link(page:&str)->Result<String,String>{
    // Only accept the official site's relative download endpoint. The body is hash checked below.
    for quote in ['\'', '"']{let marker=format!("href={quote}downloadx?");if let Some(rest)=page.split_once(&marker).map(|p|p.1){if let Some(query)=rest.split(quote).next(){return Ok(format!("https://optifine.net/downloadx?{}",query.replace("&amp;","&")))}}}
    Err("The official OptiFine download link is unavailable. Retry later.".into())
}
async fn performance_pack(client:&reqwest::Client,rt:&Runtime,shared:&Path,game:&Path,receipts:&RuntimeCache,clean:bool)->Result<(),String>{
    use futures_util::{stream,StreamExt,TryStreamExt};
    let lock:Value=serde_json::from_str(include_str!("../resources/unpvp-performance.json")).map_err(|e|e.to_string())?;
    let entries=lock["mods"].as_array().ok_or("Missing performance manifest")?.clone();
    let cache=shared.join("performance");let mods=game.join("mods");
    stream::iter(entries.clone().into_iter().map(|entry|{let cache=&cache;let mods=&mods;async move{
        let name=text(&entry,"path")?;let path=safe_path(cache,name)?;let hash=text(&entry,"sha1")?;
        let key=format!("sha1:{}",path.display());
        let valid=file_stamp(&path).is_some_and(|stamp|receipts.matches(&key,&json!({"hash":hash,"stamp":stamp})))||fs::read(&path).is_ok_and(|b|format!("{:x}",sha1::Sha1::digest(b))==hash);
        if !valid{
            let url=if let Some(url)=entry["url"].as_str(){url.to_owned()}else{
                let page=client.get(text(&entry,"downloadPage")?).send().await.map_err(|e|format!("OptiFine: {e}"))?.error_for_status().map_err(|e|e.to_string())?.text().await.map_err(|e|e.to_string())?;optifine_link(&page)?
            };download(client,&url,&path,Some(hash)).await?;
        }
        receipts.record(key,json!({"hash":hash,"stamp":file_stamp(&path).ok_or("Performance file disappeared")?}));
        receipts.copy(&path,&safe_path(mods,name)?)
    }})).buffer_unordered(4).try_collect::<Vec<_>>().await?;
    receipts.copy(&rt.resources.join("resources/unpvp-patcher-performance.jar"),&mods.join("unpvp-patcher-performance.jar"))?;
    if !clean {receipts.copy(&rt.resources.join("resources/unpvp-pvp.jar"),&mods.join("unpvp-pvp.jar"))?;
    receipts.copy(&rt.resources.join("resources/unpvp-network.jar"),&mods.join("unpvp-network.jar"))?;}
    let mut allowed:std::collections::HashSet<String>=entries.iter().filter_map(|e|e["path"].as_str().map(str::to_owned)).collect();allowed.insert("unpvp-patcher-performance.jar".into());if clean{allowed.insert("unbox-clean-test.jar".into());}else{allowed.extend(["unpvp.jar".into(),"unpvp-pvp.jar".into(),"unpvp-network.jar".into()]);}
    for entry in fs::read_dir(&mods).map_err(|e|e.to_string())?{let p=entry.map_err(|e|e.to_string())?.path();if p.extension().is_some_and(|e|e=="jar")&&!allowed.contains(p.file_name().unwrap().to_string_lossy().as_ref()){return Err(format!("Unmanaged mod {}. Move it outside this profile’s mods folder to use the verified performance pack.",p.file_name().unwrap().to_string_lossy()))}}
    default_file(&game.join("config/splash.properties"),b"enabled=false\n")?;
    default_file(&game.join("config/renderlib.cfg"),b"general {\n B:mainMenuFPSSynced=true\n}\n")?;
    default_file(&game.join("optionsof.txt"),b"ofFastRender:false\nofFastMath:true\nofSmoothFps:false\nofSmoothWorld:false\nofRenderRegions:false\nofDynamicUpdates:false\nofChunkUpdates:1\nofLazyChunkLoading:true\nofAaLevel:0\nofAfLevel:1\n")?;
    Ok(())
}
fn native_once(archive:&Path,dest:&Path,hash:&str)->Result<(),String>{
    let stamp=dest.join(format!(".unpacked-{hash}.json"));
    if let Ok(bytes)=fs::read(&stamp){if let Ok(names)=serde_json::from_slice::<Vec<String>>(&bytes){if !names.is_empty()&&names.iter().all(|n|safe_path(dest,n).is_ok_and(|p|p.is_file())){return Ok(())}}}
    unzip(archive,dest,true)?;
    let mut zip=zip::ZipArchive::new(fs::File::open(archive).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;let mut names=Vec::new();
    for i in 0..zip.len(){let f=zip.by_index(i).map_err(|e|e.to_string())?;if matches!(Path::new(f.name()).extension().and_then(|s|s.to_str()),Some("dll"|"so"|"dylib"|"jnilib")){names.push(f.name().to_owned());}}
    atomic(&stamp,&serde_json::to_vec(&names).map_err(|e|e.to_string())?)
}
fn unzip(path:&Path,dest:&Path,natives:bool)->Result<(),String>{
    let mut zip=zip::ZipArchive::new(fs::File::open(path).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    for i in 0..zip.len(){let mut f=zip.by_index(i).map_err(|e|e.to_string())?;let Some(name)=f.enclosed_name() else{return Err("Unsafe runtime archive entry".into())};
        if f.is_dir()||name.starts_with("META-INF"){continue}
        if natives&&!matches!(name.extension().and_then(|s|s.to_str()),Some("dll"|"so"|"dylib"|"jnilib")){continue}
        let out=dest.join(name);fs::create_dir_all(out.parent().ok_or("Invalid runtime path")?).map_err(|e|e.to_string())?;let mut target=fs::File::create(out).map_err(|e|e.to_string())?;std::io::copy(&mut f,&mut target).map_err(|e|e.to_string())?;
    }Ok(())
}
fn find_java(root:&Path,depth:u8)->Option<PathBuf>{if depth==0{return None}let candidate=root.join(if cfg!(windows){"bin/java.exe"}else{"bin/java"});if candidate.is_file(){return Some(candidate)}for e in fs::read_dir(root).ok()?.flatten(){if e.file_type().ok()?.is_dir(){if let Some(p)=find_java(&e.path(),depth-1){return Some(p)}}}None}
pub(crate) async fn java(client:&reqwest::Client,shared:&Path,entry:&Value)->Result<PathBuf,String>{
    let home=shared.join(entry["directory"].as_str().unwrap_or("java8"));if let Some(p)=find_java(&home,6){return Ok(p)}
    let archive=safe_path(shared,text(entry,"name")?)?;let expected=text(entry,"sha256")?;
    if !fs::read(&archive).is_ok_and(|b|format!("{:x}",Sha256::digest(b))==expected){download(client,text(entry,"url")?,&archive,None).await?;}
    if format!("{:x}",Sha256::digest(fs::read(&archive).map_err(|e|e.to_string())?))!=expected{return Err("Java 8 checksum mismatch".into())}
    fs::create_dir_all(&home).map_err(|e|e.to_string())?;
    if cfg!(windows){unzip(&archive,&home,false)?;}else{let s=crate::process::async_command("tar").arg("-xzf").arg(&archive).arg("-C").arg(&home).status().await.map_err(|e|e.to_string())?;if !s.success(){return Err("Java 8 extraction failed".into())}}
    find_java(&home,6).ok_or("Java 8 executable missing".into())
}
async fn artifact(client:&reqwest::Client,root:&Path,v:&Value,receipts:&RuntimeCache)->Result<PathBuf,String>{let p=safe_path(root,text(v,"path")?)?;receipts.download(client,text(v,"url")?,&p,text(v,"sha1")?).await?;Ok(p)}

pub async fn run(rt:&Runtime,profile:&Value,username:&str,memory:u32,prepare_only:bool,verification:bool,app:Option<&tauri::AppHandle>)->Result<(),String>{
    let preparing=std::time::Instant::now();
    let clean=clean_test(profile);rt.job.lock().unwrap()["cleanTest"]=json!(clean);
    if profile["version"]!="1.8.9"||profile["loader"]!="forge"{return Err("unpvp requires Forge 1.8.9".into())}
    if cfg!(all(target_arch="aarch64",not(target_os="macos"))){return Err("The 1.8.9 preview currently requires x64 on Windows/Linux".into())}
    let lock:Value=serde_json::from_str(include_str!("../resources/unpvp-runtime.json")).map_err(|e|e.to_string())?;
    let apple=cfg!(all(target_os="macos",target_arch="aarch64"));
    let arm:Value=serde_json::from_str(include_str!("../resources/unpvp-apple-silicon.json")).map_err(|e|e.to_string())?;
    let meta=&lock["minecraft"];let os=if cfg!(target_os="macos"){"osx"}else if cfg!(windows){"windows"}else{"linux"};let java_os=if os=="osx"{"mac"}else{os};
    let (session,local)=if prepare_only||(verification&&cfg!(debug_assertions)){(None,username.to_owned())}else{crate::auth::launch_account(rt).await?};
    // Keep existing unpvp profiles and worlds isolated from expvp.
    let game=crate::game_dir(rt,profile)?;let shared=rt.root.join("runtime/unpvp");
    for p in [game.join("mods"),game.join("logs"),shared.clone()]{fs::create_dir_all(p).map_err(|e|e.to_string())?;}
    let receipts=RuntimeCache::open(&shared);
    let client=reqwest::Client::builder().no_proxy().connect_timeout(Duration::from_secs(15)).timeout(Duration::from_secs(180)).build().map_err(|e|e.to_string())?;
    phase(rt,"Preparing unpvp · Java 8",3);let java=java(&client,&shared,if apple{&arm["java"]}else{&lock["java"][java_os]}).await?;
    phase(rt,"Preparing Minecraft 1.8.9",9);let jar=shared.join("minecraft.jar");receipts.download(&client,text(&meta["downloads"]["client"],"url")?,&jar,text(&meta["downloads"]["client"],"sha1")?).await?;
    let libs=shared.join("libraries");let natives=shared.join(if apple{text(&arm,"nativeDirectory")?.to_owned()}else{format!("natives-{os}-x64")});fs::create_dir_all(&natives).map_err(|e|e.to_string())?;let mut cp=Vec::new();
    use futures_util::{stream,StreamExt,TryStreamExt};
    let mut libraries=Vec::new();
    for lib in meta["libraries"].as_array().ok_or("Missing Minecraft libraries")?{
        if !crate::launcher::allowed(lib){continue}
        if apple&&lib["name"].as_str().is_some_and(|n|n.starts_with("org.lwjgl:" )||n.starts_with("org.lwjgl.lwjgl:")){continue}
        if lib["downloads"]["artifact"].is_object(){libraries.push((lib["downloads"]["artifact"].clone(),false));}
        if let Some(classifier)=lib["natives"][os].as_str().filter(|_|!apple){let classifier=classifier.replace("${arch}","64");libraries.push((lib["downloads"]["classifiers"][&classifier].clone(),true));}
    }
    if apple{for lib in arm["libraries"].as_array().ok_or("Missing Apple Silicon libraries")?{libraries.push((lib.clone(),false));}libraries.push((arm["natives"].clone(),true));}
    for lib in lock["forge"]["libraries"].as_array().ok_or("Missing Forge libraries")?{libraries.push((lib.clone(),false));}
    let prepared=stream::iter(libraries.into_iter().map(|(entry,native)|{let client=&client;let libs=&libs;let receipts=&receipts;async move{let path=artifact(client,libs,&entry,receipts).await?;Ok::<_,String>((path,native,text(&entry,"sha1")?.to_owned()))}})).buffered(8).try_collect::<Vec<_>>().await?;
    for (path,native,hash) in prepared{if native{native_once(&path,&natives,&hash)?;}else{cp.push(path);}}cp.push(jar);
    phase(rt,"Preparing game assets",25);let assets=rt.root.join("runtime/assets");let index_id=text(&meta["assetIndex"],"id")?;let index=assets.join(format!("indexes/{index_id}.json"));receipts.download(&client,text(&meta["assetIndex"],"url")?,&index,text(&meta["assetIndex"],"sha1")?).await?;
    let data:Value=serde_json::from_slice(&fs::read(index).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;let hashes:Vec<String>=data["objects"].as_object().ok_or("Invalid asset index")?.values().map(|v|text(v,"hash").map(str::to_owned)).collect::<Result<_,_>>()?;
    // Receipts are created only after SHA-1 verification. Missing or modified files are rechecked.
    let receipt=shared.join("verified-assets.json");let prior:Value=fs::read(&receipt).ok().and_then(|b|serde_json::from_slice(&b).ok()).unwrap_or(json!({}));
    let hashes:std::collections::BTreeSet<_>=hashes.into_iter().collect();
    let checked=stream::iter(hashes.into_iter().map(|h|{let client=&client;let assets=&assets;let prior=&prior;async move{if h.len()!=40||!h.bytes().all(|b|b.is_ascii_hexdigit()){return Err("Invalid asset hash".into())}let rel=format!("{}/{}",&h[..2],h);let path=assets.join("objects").join(&rel);let stamp=file_stamp(&path);
        if stamp.is_none()||stamp.as_ref()!=prior.get(&h){download(client,&format!("https://resources.download.minecraft.net/{rel}"),&path,Some(&h)).await?;}
        Ok::<_,String>((h,file_stamp(&path).ok_or("Verified asset disappeared")?))
    }})).buffer_unordered(12).try_collect::<Vec<_>>().await?;
    let receipt_data=Value::Object(checked.into_iter().collect());if receipt_data!=prior{atomic(&receipt,&serde_json::to_vec(&receipt_data).map_err(|e|e.to_string())?)?;}
    phase(rt,if clean{"Preparing Clean Test"}else{"Preparing Unbox vector UI"},85);
    let (source,target)=if clean{("resources/unbox-clean-test-1.8.9.jar","mods/unbox-clean-test.jar")}else{("resources/unpvp-forge-1.8.9.jar","mods/unpvp.jar")};receipts.copy(&rt.resources.join(source),&game.join(target))?;
    phase(rt,"Preparing six performance components",90);performance_pack(&client,rt,&shared,&game,&receipts,clean).await?;
    let logging=&meta["logging"]["client"]["file"];let logcfg=shared.join(text(logging,"id")?);receipts.download(&client,text(logging,"url")?,&logcfg,text(logging,"sha1")?).await?;
    let options=game.join("options.txt");if verification&&cfg!(debug_assertions){atomic(&options,b"guiScale:2\nmaxFps:120\nenableVsync:false\npauseOnLostFocus:false\nsoundCategory_master:0.0\nrenderDistance:6\n")?;}else if !options.exists(){atomic(&options,b"guiScale:2\nmaxFps:260\nenableVsync:false\nrenderDistance:8\nuseVbo:true\nmipmapLevels:4\n")?;}
    if !verification&&!game.join("config/unpvp-performance-v1").exists(){
        let current=fs::read_to_string(&options).map_err(|e|e.to_string())?;let mut lines:Vec<&str>=current.lines().filter(|l|!l.starts_with("maxFps:")&&!l.starts_with("enableVsync:")).collect();lines.extend(["maxFps:260","enableVsync:false"]);atomic(&options,format!("{}\n",lines.join("\n")).as_bytes())?;atomic(&game.join("config/unpvp-performance-v1"),b"1\n")?;
    }
    atomic(&game.join("unbox-ready.json"),&serde_json::to_vec(&json!({"version":"1.8.9","stage":if clean{"Clean Test"}else{"Vector UI + PvP"},"gameplay":true})).unwrap())?;
    receipts.save()?;
    if prepare_only{return Ok(())}
    let microsoft=session.as_ref().is_some_and(|s|s.token!="0");let player=session.as_ref().map(|s|s.name.as_str()).unwrap_or(&local);let uuid=session.as_ref().map(|s|s.uuid.clone()).unwrap_or_else(||crate::launcher::md5_offline(player));
    let feedback_session=uuid::Uuid::new_v4().to_string();if !clean{crate::feedback::prepare(&game)?;}let feedback_account=if verification||clean{None}else{crate::social::feedback_account(&uuid,microsoft).ok()};
    let cp=std::env::join_paths(cp).map_err(|e|e.to_string())?;
    let mut command=crate::process::async_command(java);if verification&&!clean&&cfg!(debug_assertions){command.args(["-Dunpvp.test=true","-Dunpvp.test.world=true"]);}if !clean{command.arg(format!("-Dunbox.feedbackSession={feedback_session}")).arg(format!("-Dunbox.clientVersion={}",env!("CARGO_PKG_VERSION")));}command.current_dir(&game).args([format!("-Xmx{}M",memory.clamp(512,8192)),"-Xms256M".into(),"-Dfml.disableVersionCheck=true".into(),format!("-Djava.library.path={}",natives.display()),format!("-Dlog4j.configurationFile={}",logcfg.display()),format!("-Dunbox.accountKind={}",if microsoft{"microsoft"}else{"local"})]).arg("-cp").arg(cp).arg(text(&lock["forge"],"mainClass")?).args(["--tweakClass","net.minecraftforge.fml.common.launcher.FMLTweaker","--username",player,"--uuid",&uuid,"--accessToken",session.as_ref().map(|s|s.token.as_str()).unwrap_or("0"),"--userType",if microsoft{"mojang"}else{"legacy"},"--version","1.8.9","--assetIndex",index_id,"--width","1280","--height","800"]).arg("--gameDir").arg(&game).arg("--assetsDir").arg(&assets);
    println!("[unpvp-startup] preparation-ms={}",preparing.elapsed().as_millis());
    let log=fs::File::create(game.join("logs/unbox-launch.log")).map_err(|e|e.to_string())?;let mut child=command.stdout(log.try_clone().map_err(|e|e.to_string())?).stderr(log).spawn().map_err(|e|format!("Cannot start the prepared Java 8 runtime: {e}. See the profile launch log."))?;phase(rt,"Playing",100);
    let minimize=fs::read(rt.root.join("state.json")).ok().and_then(|b|serde_json::from_slice::<Value>(&b).ok()).is_none_or(|s|s["settings"]["minimize"]!=false);if minimize{if let Some(w)=app.and_then(|a|a.get_webview_window("main")){let _=w.minimize();}}
    let result=child.wait().await;if !clean{crate::feedback::on_exit(rt,&game,&feedback_session,&uuid,feedback_account.as_ref()).await;} if minimize{if let Some(w)=app.and_then(|a|a.get_webview_window("main")){let _=w.unminimize();}}
    if !result.map_err(|e|e.to_string())?.success(){return Err("Minecraft 1.8.9 exited unexpectedly. See the profile game folder → logs/unbox-launch.log".into())}Ok(())
}

#[cfg(test)]mod tests {
    use super::*;
    #[test] fn clean_profile_is_opt_in_and_isolated(){
        let root=std::env::temp_dir().join("unbox-clean-isolation");
        let rt=Runtime{root:root.clone(),resources:PathBuf::new(),job:Default::default(),auth:Default::default(),social:Default::default()};
        let mut profile=json!({"id":"00000000-0000-4000-8000-000000000189","version":"1.8.9","loader":"forge"});
        assert!(!clean_test(&profile));assert!(crate::game_dir(&rt,&profile).unwrap().ends_with("unpvp-ui"));
        profile["cleanTest"]=json!(true);assert!(clean_test(&profile));assert!(crate::game_dir(&rt,&profile).unwrap().ends_with("clean-test"));
        profile["version"]=json!("26.3");assert!(!clean_test(&profile));
        *rt.job.lock().unwrap()=json!({"busy":true,"cleanTest":true});assert!(clean_active(&rt));
        rt.job.lock().unwrap()["busy"]=json!(false);assert!(!clean_active(&rt));
    }
    #[tokio::test]
    #[ignore = "Prepares or opens an isolated Clean Test fixture with the actual launcher"]
    async fn clean_launcher_fixture(){
        let root=PathBuf::from(std::env::var("UNPVP_TEST_ROOT").expect("Set isolated test root"));
        assert!(root.is_absolute()&&root.to_string_lossy().contains("/.cache/unpvp-"));
        let rt=Runtime{root:root.clone(),resources:PathBuf::from(env!("CARGO_MANIFEST_DIR")),job:Default::default(),auth:Default::default(),social:Default::default()};
        let profile=json!({"id":"00000000-0000-4000-8000-000000000190","version":"1.8.9","loader":"forge","cleanTest":true});
        let launch=std::env::var_os("CLEAN_TEST_LAUNCH").is_some();
        run(&rt,&profile,"CleanPreview",1536,!launch,true,None).await.unwrap();
        let game=crate::game_dir(&rt,&profile).unwrap();
        let jars:Vec<_>=fs::read_dir(game.join("mods")).unwrap().flatten().map(|f|f.file_name().to_string_lossy().into_owned()).filter(|s|s.ends_with(".jar")).collect();
        assert_eq!(jars.len(),9);assert!(jars.iter().any(|s|s=="unbox-clean-test.jar"));assert!(jars.iter().any(|s|s=="unpvp-patcher-performance.jar"));
        for absent in ["unpvp.jar","unpvp-pvp.jar","unpvp-network.jar"]{assert!(!jars.iter().any(|s|s==absent));}
        assert!(!game.join("unbox-feedback").exists());
        if launch{let log=fs::read_to_string(game.join("logs/unbox-launch.log")).unwrap();assert!(log.contains("[clean-test] Auto Sprint and Fullbright enabled."));}
    }
    #[tokio::test] async fn runtime_receipts_revalidate_changes(){
        let root=std::env::temp_dir().join(format!("unpvp-receipts-{}",std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_nanos()));fs::create_dir_all(&root).unwrap();
        let source=root.join("source.jar");let target=root.join("mods/target.jar");fs::write(&source,b"original").unwrap();
        let hash=format!("{:x}",sha1::Sha1::digest(b"original"));let client=reqwest::Client::new();let cache=RuntimeCache::open(&root);
        // A verified local file needs no URL/network access; changed files must not use that receipt.
        cache.download(&client,"not a URL",&source,&hash).await.unwrap();cache.copy(&source,&target).unwrap();cache.save().unwrap();
        let cache=RuntimeCache::open(&root);cache.download(&client,"not a URL",&source,&hash).await.unwrap();
        assert!(cache.download(&client,"not a URL",&source,&"0".repeat(40)).await.is_err());
        fs::write(&target,b"corrupt").unwrap();cache.copy(&source,&target).unwrap();assert_eq!(fs::read(&target).unwrap(),b"original");
        fs::write(&source,b"updated source").unwrap();assert!(cache.download(&client,"not a URL",&source,&hash).await.is_err());
        cache.copy(&source,&target).unwrap();assert_eq!(fs::read(&target).unwrap(),b"updated source");
        fs::remove_file(&target).unwrap();cache.copy(&source,&target).unwrap();assert_eq!(fs::read(&target).unwrap(),b"updated source");
        fs::write(root.join("verified-runtime.json"),b"broken JSON").unwrap();assert!(RuntimeCache::open(&root).records.lock().unwrap().is_empty());
        fs::remove_dir_all(root).unwrap();
    }
    #[test] fn apple_silicon_runtime_is_pinned_and_isolated(){
        let arm:Value=serde_json::from_str(include_str!("../resources/unpvp-apple-silicon.json")).unwrap();
        assert!(text(&arm["java"],"directory").unwrap().contains("arm64"));
        assert!(text(&arm["java"],"url").unwrap().contains("aarch64"));
        assert_eq!(text(&arm["java"],"sha256").unwrap().len(),64);
        assert!(text(&arm,"nativeDirectory").unwrap().contains("arm64"));
        assert_eq!(arm["libraries"].as_array().unwrap().len(),2);
        for lib in arm["libraries"].as_array().unwrap().iter().chain(std::iter::once(&arm["natives"])){assert_eq!(text(lib,"sha1").unwrap().len(),40);assert!(safe_path(Path::new("cache"),text(lib,"path").unwrap()).is_ok());}
    }
    #[test] fn official_optifine_link(){
        assert_eq!(optifine_link("<a href='downloadx?f=OptiFine.jar&amp;x=abc'>Download</a>").unwrap(),"https://optifine.net/downloadx?f=OptiFine.jar&x=abc");
        assert!(optifine_link("<a href='https://other.invalid/downloadx?x=abc'>Download</a>").is_err());
    }
    #[test] fn performance_manifest_is_pinned(){
        let lock:Value=serde_json::from_str(include_str!("../resources/unpvp-performance.json")).unwrap();
        let entries=lock["mods"].as_array().unwrap();assert_eq!(entries.len(),7); // Five original mods + two required libraries; Patcher is local.
        let mut names=std::collections::HashSet::new();for e in entries{let name=text(e,"path").unwrap();assert!(names.insert(name));assert!(safe_path(Path::new("cache"),name).is_ok());assert_eq!(text(e,"sha1").unwrap().len(),40);assert_eq!(text(e,"sha512").unwrap().len(),128);}
    }
    #[tokio::test]
    #[ignore = "Prepares an isolated cached runtime twice to measure warm launch preparation"]
    async fn launcher_prepare_cache(){
        let root=PathBuf::from(std::env::var("UNPVP_TEST_ROOT").expect("Set an isolated .cache test root"));
        assert!(root.is_absolute()&&root.to_string_lossy().contains("/.cache/unpvp-"));
        let rt=Runtime{root:root.clone(),resources:PathBuf::from(env!("CARGO_MANIFEST_DIR")),job:Default::default(),auth:Default::default(),social:Default::default()};
        let profile=json!({"id":"00000000-0000-4000-8000-000000000189","version":"1.8.9","loader":"forge"});
        for pass in 1..=2{let start=std::time::Instant::now();run(&rt,&profile,"UIPreview",1536,true,false,None).await.unwrap();println!("[unpvp-preparation-test] pass={pass} ms={}",start.elapsed().as_millis());}
        let receipt:Value=serde_json::from_slice(&fs::read(root.join("runtime/unpvp/verified-assets.json")).unwrap()).unwrap();assert!(!receipt.as_object().unwrap().is_empty());
    }
    #[tokio::test]
    #[ignore = "Downloads official runtime and opens an isolated Minecraft test window"]
    async fn launcher_ui_smoke(){
        let root=PathBuf::from(std::env::var("UNPVP_TEST_ROOT").expect("Set an isolated .cache test root"));
        assert!(root.is_absolute()&&root.to_string_lossy().contains("/.cache/unpvp-"));
        let rt=Runtime{root:root.clone(),resources:PathBuf::from(env!("CARGO_MANIFEST_DIR")),job:Default::default(),auth:Default::default(),social:Default::default()};
        let profile=json!({"id":"00000000-0000-4000-8000-000000000189","version":"1.8.9","loader":"forge"});
        run(&rt,&profile,"UIPreview",1536,false,true,None).await.unwrap();
        let log=fs::read_to_string(root.join("profiles/00000000-0000-4000-8000-000000000189/unpvp-ui/logs/unbox-launch.log")).unwrap();
        assert!(log.contains("[unpvp-test] PASS"),"Game UI assertions did not finish");
        assert!(!log.contains("OneConfig"));
        for name in ["unbox_patcher","entityculling","nothirium","foamfix","phosphor-lighting","OptiFine"]{assert!(log.contains(name),"Missing performance engine {name}");}
        assert!(log.contains("Live HUD draws after returning to gameplay"));
    }
}
