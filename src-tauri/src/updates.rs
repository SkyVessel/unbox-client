use crate::Runtime;
use serde_json::{json,Value};
use std::{sync::Mutex,time::Duration};
use tauri_plugin_updater::{Update,UpdaterExt};
const RELEASES:&str="https://printoria.printoria-studio.workers.dev/unbox/releases/";
const BETA_MANIFEST:&str="https://printoria.printoria-studio.workers.dev/unbox/releases/beta.json";
const MANIFEST:&str="https://printoria.printoria-studio.workers.dev/unbox/releases/latest.json";
#[derive(Default)] pub struct State { pending:tokio::sync::Mutex<Option<Update>>, progress:Mutex<Value> }
fn trusted_asset(url:&str)->bool {
 let Some(path)=url.strip_prefix(RELEASES) else{return false};
 let Some((version,file))=path.split_once('/') else{return false};
 version.strip_prefix('v').and_then(|s|semver::Version::parse(s).ok()).is_some_and(|v|v.build.is_empty()&&(v.pre.is_empty()||is_beta(&v)))
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
fn is_beta(v: &semver::Version) -> bool {
    v.pre.as_str().strip_prefix("beta.").is_some_and(|n| !n.is_empty() && n.bytes().all(|c| c.is_ascii_digit()))
}
fn receives_beta(root: &std::path::Path) -> bool {
    std::fs::read(root.join("receive-beta-updates")).is_ok_and(|bytes| bytes == b"1")
}
#[tauri::command]
pub async fn set_beta_updates(enabled: bool, state: tauri::State<'_,State>, rt: tauri::State<'_,Runtime>) -> Result<(),String> {
    let mut pending = state.pending.lock().await;
    if receives_beta(&rt.root) != enabled {
        crate::atomic(&rt.root.join("receive-beta-updates"), if enabled {b"1"} else {b"0"})?;
        *pending = None;
        *state.progress.lock().unwrap() = json!({"status":"idle"});
    }
    Ok(())
}
#[tauri::command]
pub fn update_status(state:tauri::State<State>,rt:tauri::State<Runtime>,app:tauri::AppHandle)->Value{
    let mut v=state.progress.lock().unwrap().clone();
    if v.is_null(){v=json!({"status":"idle"})}
    v["currentVersion"]=json!(app.package_info().version.to_string());
    v["testingBuild"]=json!(cfg!(debug_assertions));
    v["receiveBeta"]=json!(receives_beta(&rt.root));
    v
}
async fn candidate(app:&tauri::AppHandle, beta:bool)->Result<Option<(semver::Version,Update)>,String>{
    let endpoint=if beta {BETA_MANIFEST} else {MANIFEST};
    let update=app.updater_builder().no_proxy().timeout(Duration::from_secs(30))
        .endpoints(vec![endpoint.parse().map_err(|_|"Invalid update URL")?]).map_err(|_|"Invalid updater configuration")?
        .build().map_err(|_|"Could not configure updater")?.check().await.map_err(|_|if beta {"Could not check the Beta channel. Retry later."} else {"Could not check the stable channel. Retry later."})?;
    let Some(mut u)=update else{return Ok(None)};
    let version=semver::Version::parse(&u.version).map_err(|_|"Invalid update version")?;
    if (beta && !is_beta(&version)) || (!beta && !version.pre.is_empty()) ||
        !trusted_asset(u.download_url.as_str()) || !u.download_url.as_str().starts_with(&format!("{RELEASES}v{version}/")) || u.signature.is_empty() {
        return Err("Invalid package for this update channel".into())
    }
    u.timeout=Some(Duration::from_secs(300));
    Ok(Some((version,u)))
}
#[tauri::command]
pub async fn check_update(state:tauri::State<'_,State>,rt:tauri::State<'_,Runtime>,app:tauri::AppHandle)->Result<Value,String>{
    let mut pending=state.pending.try_lock().map_err(|_|"An update operation is already in progress")?;
    *pending=None;
    let beta=receives_beta(&rt.root);
    let result=async{
        let next=if beta{
            let (stable,preview)=tokio::join!(candidate(&app,false),candidate(&app,true));
            match (stable,preview){
                (Ok(a),Ok(b))=>a.into_iter().chain(b).max_by(|a,b|a.0.cmp(&b.0)),
                (Ok(Some(u)),Err(_))|(Err(_),Ok(Some(u)))=>Some(u),
                (Err(e),_)|(_,Err(e))=>return Err(e)
            }
        }else{candidate(&app,false).await?};
        if let Some((_,u))=next{
            let view=json!({"status":"available","version":u.version,"notes":u.body.as_deref().unwrap_or("")});
            *pending=Some(u);
            Ok(view)
        }else{Ok(json!({"status":"current"}))}
    }.await;
    *state.progress.lock().unwrap()=match &result{Ok(v)=>v.clone(),Err(e)=>json!({"status":"error","error":e})};
    result.map(|mut view|{view["receiveBeta"]=json!(beta);view})
}
#[tauri::command]
pub async fn install_update(state:tauri::State<'_,State>,rt:tauri::State<'_,Runtime>,app:tauri::AppHandle)->Result<(),String>{
 let pending=state.pending.try_lock().map_err(|_|"An update operation is already in progress")?;
 let update=pending.as_ref().ok_or("Check for updates first")?;
 if semver::Version::parse(&update.version).is_ok_and(|v|is_beta(&v)) && !receives_beta(&rt.root) {
     return Err("Beta updates are off. Check for updates again.".into())
 }
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
 #[test]fn beta_is_opt_in_and_only_versioned_beta_assets_are_allowed(){
  let dir=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());
  assert!(!receives_beta(&dir));
  crate::atomic(&dir.join("receive-beta-updates"),b"1").unwrap();assert!(receives_beta(&dir));
  crate::atomic(&dir.join("receive-beta-updates"),b"0").unwrap();assert!(!receives_beta(&dir));
  assert!(trusted_asset(&format!("{RELEASES}v0.2.29-beta.1/test.exe")));
  for tag in ["v0.2.29-rc.1","v0.2.29-beta.foo","v0.2.29-beta.1+extra"]{assert!(!trusted_asset(&format!("{RELEASES}{tag}/test.exe")));}
  std::fs::remove_dir_all(dir).unwrap();
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
