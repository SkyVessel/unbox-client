//! Public-client device authorization. Tokens never cross the Tauri IPC boundary.
use crate::{atomic,Runtime};
use serde::{Deserialize,Serialize};
use serde_json::{json,Value};
use std::{fs,time::{Duration,Instant,SystemTime,UNIX_EPOCH}};
use base64::{Engine,engine::general_purpose::STANDARD};
const TOKEN:&str="https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const SCOPE:&str="XboxLive.signin offline_access";
#[derive(Default)]pub struct AuthState {pending:Option<Pending>,generation:u64}
struct Pending {id:String,device:String,client_id:String,expires:Instant,next:Instant,interval:u64}
#[derive(Serialize,Deserialize)]struct Stored {refresh:String,access:String,expires:u64,client_id:String,xuid:String,account:Value}
pub struct Session {pub name:String,pub uuid:String,pub token:String,pub xuid:String,pub client_id:String}
fn now()->u64{SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs()}
fn client()->Result<reqwest::Client,String>{reqwest::Client::builder().no_proxy().redirect(reqwest::redirect::Policy::none()).timeout(Duration::from_secs(25)).build().map_err(|_|"Could not initialize account connection".into())}
fn field<'a>(v:&'a Value,k:&str)->Result<&'a str,String>{v[k].as_str().filter(|s|!s.is_empty()).ok_or_else(||format!("The account service did not return {k}"))}
fn client_id(rt:&Runtime)->Result<String,String>{let v:Value=serde_json::from_slice(&fs::read(rt.root.join("auth-config.json")).map_err(|_|"Microsoft application registration is required. Add the Unbox Client ID in account setup.")?).map_err(|_|"Invalid account configuration")?;let id=field(&v,"clientId")?;uuid::Uuid::parse_str(id).map_err(|_|"Invalid Microsoft Client ID")?;Ok(id.to_owned())}
#[cfg(target_os="macos")]fn store(s:&Stored)->Result<(),String>{security_framework::passwords::set_generic_password("dev.unbox.client.microsoft","primary",&serde_json::to_vec(s).map_err(|_|"Could not encode account")?).map_err(|_|"macOS Keychain could not save the account. Allow Unbox access and retry.".into())}
#[cfg(target_os="macos")]fn load()->Result<Stored,String>{let b=security_framework::passwords::get_generic_password("dev.unbox.client.microsoft","primary").map_err(|_|"Sign in with Microsoft again to unlock your account.")?;serde_json::from_slice(&b).map_err(|_|"Saved account is invalid. Sign in again.".into())}
#[cfg(not(target_os="macos"))]fn store(_: &Stored)->Result<(),String>{Err("Secure account storage is currently supported on macOS only".into())}
#[cfg(not(target_os="macos"))]fn load()->Result<Stored,String>{Err("Secure account storage is currently supported on macOS only".into())}
#[tauri::command]pub fn auth_setup(rt:tauri::State<Runtime>,client_id:String)->Result<(),String>{uuid::Uuid::parse_str(&client_id).map_err(|_|"Enter your application's UUID Client ID")?;atomic(&rt.root.join("auth-config.json"),&serde_json::to_vec(&json!({"clientId":client_id})).unwrap())}
#[tauri::command]pub fn auth_info(rt:tauri::State<Runtime>)->Value{json!({"configured":client_id(&rt).is_ok()})}
#[tauri::command]pub async fn auth_begin(rt:tauri::State<'_,Runtime>)->Result<Value,String>{
    let client_id=client_id(&rt)?;let mut lock=rt.auth.lock().await;lock.generation+=1;lock.pending=None;
    let r=client()?.post("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode").form(&[("client_id",client_id.as_str()),("scope",SCOPE)]).send().await.map_err(|_|"Could not reach Microsoft. Check your connection.")?;
    let status=r.status();let v:Value=r.json().await.map_err(|_|"Invalid Microsoft response")?;
    if !status.is_success(){return Err("Microsoft rejected the application. Check the Client ID, personal accounts, and public client flow settings.".into())}
    let code=field(&v,"user_code")?.to_owned();let device=field(&v,"device_code")?.to_owned();let interval=v["interval"].as_u64().unwrap_or(5).clamp(5,60);let seconds=v["expires_in"].as_u64().unwrap_or(900).min(1800);let id=uuid::Uuid::new_v4().to_string();
    lock.pending=Some(Pending{id:id.clone(),device,client_id,expires:Instant::now()+Duration::from_secs(seconds),next:Instant::now()+Duration::from_secs(interval),interval});
    Ok(json!({"id":id,"code":code,"interval":interval,"expiresIn":seconds,"url":"https://www.microsoft.com/link"}))
}
#[tauri::command]pub fn auth_open()->Result<(),String>{
    #[cfg(target_os="macos")]let program="open";
    #[cfg(target_os="windows")]let program="explorer";
    #[cfg(target_os="linux")]let program="xdg-open";
    std::process::Command::new(program).arg("https://www.microsoft.com/link").spawn().map_err(|_|"Could not open your browser")?;Ok(())
}
#[tauri::command]pub async fn auth_cancel(rt:tauri::State<'_,Runtime>)->Result<(),String>{let mut a=rt.auth.lock().await;a.generation+=1;a.pending=None;Ok(())}
#[tauri::command]pub async fn auth_poll(rt:tauri::State<'_,Runtime>,id:String)->Result<Value,String>{
    let mut lock=rt.auth.lock().await;let generation=lock.generation;let p=lock.pending.as_mut().filter(|p|p.id==id).ok_or("Sign-in was canceled. Start again.")?;
    if Instant::now()>=p.expires{lock.pending=None;return Err("Sign-in code expired. Start again.".into())}
    if Instant::now()<p.next{return Ok(json!({"status":"pending"}))}
    p.next=Instant::now()+Duration::from_secs(p.interval);
    let client_id=p.client_id.clone();let device=p.device.clone();drop(lock);
    let c=client()?;let r=c.post(TOKEN).form(&[("client_id",client_id.as_str()),("grant_type","urn:ietf:params:oauth:grant-type:device_code"),("device_code",device.as_str())]).send().await.map_err(|_|"Microsoft connection failed. Please retry sign-in.")?;
    let status=r.status();let v:Value=r.json().await.map_err(|_|"Invalid Microsoft response")?;
    let mut lock=rt.auth.lock().await;if lock.generation!=generation{return Err("Sign-in canceled".into())}let p=lock.pending.as_mut().filter(|p|p.id==id).ok_or("Sign-in canceled")?;
    match v["error"].as_str(){Some("authorization_pending")=>return Ok(json!({"status":"pending"})),Some("slow_down")=>{p.interval+=5;p.next=Instant::now()+Duration::from_secs(p.interval);return Ok(json!({"status":"pending"}));},Some(_)=>{lock.pending=None;return Err("Sign-in declined or expired. Start again when ready.".into());},None=>{}}
    if !status.is_success(){lock.pending=None;return Err("Microsoft sign-in failed".into())}
    lock.pending=None;drop(lock);
    let saved=minecraft(&c,&v,&client_id).await?;let lock=rt.auth.lock().await;if lock.generation!=generation{return Err("Sign-in canceled".into())}store(&saved)?;Ok(json!({"status":"complete","account":saved.account}))
}
async fn checked(r:reqwest::Response,stage:&str)->Result<Value,String>{let status=r.status();let v:Value=r.json().await.map_err(|_|format!("{stage}: invalid response"))?;if status.is_success(){return Ok(v)}let msg=match v["XErr"].as_u64(){Some(2148916233)=>"Create an Xbox profile at xbox.com first",Some(2148916238)=>"Your Microsoft family organizer must allow Xbox access",_=>if stage=="Minecraft authentication"&&status.as_u16()==403{"This Microsoft application has not been approved for Minecraft Services. Application registration approval is required."}else if status.as_u16()==401{"Your session expired. Sign in again."}else if stage=="Minecraft profile"&&status.as_u16()==404{"No Java Edition profile found. Check ownership and create your profile at minecraft.net."}else{"The account service rejected the request. Please retry."}};Err(format!("{stage}: {msg}"))}
async fn post(c:&reqwest::Client,url:&str,body:Value,stage:&str)->Result<Value,String>{checked(c.post(url).header("x-xbl-contract-version","1").json(&body).send().await.map_err(|_|format!("{stage}: connection failed"))?,stage).await}
async fn minecraft(c:&reqwest::Client,oauth:&Value,client_id:&str)->Result<Stored,String>{
    let user=post(c,"https://user.auth.xboxlive.com/user/authenticate",json!({"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":format!("d={}",field(oauth,"access_token")?)},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}),"Xbox sign-in").await?;
    let x=post(c,"https://xsts.auth.xboxlive.com/xsts/authorize",json!({"Properties":{"SandboxId":"RETAIL","UserTokens":[field(&user,"Token")?]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}),"Xbox authorization").await?;
    let claims=&x["DisplayClaims"]["xui"][0];let mc=post(c,"https://api.minecraftservices.com/authentication/login_with_xbox",json!({"identityToken":format!("XBL3.0 x={};{}",field(claims,"uhs")?,field(&x,"Token")?)}),"Minecraft authentication").await?;
    let access=field(&mc,"access_token")?.to_owned();
    let ent=checked(c.get("https://api.minecraftservices.com/entitlements/mcstore").bearer_auth(&access).send().await.map_err(|_|"Ownership check: connection failed")?,"Ownership check").await?;
    if ent["items"].as_array().is_none_or(|v|v.is_empty()){return Err("This account does not have an active Minecraft Java Edition entitlement.".into())}
    let p=checked(c.get("https://api.minecraftservices.com/minecraft/profile").bearer_auth(&access).send().await.map_err(|_|"Profile connection failed")?,"Minecraft profile").await?;
    let id=field(&p,"id")?;uuid::Uuid::parse_str(id).map_err(|_|"Invalid Minecraft profile ID")?;
    let mut account=json!({"name":field(&p,"name")?,"uuid":id,"type":"microsoft","skinModel":"classic"});
    if let Some(skin)=p["skins"].as_array().and_then(|a|a.iter().find(|s|s["state"]=="ACTIVE")){
        account["skinModel"]=json!(if skin["variant"]=="SLIM"{"slim"}else{"classic"});
        if let Some(source)=skin["url"].as_str(){let url=source.strip_prefix("http://textures.minecraft.net/texture/").map(|p|format!("https://textures.minecraft.net/texture/{p}")).unwrap_or_else(||source.to_owned());if skin_url(&url){if let Ok(r)=c.get(&url).send().await{if r.status().is_success()&&r.content_length().unwrap_or(0)<=1048576{let mut response=r;let mut bytes=Vec::new();while let Ok(Some(chunk))=response.chunk().await{if bytes.len()+chunk.len()>1048576{bytes.clear();break;}bytes.extend_from_slice(&chunk);}if bytes.starts_with(b"\x89PNG\r\n\x1a\n"){account["skin"]=json!(format!("data:image/png;base64,{}",STANDARD.encode(bytes)));}}}}}
    }
    Ok(Stored{refresh:field(oauth,"refresh_token")?.to_owned(),access,expires:now()+mc["expires_in"].as_u64().unwrap_or(3600),client_id:client_id.to_owned(),xuid:claims["xid"].as_str().unwrap_or("").to_owned(),account})
}
fn skin_url(url:&str)->bool{reqwest::Url::parse(url).is_ok_and(|u|u.scheme()=="https"&&u.host_str()==Some("textures.minecraft.net")&&u.username().is_empty()&&u.password().is_none()&&u.port().is_none()&&u.path().starts_with("/texture/"))}
pub async fn session(_rt:&Runtime,uuid:&str)->Result<Session,String>{
    let mut s=load()?;if s.account["uuid"]!=uuid{return Err("The selected Microsoft account does not match the saved credentials. Sign in again.".into())}
    if s.expires<=now()+120{
        let c=client()?;let r=c.post(TOKEN).form(&[("client_id",s.client_id.as_str()),("grant_type","refresh_token"),("refresh_token",s.refresh.as_str()),("scope",SCOPE)]).send().await.map_err(|_|"Could not refresh your account. Check your connection.")?;
        let mut v=checked(r,"Microsoft session refresh").await?;if v["refresh_token"].is_null(){v["refresh_token"]=json!(s.refresh);}else{s.refresh=field(&v,"refresh_token")?.to_owned();store(&s)?;}
        let renewed=minecraft(&c,&v,&s.client_id).await?;if renewed.account["uuid"]!=uuid{return Err("Microsoft returned a different account. Sign in again.".into())}s=renewed;store(&s)?;
    }
    Ok(Session{name:field(&s.account,"name")?.to_owned(),uuid:uuid.to_owned(),token:s.access,xuid:s.xuid,client_id:s.client_id})
}
#[tauri::command]pub async fn auth_sign_out(rt:tauri::State<'_,Runtime>)->Result<(),String>{let mut lock=rt.auth.lock().await;lock.generation+=1;lock.pending=None;
    #[cfg(target_os="macos")] {match security_framework::passwords::delete_generic_password("dev.unbox.client.microsoft","primary"){Ok(())=>{},Err(e) if e.code()==-25300=>{},Err(_)=>return Err("Could not remove the account from macOS Keychain".into())}}
    Ok(())
}
#[cfg(test)]mod tests{use super::*;#[test]fn skin_origin_is_restricted(){assert!(skin_url("https://textures.minecraft.net/texture/abc"));for u in ["http://textures.minecraft.net/texture/abc","https://textures.minecraft.net.evil.example/texture/x","https://example.com/x","https://textures.minecraft.net:444/texture/x","https://user@textures.minecraft.net/texture/x"]{assert!(!skin_url(u));}}}

