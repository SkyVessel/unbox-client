use crate::{Runtime,atomic};
use serde_json::{json,Value};
use std::time::Duration;
#[tauri::command]
pub async fn news_feed(rt:tauri::State<'_,Runtime>)->Result<Value,String>{
 let path=rt.root.join("news-cache.json");
 let fresh=async {
  let url=format!("{}/v1/news",crate::social::endpoint(&rt)?);
  let mut r=reqwest::Client::builder().no_proxy().redirect(reqwest::redirect::Policy::none()).timeout(Duration::from_secs(12)).build().map_err(|e|e.to_string())?.get(url).send().await.map_err(|_|"News is temporarily unavailable")?;
  if !r.status().is_success(){return Err("News is temporarily unavailable".to_string())}
  let mut bytes=Vec::new();while let Some(chunk)=r.chunk().await.map_err(|_|"News download interrupted")? {if bytes.len()+chunk.len()>1_000_000{return Err("News response is too large".into())}bytes.extend_from_slice(&chunk);}
  let value:Value=serde_json::from_slice(&bytes).map_err(|_|"Invalid news response")?;
  if !value["posts"].is_array(){return Err("Invalid news response".into())}atomic(&path,&bytes)?;Ok(value)
 }.await;
 match fresh{Ok(v)=>Ok(v),Err(error)=>{if let Ok(bytes)=std::fs::read(path){if let Ok(mut cached)=serde_json::from_slice::<Value>(&bytes){cached["cached"]=json!(true);return Ok(cached)}}Err(error)}}
}
