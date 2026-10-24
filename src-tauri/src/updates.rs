use crate::Runtime;
use serde_json::{json,Value};
use std::{sync::Mutex,time::Duration};
use tauri_plugin_updater::{Update,UpdaterExt};
const RELEASES:&str="https://printoria.printoria-studio.workers.dev/unbox/releases/";
const MANIFEST:&str="https://printoria.printoria-studio.workers.dev/unbox/releases/latest.json";
#[derive(Default)] pub struct State { pending:tokio::sync::Mutex<Option<Update>>, progress:Mutex<Value> }
fn trusted_asset(url:&str)->bool {
 let Some(path)=url.strip_prefix(RELEASES) else{return false};
 let Some((version,file))=path.split_once('/') else{return false};
 version.starts_with('v')&&version[1..].split('.').count()==3&&version[1..].split('.').all(|p|!p.is_empty()&&p.bytes().all(|c|c.is_ascii_digit()))
 &&!file.is_empty()&&file.bytes().all(|c|c.is_ascii_alphanumeric()||b"._-".contains(&c))&&(file.ends_with(".app.tar.gz")||file.ends_with(".exe"))
}
fn reserve(job:&Mutex<Value>)->Result<Value,String>{let mut job=job.lock().unwrap();if job["busy"]==true{return Err("Close Minecraft and wait for downloads to finish before installing".into())}let previous=job.clone();*job=json!({"busy":true,"stage":"Updating Unbox"});Ok(previous)}
fn install_error(error:&tauri_plugin_updater::Error)->String{
 #[cfg(target_os="macos")]
 if matches!(error,tauri_plugin_updater::Error::Io(e) if e.kind()==std::io::ErrorKind::PermissionDenied){
  return format!("macOS blocked the update installation. Quit Unbox, move the app to your Applications folder, reopen it and retry. Administrator approval alone does not grant access to protected folders such as Documents. Details: {error}")
 }
 format!("Could not install the update. Details: {error}")
}
#[tauri::command]
pub fn update_status(state:tauri::State<State>,app:tauri::AppHandle)->Value{let mut v=state.progress.lock().unwrap().clone();if v.is_null(){v=json!({"status":"idle"})}v["currentVersion"]=json!(app.package_info().version.to_string());v["testingBuild"]=json!(cfg!(debug_assertions));v}
#[tauri::command]
pub async fn check_update(state:tauri::State<'_,State>,app:tauri::AppHandle)->Result<Value,String>{
 let mut pending=state.pending.try_lock().map_err(|_|"An update operation is already in progress")?;*pending=None;
 let result=async{
  let update=app.updater_builder().no_proxy().timeout(Duration::from_secs(30)).endpoints(vec![MANIFEST.parse().map_err(|_|"Invalid update URL")?]).map_err(|_|"Invalid updater configuration")?.build().map_err(|_|"Could not configure updater")?.check().await.map_err(|_|"Could not read the signed update manifest. Retry later.")?;
  if let Some(mut u)=update{if !trusted_asset(u.download_url.as_str())||u.signature.is_empty(){return Err("Update package is not from the configured download server".into())}u.timeout=Some(Duration::from_secs(300));let v=json!({"status":"available","version":u.version,"notes":u.body.as_deref().unwrap_or("")});*pending=Some(u);Ok(v)}else{Ok(json!({"status":"current"}))}
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
  update.install(bytes).map_err(|e|install_error(&e))?;Ok::<(),String>(())
 }.await;
 if let Err(e)=result{*rt.job.lock().unwrap()=previous;*state.progress.lock().unwrap()=json!({"status":"available","version":update.version,"notes":update.body,"error":e});return Err(e)}
 *state.progress.lock().unwrap()=json!({"status":"restarting"});app.restart();
}
#[cfg(test)]mod tests{
 use super::*;
 #[test]fn release_assets_are_confined_to_cloudflare(){
  assert!(trusted_asset(&format!("{RELEASES}v0.2.21/Unbox.Client_0.2.21_aarch64.app.tar.gz")));
  assert!(trusted_asset(&format!("{RELEASES}v0.2.21/Unbox.Client_0.2.21_windows-x64-setup.exe")));
  for path in ["../bad.exe","v1/evil.exe","v1.2.3/../bad.exe","v1.2.3/a.exe?x=1","v1.2.3/a.exe#x","v1.2.3/%2e%2e.exe","v1.2.3/a/b.exe","v1.2.3/","v1.2.3/a.json"]{assert!(!trusted_asset(&format!("{RELEASES}{path}")),"{path}");}
  for url in ["https://github.com/SkyVessel/unbox-client/releases/download/v1.2.3/a.exe","http://printoria.printoria-studio.workers.dev/unbox/releases/v1.2.3/a.exe","https://printoria.printoria-studio.workers.dev.evil.test/unbox/releases/v1.2.3/a.exe"]{assert!(!trusted_asset(url));}
 }
 #[test]fn updater_cannot_replace_active_game_job_or_race_with_launch(){let job=Mutex::new(json!({"busy":true,"stage":"Playing","profileId":"test"}));let before=job.lock().unwrap().clone();assert!(reserve(&job).is_err());assert_eq!(*job.lock().unwrap(),before);*job.lock().unwrap()=json!({"busy":false});let previous=reserve(&job).unwrap();assert_eq!(previous["busy"],false);assert!(reserve(&job).is_err());}
 #[test]fn install_failure_preserves_the_actual_cause(){
  let error=tauri_plugin_updater::Error::Io(std::io::Error::new(std::io::ErrorKind::Other,"No space left on device"));
  let message=install_error(&error);assert!(message.contains("No space left on device"));assert!(!message.contains("permissions"));assert!(!message.contains("Administrator"));
  let error=tauri_plugin_updater::Error::PreviousAppNotRestored(std::path::PathBuf::from("/backup/Unbox Client.app"));
  assert!(install_error(&error).contains("/backup/Unbox Client.app"));
 }
 #[cfg(target_os="macos")]
 #[test]fn mac_permission_failure_explains_folder_access(){
  let error=tauri_plugin_updater::Error::Io(std::io::Error::from(std::io::ErrorKind::PermissionDenied));
  let message=install_error(&error);assert!(message.contains("Applications"));assert!(message.contains("Documents"));assert!(message.contains(&error.to_string()));
 }
}
