use crate::Runtime;
use serde_json::{json,Value};
use std::{sync::Mutex,time::Duration};
use tauri_plugin_updater::{Update,UpdaterExt};
const REPO:&str="https://github.com/SkyVessel/unbox-client/releases/download/";
#[derive(Default)] pub struct State { pending:tokio::sync::Mutex<Option<Update>>, progress:Mutex<Value> }
fn trusted_asset(url:&str)->bool {url.starts_with(REPO)&&url.len()>REPO.len()&&!url.contains(['?','#','\\'])}
fn manifest(release:&Value)->Result<String,String>{
 if release["draft"]==true||release["prerelease"]==true{return Err("No stable release is published yet".into())}
 let url=release["assets"].as_array().and_then(|assets|assets.iter().find(|a|a["name"]=="latest.json")).and_then(|a|a["browser_download_url"].as_str()).ok_or("This release has no signed updater package yet")?;
 if !trusted_asset(url){return Err("Untrusted update manifest location".into())}Ok(url.into())
}
fn reserve(job:&Mutex<Value>)->Result<Value,String>{let mut job=job.lock().unwrap();if job["busy"]==true{return Err("Close Minecraft and wait for downloads to finish before installing".into())}let previous=job.clone();*job=json!({"busy":true,"stage":"Updating Unbox"});Ok(previous)}
#[tauri::command]
pub fn update_status(state:tauri::State<State>,app:tauri::AppHandle)->Value{let mut v=state.progress.lock().unwrap().clone();if v.is_null(){v=json!({"status":"idle"})}v["currentVersion"]=json!(app.package_info().version.to_string());v}
#[tauri::command]
pub async fn check_update(state:tauri::State<'_,State>,app:tauri::AppHandle)->Result<Value,String>{
 let mut pending=state.pending.try_lock().map_err(|_|"An update operation is already in progress")?;*pending=None;
 let result=async{
  let r=reqwest::Client::builder().no_proxy().timeout(Duration::from_secs(20)).user_agent("Unbox-Client-Updater").build().map_err(|_|"Could not start update check")?.get("https://api.github.com/repos/SkyVessel/unbox-client/releases/latest").send().await.map_err(|_|"Could not reach GitHub. Check your connection and retry.")?;
  if r.status()==reqwest::StatusCode::NOT_FOUND{return Ok(json!({"status":"unpublished"}))}
  if r.status()==reqwest::StatusCode::FORBIDDEN||r.status()==reqwest::StatusCode::TOO_MANY_REQUESTS{return Err("GitHub is limiting requests. Try again later.".into())}
  if !r.status().is_success(){return Err("GitHub could not check updates. Try again later.".into())}
  let release:Value=r.json().await.map_err(|_|"Invalid GitHub release response")?;let url=manifest(&release)?;
  let update=app.updater_builder().no_proxy().timeout(Duration::from_secs(30)).endpoints(vec![url.parse().map_err(|_|"Invalid update URL")?]).map_err(|_|"Invalid updater configuration")?.build().map_err(|_|"Could not configure updater")?.check().await.map_err(|_|"Could not read the signed update manifest. Retry later.")?;
  if let Some(mut u)=update{if !trusted_asset(u.download_url.as_str())||u.signature.is_empty(){return Err("Update package is not from the configured release repository".into())}u.timeout=Some(Duration::from_secs(300));let v=json!({"status":"available","version":u.version,"notes":u.body.as_deref().unwrap_or("")});*pending=Some(u);Ok(v)}else{Ok(json!({"status":"current"}))}
 }.await;
 let view=match &result{Ok(v)=>v.clone(),Err(e)=>json!({"status":"error","error":e})};*state.progress.lock().unwrap()=view;result
}
#[tauri::command]
pub async fn install_update(state:tauri::State<'_,State>,rt:tauri::State<'_,Runtime>,app:tauri::AppHandle)->Result<(),String>{
 let pending=state.pending.try_lock().map_err(|_|"An update operation is already in progress")?;
 let update=pending.as_ref().ok_or("Check for updates first")?;
 #[cfg(target_os="macos")]
 if !std::env::current_exe().map_err(|_|"Could not find Unbox application")?.to_string_lossy().contains(".app/Contents/MacOS/"){return Err("Open the installed Unbox app to apply updates".into())}
 // The same lock used by start_game prevents a launch racing with installation.
 let previous=reserve(&rt.job)?;
 *state.progress.lock().unwrap()=json!({"status":"downloading","version":update.version,"downloaded":0});
 let result=async{
  let mut downloaded=0u64;
  let bytes=update.download(|n,total|{downloaded+=n as u64;*state.progress.lock().unwrap()=json!({"status":"downloading","version":update.version,"downloaded":downloaded,"total":total});},||{}).await.map_err(|_|"Download or signature verification failed. No update was installed.")?;
  *state.progress.lock().unwrap()=json!({"status":"installing","version":update.version});
  update.install(bytes).map_err(|_|"Could not install the update. Check app permissions and retry.")?;Ok::<(),String>(())
 }.await;
 if let Err(e)=result{*rt.job.lock().unwrap()=previous;*state.progress.lock().unwrap()=json!({"status":"available","version":update.version,"notes":update.body,"error":e});return Err(e)}
 *state.progress.lock().unwrap()=json!({"status":"restarting"});app.restart();
}
#[cfg(test)]mod tests{
 use super::*;
 #[test]fn release_assets_are_confined_to_our_repository(){
  let valid=json!({"assets":[{"name":"latest.json","browser_download_url":format!("{REPO}v0.2.0/latest.json")}]});assert!(manifest(&valid).is_ok());
  for url in ["http://github.com/SkyVessel/unbox-client/releases/download/v1/latest.json","https://github.com/other/repo/releases/download/v1/latest.json","https://github.com.evil.test/SkyVessel/unbox-client/releases/download/v1/latest.json"]{assert!(manifest(&json!({"assets":[{"name":"latest.json","browser_download_url":url}]})).is_err())}
  assert!(manifest(&json!({"assets":[]})).is_err());assert!(manifest(&json!({"draft":true,"assets":valid["assets"]})).is_err());
 }
 #[test]fn updater_cannot_replace_active_game_job_or_race_with_launch(){let job=Mutex::new(json!({"busy":true,"stage":"Playing","profileId":"test"}));let before=job.lock().unwrap().clone();assert!(reserve(&job).is_err());assert_eq!(*job.lock().unwrap(),before);*job.lock().unwrap()=json!({"busy":false});let previous=reserve(&job).unwrap();assert_eq!(previous["busy"],false);assert!(reserve(&job).is_err());}
}