#[cfg(test)]mod response_tests{
    use super::*;
    async fn response(status:u16,body:&str)->reqwest::Response{
        use tokio::io::{AsyncReadExt,AsyncWriteExt};
        let listener=tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();let addr=listener.local_addr().unwrap();let body=body.to_owned();
        tokio::spawn(async move{let(mut stream,_)=listener.accept().await.unwrap();let mut request=[0u8;2048];let _=stream.read(&mut request).await;let reply=format!("HTTP/1.1 {status} Test\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",body.len());stream.write_all(reply.as_bytes()).await.unwrap();});
        client().unwrap().get(format!("http://{addr}")).send().await.unwrap()
    }
    #[tokio::test]async fn registration_failure_has_actionable_message_without_server_token(){let r=response(403,r#"{"error":"Invalid app registration","token":"do-not-expose-token"}"#).await;let e=checked(r,"Minecraft authentication").await.unwrap_err();assert!(e.contains("approved for Minecraft Services"));assert!(!e.contains("do-not-expose-token"));}
    #[tokio::test]async fn xbox_profile_and_family_errors_are_distinguished(){let e=checked(response(401,r#"{"XErr":2148916233}"#).await,"Xbox authorization").await.unwrap_err();assert!(e.contains("Create an Xbox profile"));let e=checked(response(401,r#"{"XErr":2148916238}"#).await,"Xbox authorization").await.unwrap_err();assert!(e.contains("family organizer"));}
    #[tokio::test]async fn missing_java_profile_has_specific_message(){let e=checked(response(404,"{}").await,"Minecraft profile").await.unwrap_err();assert!(e.contains("No Java Edition profile"));}
    #[test]fn private_token_structure_is_not_the_public_account(){let stored=Stored{refresh:"test-refresh".into(),access:"test-access".into(),expires:0,client_id:"test-app".into(),xuid:"test-xuid".into(),account:json!({"name":"Test","type":"microsoft","uuid":"test-uuid"})};let public=json!({"status":"complete","account":stored.account}).to_string();assert!(!public.contains("test-refresh"));assert!(!public.contains("test-access"));}
}
