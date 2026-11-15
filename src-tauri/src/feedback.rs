//! Session-bound, opt-in, exit-only upload. No game traffic is routed through this HTTP endpoint.
use std::{fs,io::Read,path::Path};
use serde_json::{json,Value};
use crate::{Runtime,social};
const MAX: u64=48000;
pub(crate) fn prepare(game:&Path)->Result<(),String>{
    let _=fs::remove_file(game.join("logs/unbox-feedback-recording.json"));
    let file=game.join("logs/unbox-feedback.json");
    if file.exists(){let old=game.join("logs/unbox-feedback-previous.json");let _=fs::remove_file(&old);fs::rename(file,old).map_err(|_|"Could not isolate the previous feedback report")?;}
    Ok(())
}
fn read(game:&Path,session:&str,uuid:&str)->Result<Option<Value>,String>{
    let file=game.join("logs/unbox-feedback.json");
    let f=match fs::File::open(file){Ok(f)=>f,Err(e) if e.kind()==std::io::ErrorKind::NotFound=>return Ok(None),Err(_)=>return Err("Could not read feedback report".into())};
    let mut bytes=Vec::new();f.take(MAX+1).read_to_end(&mut bytes).map_err(|_|"Could not read feedback report")?;
    if bytes.len() as u64>MAX{return Err("Feedback report exceeds the size limit".into())}
    let report:Value=serde_json::from_slice(&bytes).map_err(|_|"Invalid feedback report")?;
    if report["consent"]!=true||report["session"]!=session||report["accountUuid"]!=uuid||report["schema"]!=1||report["minecraft"]!="1.8.9"{return Err("Feedback does not match this game session".into())}
    if report["windows"].as_array().is_none_or(|w|w.is_empty()||w.len()>40){return Err("Invalid feedback measurements".into())}
    Ok(Some(report))
}
pub(crate) async fn on_exit(rt:&Runtime,game:&Path,session:&str,uuid:&str,account:Option<&social::FeedbackAccount>){
    let result=match read(game,session,uuid){Ok(None)=>return,Ok(Some(report))=>match account{Some(a)=>social::send_feedback(rt,a,report).await,None=>Err("Account unavailable; report kept on this device".into())},Err(e)=>Err(e)};
    let (sent,message)=match result{Ok(())=>{let _=fs::remove_file(game.join("logs/unbox-feedback.json"));(true,"Sent to Unbox".to_string())},Err(e)=>(false,e)};
    let _=social::atomic(&game.join("logs/unbox-feedback-status.json"),&serde_json::to_vec(&json!({"sent":sent,"message":message})).unwrap());
}
#[cfg(test)]mod tests{
 use super::*;
 #[test]fn consent_session_account_and_size_are_required(){
  let p=std::env::temp_dir().join(format!("feedback-{}",uuid::Uuid::new_v4()));fs::create_dir_all(p.join("logs")).unwrap();assert!(read(&p,"s","u").unwrap().is_none());
  let mut r=json!({"schema":1,"minecraft":"1.8.9","consent":true,"session":"s","accountUuid":"u","windows":[{}]});let file=p.join("logs/unbox-feedback.json");
  fs::write(&file,r.to_string()).unwrap();assert!(read(&p,"s","u").unwrap().is_some());assert!(read(&p,"other","u").is_err());assert!(read(&p,"s","other").is_err());
  r["consent"]=json!(false);fs::write(&file,r.to_string()).unwrap();assert!(read(&p,"s","u").is_err());fs::write(&file,vec![b' ';48001]).unwrap();assert!(read(&p,"s","u").is_err());prepare(&p).unwrap();assert!(read(&p,"s","u").unwrap().is_none());assert!(p.join("logs/unbox-feedback-previous.json").exists());fs::remove_dir_all(p).unwrap();
 }
}
