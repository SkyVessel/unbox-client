#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]
mod launcher;
mod auth;
mod content;
mod social;
mod updates;
mod sharing;
use serde_json::{json, Value};
use std::{fs, path::PathBuf, sync::{Arc, Mutex}};
use tauri::Manager;

#[derive(Clone)]
pub struct Runtime { pub root: PathBuf, pub resources: PathBuf, pub job: Arc<Mutex<Value>>, pub auth:Arc<tokio::sync::Mutex<auth::AuthState>>, pub social:Arc<tokio::sync::Mutex<social::State>> }
pub fn atomic(path: &std::path::Path, data: &[u8]) -> Result<(), String> {
    if let Some(p) = path.parent() { fs::create_dir_all(p).map_err(|e|e.to_string())?; }
    let temp = path.with_extension("tmp"); fs::write(&temp, data).map_err(|e|e.to_string())?;
    fs::rename(temp, path).map_err(|e|e.to_string())
}
pub fn profile_dir(rt: &Runtime, id: &str) -> Result<PathBuf,String> {
    uuid::Uuid::parse_str(id).map_err(|_|"Invalid profile ID")?;
    Ok(rt.root.join("profiles").join(id))
}
#[tauri::command]
async fn bootstrap(rt: tauri::State<'_,Runtime>) -> Result<Value,String> {
    let path=rt.root.join("state.json");
    let _guard=rt.auth.lock().await;
    let mut saved=if path.exists(){serde_json::from_slice::<Value>(&fs::read(path).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?}else{json!({"profiles":[],"selected":null,"settings":{"memory":4096,"reducedMotion":false,"reducedTransparency":false,"minimize":true},"account":null})};
    saved["account"]=auth::restore_account(&rt,&saved["account"])?;
    Ok(json!({"state":saved,"dataDirectory":rt.root,"native":true,"version":"26.1"}))
}
#[tauri::command]
async fn save_state(rt: tauri::State<'_,Runtime>, mut state: Value) -> Result<(),String> {
    if !state.is_object() || !state["profiles"].is_array() {return Err("Invalid state".into())}
    for p in state["profiles"].as_array().unwrap() {profile_dir(&rt,p["id"].as_str().ok_or("Missing ID")?)?;}
    let _guard=rt.auth.lock().await;
    state["account"]=auth::restore_account(&rt,&state["account"])?;
    // A settings write queued before sync finished must not erase the new friend profile.
    if let Ok(bytes)=fs::read(rt.root.join("state.json")){if let Ok(saved)=serde_json::from_slice::<Value>(&bytes){if let Some(existing)=saved["profiles"].as_array(){let profiles=state["profiles"].as_array_mut().unwrap();for p in existing{if p["sharedHost"].is_string()&&!profiles.iter().any(|v|v["id"]==p["id"]){profiles.push(p.clone());}}}}}
    atomic(&rt.root.join("state.json"),serde_json::to_vec_pretty(&state).map_err(|e|e.to_string())?.as_slice())
}
#[tauri::command]
fn create_profile(rt: tauri::State<Runtime>, name: String, version: String, loader: String, icon: String) -> Result<Value,String> {
    if version!="26.1" {return Err("Unbox Client supports Minecraft 26.1 only".into())}
    if !["fabric","vanilla"].contains(&loader.as_str()) {return Err("This loader has not passed Unbox launch validation yet".into())}
    let name=name.trim(); if name.is_empty() || name.chars().count()>48 {return Err("Use a name between 1 and 48 characters".into())}
    if !["grass_block","stone","dirt","oak_planks","bricks","tnt","gold_block","iron_block"].contains(&icon.as_str()){return Err("Unknown block icon".into())}
    let id=uuid::Uuid::new_v4().to_string(); let dir=profile_dir(&rt,&id)?;
    let profile=json!({"id":id,"name":name,"version":version,"loader":loader,"icon":icon});
    atomic(&dir.join("profile.json"),&serde_json::to_vec_pretty(&profile).unwrap())?;
    Ok(profile)
}
#[tauri::command]
fn save_modules(rt: tauri::State<Runtime>, id: String, config: Value) -> Result<(),String> {
    let dir=profile_dir(&rt,&id)?.join("config");
    let mut merged=read_properties(&dir.join("unbox.properties"));
    let patch=config.as_object().ok_or("Invalid module config")?;
    for(k,v)in patch {merged.insert(k.clone(),v.clone());}
    let mut lines=String::from("# Unbox Client settings\n");
    for (key,value) in &merged {
        if !key.bytes().all(|b|b.is_ascii_alphanumeric()||b==b'.'||b==b'_') {return Err("Invalid setting name".into())}
        let value=match value {Value::Bool(v)=>v.to_string(),Value::Number(v)=>v.to_string(),Value::String(v)=>v.to_owned(),_=>continue};
        if value.contains(['\n','\r','\\']) || value.len()>4096 {return Err("Invalid setting value".into())}
        lines.push_str(&format!("{key}={value}\n"));
    }
    atomic(&dir.join("unbox.properties"),lines.as_bytes())
}
fn read_properties(path:&std::path::Path)->serde_json::Map<String,Value>{
    let mut result=serde_json::Map::new();
    if let Ok(s)=fs::read_to_string(path){for line in s.lines(){if line.starts_with('#')||line.starts_with('!'){continue}if let Some((k,v))=line.split_once('='){let v=v.replace("\\#","#");let value=if v=="true"||v=="false"{json!(v=="true")}else if let Ok(n)=v.parse::<i64>(){json!(n)}else{json!(v)};result.insert(k.to_owned(),value);}}}result
}
#[tauri::command]
fn read_modules(rt: tauri::State<Runtime>, id:String)->Result<Value,String>{Ok(Value::Object(read_properties(&profile_dir(&rt,&id)?.join("config/unbox.properties"))))}
#[tauri::command]
fn status(rt: tauri::State<Runtime>) -> Value {rt.job.lock().unwrap().clone()}
#[tauri::command]
fn start_game(rt: tauri::State<Runtime>, app: tauri::AppHandle, id: String, username: String, memory: u32, prepare_only: bool) -> Result<(),String> {
    if username.is_empty()||username.len()>16||!username.bytes().all(|b|b.is_ascii_alphanumeric()||b==b'_'){return Err("Local name must be 1–16 letters, numbers or underscores".into())}
    let dir=profile_dir(&rt,&id)?;
    let profile:Value=serde_json::from_slice(&fs::read(dir.join("profile.json")).map_err(|e|e.to_string())?).map_err(|e|e.to_string())?;
    {let mut job=rt.job.lock().unwrap();if job["busy"]==true{return Err("A game or preparation is already running".into())}*job=json!({"busy":true,"stage":"Checking files","progress":0,"profileId":id});}
    let runtime=rt.inner().clone();
    tauri::async_runtime::spawn(async move {
        let mut current=profile.clone();
        let result=async{for _ in 0..4{launcher::run(&runtime,&current,&username,memory.clamp(2048,16384),prepare_only,false,&app).await?;if prepare_only{return Ok(())}if let Some(next)=sharing::resume(&runtime,&current).await?{current=next;*runtime.job.lock().unwrap()=json!({"busy":true,"stage":"Preparing friend’s mods","profileId":current["id"]});}else{return Ok(())}}Err::<(),String>("Host mods keep changing. Ask for a new invitation.".into())}.await;
        let mut job=runtime.job.lock().unwrap();
        *job=match result {Ok(_)=>json!({"busy":false,"stage":if prepare_only{"Ready"}else{"Game closed"},"progress":100,"profileId":current["id"]}),Err(e)=>json!({"busy":false,"stage":"Failed","error":e,"profileId":current["id"]})};
    }); Ok(())
}
#[tauri::command]
async fn list_content(rt: tauri::State<'_,Runtime>, id: String, kind: String) -> Result<Value,String> {
    let rt=rt.inner().clone();tauri::async_runtime::spawn_blocking(move||content::list(&rt,&id,&kind)).await.map_err(|_|"Could not read content list")?
}
fn managed_mod_names(game:&std::path::Path)->std::collections::HashSet<String>{
    let mut names=std::collections::HashSet::from(["unbox-client.jar".to_owned()]);
    if let Ok(bytes)=fs::read(game.join("unbox-performance-lock.json")){if let Ok(Value::Array(entries))=serde_json::from_slice::<Value>(&bytes){for m in entries{if let Some(name)=m["filename"].as_str(){names.insert(name.to_owned());}}}}
    names
}
#[cfg(test)]mod content_tests{
    use super::*;
    #[test]fn only_bundled_client_files_are_hidden(){
        let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());fs::create_dir_all(&root).unwrap();
        fs::write(root.join("unbox-performance-lock.json"),br#"[{"filename":"sodium.jar"},{"filename":"fabric-api.jar"}]"#).unwrap();
        let hidden=managed_mod_names(&root);
        assert!(hidden.contains("unbox-client.jar"));assert!(hidden.contains("sodium.jar"));assert!(hidden.contains("fabric-api.jar"));
        assert!(!hidden.contains("create.jar"));assert!(!hidden.contains("my-personal-mod.jar"));
        fs::remove_dir_all(root).unwrap();
    }
}
#[tauri::command]
fn open_folder(rt: tauri::State<Runtime>, id: Option<String>) -> Result<(),String> {
    let p=if let Some(id)=id{profile_dir(&rt,&id)?}else{rt.root.clone()};fs::create_dir_all(&p).map_err(|e|e.to_string())?;
    #[cfg(target_os="macos")] let program="open";
    #[cfg(target_os="windows")] let program="explorer";
    #[cfg(target_os="linux")] let program="xdg-open";
    std::process::Command::new(program).arg(p).spawn().map_err(|e|e.to_string())?;Ok(())
}
fn main() {
    #[cfg(debug_assertions)] if std::env::args().nth(1).as_deref()==Some("--apply-sync-fixture") {
        let args:Vec<String>=std::env::args().collect();let root=PathBuf::from(&args[2]);assert!(root.to_string_lossy().contains("/.cache/"),"Fixture root only");
        let rt=Runtime{root:root.clone(),resources:root.clone(),job:Arc::default(),auth:Arc::default(),social:Arc::default()};
        let source:Value=serde_json::from_slice(&fs::read(&args[3]).unwrap()).unwrap();let request:Value=serde_json::from_slice(&fs::read(&args[4]).unwrap()).unwrap();
        match sharing::apply(&rt,&source,&request){Ok(p)=>println!("{}",p),Err(e)=>{eprintln!("{}",e);std::process::exit(1)}}return;
    }
    tauri::Builder::default().plugin(tauri_plugin_updater::Builder::new().build()).manage(updates::State::default()).setup(|app| {
        let root=app.path().app_data_dir()?;fs::create_dir_all(&root)?;
        let rt=Runtime {root,resources:app.path().resource_dir()?,job:Arc::new(Mutex::new(json!({"busy":false,"stage":"Idle"}))),auth:Arc::new(tokio::sync::Mutex::new(auth::AuthState::default())),social:Arc::new(tokio::sync::Mutex::new(social::State::default()))};
        if cfg!(debug_assertions)&&std::env::args().any(|a|a=="--smoke-launch") {
            let profile=json!({"id":"00000000-0000-4000-8000-000000000001","name":"Unbox launch verification","version":"26.1","loader":"fabric","icon":"grass_block"});
            let dir=profile_dir(&rt,profile["id"].as_str().unwrap()).map_err(std::io::Error::other)?;
            atomic(&dir.join("profile.json"),&serde_json::to_vec(&profile)?).map_err(std::io::Error::other)?;
            let copy=rt.clone();let handle=app.handle().clone();
            tauri::async_runtime::spawn(async move {let result=launcher::run(&copy,&profile,"UnboxTest",4096,false,true,&handle).await;println!("UNBOX_SMOKE_RESULT: {:?}",result);if let Err(e)=result{*copy.job.lock().unwrap()=json!({"busy":false,"stage":"Failed","error":e});}});
        }
        social::start(rt.clone());app.manage(rt);Ok(())
    }).invoke_handler(tauri::generate_handler![updates::check_update,updates::install_update,updates::update_status,auth::auth_unbox_password,auth::auth_unbox_rename,auth::auth_info,auth::auth_setup,auth::auth_development_setup,auth::auth_accounts,auth::auth_select,auth::auth_local,auth::auth_refresh_profile,auth::auth_upload_skin,auth::auth_begin,auth::auth_open,auth::auth_poll,auth::auth_cancel,auth::auth_sign_out,bootstrap,save_state,create_profile,save_modules,read_modules,status,start_game,list_content,open_folder,content::import_content,content::open_content_folder,social::social_status,social::social_action]).run(tauri::generate_context!()).expect("Unable to start Unbox Client");
}
