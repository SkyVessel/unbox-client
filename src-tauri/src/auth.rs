//! Microsoft device authorization. Only public account metadata crosses IPC.
use crate::Runtime;
use base64::{engine::general_purpose::STANDARD, Engine};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{io::Cursor, time::{Duration, Instant, SystemTime, UNIX_EPOCH}};
const TOKEN: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const DEVICE: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const SCOPE: &str = "XboxLive.signin offline_access";
const SERVICE: &str = "dev.unbox.client.microsoft";
const VAULT: &str = "accounts-v2";
// Public application identifier shared by debug and release builds; never a secret.
const UNBOX_CLIENT_ID: &str = "0136aeab-bc7a-41b1-988e-08bf665fd53c";
const MAX_SKIN: usize = 1024 * 1024;
#[derive(Default)] pub struct AuthState { pending: Option<Pending>, generation: u64 }
struct Pending { id:String, device:String, client_id:String, expires:Instant, next:Instant, interval:u64, polling:bool }
#[derive(Clone, Serialize, Deserialize)]
struct Stored { refresh:String, access:String, expires:u64, client_id:String, xuid:String, account:Value }
#[derive(Clone, Default, Serialize, Deserialize)]
struct Vault { accounts:Vec<Stored>, selected:Option<String>, local:Option<String>, #[serde(default)] unbox:Vec<UnboxStored> }
#[derive(Clone,Serialize,Deserialize)]
struct UnboxStored { id:String, token:String, expires:u64, account:Value }
fn unbox_public(s:&UnboxStored)->Value{let mut a=s.account.clone();a["needsSignIn"]=json!(s.expires<=now()*1000);a}
pub struct Session { pub name:String, pub uuid:String, pub token:String, pub xuid:String, pub client_id:String }
struct Api { c:reqwest::Client, microsoft:String, xbox:String, xsts:String, minecraft:String }
impl Api {
    fn new()->Result<Self,String>{Ok(Self{c:client()?,microsoft:TOKEN.into(),xbox:"https://user.auth.xboxlive.com/user/authenticate".into(),xsts:"https://xsts.auth.xboxlive.com/xsts/authorize".into(),minecraft:"https://api.minecraftservices.com".into()})}
}
fn now()->u64{SystemTime::now().duration_since(UNIX_EPOCH).unwrap_or_default().as_secs()}
fn client()->Result<reqwest::Client,String>{reqwest::Client::builder().no_proxy().redirect(reqwest::redirect::Policy::none()).timeout(Duration::from_secs(25)).build().map_err(|_|"Could not initialize account connection".into())}
fn field<'a>(v:&'a Value,k:&str)->Result<&'a str,String>{v[k].as_str().filter(|s|!s.is_empty()).ok_or_else(||format!("The account service did not return {k}"))}
fn canonical_uuid(id:&str)->Result<String,String>{uuid::Uuid::parse_str(id).map(|u|u.simple().to_string()).map_err(|_|"Invalid Minecraft profile ID".into())}
fn key(s:&Stored)->String{format!("{}:{}",s.client_id,s.account["uuid"].as_str().unwrap_or_default())}
// Ignore legacy auth-config.json so an upgrade cannot keep using another application's grants.
fn client_id(_rt:&Runtime)->Result<String,String>{Ok(UNBOX_CLIENT_ID.into())}
fn provider_name(id:&str)->&'static str{if id==UNBOX_CLIENT_ID{"Unbox"}else{"Previous application"}}
fn public_account(s:&Stored,current:Option<&str>)->Value{
    let mut a=json!({"accountId":key(s),"name":s.account["name"],"uuid":s.account["uuid"],"type":"microsoft","skinModel":s.account["skinModel"],"provider":provider_name(&s.client_id),"needsSignIn":s.refresh.is_empty()||current!=Some(s.client_id.as_str())});
    for k in ["skin","skinStatus"]{if !s.account[k].is_null(){a[k]=s.account[k].clone();}}a
}
impl Vault {
    fn selected_account(&self,current:Option<&str>)->Value{
        if let Some(name)=&self.local{return json!({"name":name,"type":"local"})}
        if let Some(a)=self.unbox.iter().find(|a|a.account["accountId"].as_str()==self.selected.as_deref()){return unbox_public(a)}
        self.accounts.iter().find(|a|Some(key(a))==self.selected).map(|a|public_account(a,current)).unwrap_or(Value::Null)
    }
    fn upsert(&mut self,s:Stored,select:bool){let id=key(&s);self.accounts.retain(|a|key(a)!=id);self.accounts.push(s);if select{self.selected=Some(id);self.local=None;}}
    // Replace the same Minecraft identity only after fresh Unbox authorization has succeeded.
    fn authorize(&mut self,s:Stored){self.accounts.retain(|a|a.account["uuid"]!=s.account["uuid"]);self.upsert(s,true);}
    fn remove(&mut self,id:&str){self.unbox.retain(|s|s.account["accountId"]!=id);self.accounts.retain(|s|key(s)!=id);if self.selected.as_deref()==Some(id){self.selected=None;}}
    fn snapshot(&self,current:Option<&str>)->Value{json!({"accounts":self.accounts.iter().map(|s|public_account(s,current)).chain(self.unbox.iter().map(unbox_public)).collect::<Vec<_>>(),"account":self.selected_account(current)})}
}
#[cfg(target_os="macos")]
fn read_secure(name:&str)->Result<Option<Vec<u8>>,String>{crate::credential_cache::read(SERVICE,name,||match security_framework::passwords::get_generic_password(SERVICE,name){Ok(b)=>Ok(Some(b)),Err(e) if e.code()==-25300=>Ok(None),Err(_)=>Err("macOS Keychain is locked or access was denied. Allow Unbox access and retry.".into())})}
#[cfg(target_os="macos")]
fn save_vault(v:&Vault)->Result<(),String>{let bytes=serde_json::to_vec(v).map_err(|_|"Could not encode accounts")?;crate::credential_cache::write(SERVICE,VAULT,&bytes,||security_framework::passwords::set_generic_password(SERVICE,VAULT,&bytes).map_err(|_|"macOS Keychain could not save accounts. Allow Unbox access and retry.".into()))}
#[cfg(not(any(target_os="macos",target_os="windows")))]
fn read_secure(_: &str)->Result<Option<Vec<u8>>,String>{Err("Secure account storage is currently supported on macOS only".into())}
#[cfg(not(any(target_os="macos",target_os="windows")))]
fn save_vault(_: &Vault)->Result<(),String>{Err("Secure account storage is currently supported on macOS only".into())}
#[cfg(target_os="windows")]
fn read_secure(name:&str)->Result<Option<Vec<u8>>,String>{crate::windows_vault::read(SERVICE,name)}
#[cfg(target_os="windows")]
fn save_vault(v:&Vault)->Result<(),String>{crate::windows_vault::write(SERVICE,VAULT,&serde_json::to_vec(v).map_err(|_|"Could not encode accounts")?)}
fn load_vault()->Result<Vault,String>{
    if let Some(b)=read_secure(VAULT)?{return serde_json::from_slice(&b).map_err(|_|"Saved account storage is invalid; it has not been overwritten.".into())}
    let mut v=Vault::default();
    if let Some(b)=read_secure("primary")?{
        let mut s:Stored=serde_json::from_slice(&b).map_err(|_|"The previous account could not be migrated.".to_string())?;
        s.account["uuid"]=json!(canonical_uuid(field(&s.account,"uuid")?)?);v.upsert(s,true);save_vault(&v)?;
    }
    Ok(v)
}
fn remove_legacy()->Result<(),String>{
    #[cfg(target_os="macos")]
    match security_framework::passwords::delete_generic_password(SERVICE,"primary"){Ok(())=>{},Err(e) if e.code()==-25300=>{},Err(_)=>return Err("Could not remove the legacy credential from Keychain. Retry removing the account.".into())}
    Ok(())
}
pub fn restore_account(rt:&Runtime,previous:&Value)->Result<Value,String>{
    let v=load_vault()?;let account=v.selected_account(client_id(rt).ok().as_deref());
    // Old local-only installations have no vault. Do not restore stale Microsoft metadata.
    if account.is_null()&&read_secure(VAULT)?.is_none()&&previous["type"]=="local"{let mut migrated=v;migrated.local=Some(field(previous,"name")?.to_owned());save_vault(&migrated)?;return Ok(migrated.selected_account(None))}Ok(account)
}
#[tauri::command]
pub fn auth_info()->Value{json!({"configured":true,"clientId":UNBOX_CLIENT_ID,"provider":"Unbox"})}
#[tauri::command]
pub async fn auth_accounts(rt:tauri::State<'_,Runtime>)->Result<Value,String>{let _guard=rt.auth.lock().await;Ok(load_vault()?.snapshot(client_id(&rt).ok().as_deref()))}
#[tauri::command]
pub async fn auth_select(rt:tauri::State<'_,Runtime>,account_id:String)->Result<Value,String>{
    let mut a=rt.auth.lock().await;let mut v=load_vault()?;
    if !v.accounts.iter().any(|s|key(s)==account_id)&&!v.unbox.iter().any(|s|s.account["accountId"]==account_id){return Err("Saved account not found. Sign in again.".into())}
    a.generation+=1;a.pending=None;v.selected=Some(account_id);v.local=None;save_vault(&v)?;Ok(v.snapshot(client_id(&rt).ok().as_deref()))
}
#[tauri::command]
pub async fn auth_local(rt:tauri::State<'_,Runtime>,name:String)->Result<Value,String>{
    if name.is_empty()||name.len()>16||!name.bytes().all(|b|b.is_ascii_alphanumeric()||b==b'_'){return Err("Use 1–16 letters, numbers or underscores".into())}
    let mut a=rt.auth.lock().await;a.generation+=1;a.pending=None;let mut v=load_vault()?;v.local=Some(name);v.selected=None;save_vault(&v)?;Ok(v.snapshot(client_id(&rt).ok().as_deref()))
}
#[tauri::command]
pub async fn auth_begin(rt:tauri::State<'_,Runtime>)->Result<Value,String>{
    let (client_id,generation)={let mut a=rt.auth.lock().await;let id=client_id(&rt)?;a.generation+=1;a.pending=None;(id,a.generation)};
    let r=client()?.post(DEVICE).form(&[("client_id",client_id.as_str()),("scope",SCOPE)]).send().await.map_err(|_|"Could not reach Microsoft. Check your connection.")?;
    let v=checked(r,"Microsoft authorization").await?;
    let code=field(&v,"user_code")?.to_owned();let device=field(&v,"device_code")?.to_owned();let interval=v["interval"].as_u64().unwrap_or(5).clamp(5,60);let seconds=v["expires_in"].as_u64().unwrap_or(900).clamp(1,1800);let id=uuid::Uuid::new_v4().to_string();
    let mut a=rt.auth.lock().await;if a.generation!=generation{return Err("Sign-in canceled".into())}
    a.pending=Some(Pending{id:id.clone(),device,client_id:client_id.clone(),expires:Instant::now()+Duration::from_secs(seconds),next:Instant::now()+Duration::from_secs(interval),interval,polling:false});
    Ok(json!({"id":id,"code":code,"interval":interval,"expiresIn":seconds,"url":"https://www.microsoft.com/link","provider":provider_name(&client_id)}))
}
#[tauri::command]
pub fn auth_open()->Result<(),String>{
    #[cfg(target_os="macos")]let program="open";
    #[cfg(target_os="windows")]let program="explorer";
    #[cfg(target_os="linux")]let program="xdg-open";
    std::process::Command::new(program).arg("https://www.microsoft.com/link").spawn().map_err(|_|"Could not open your browser")?;Ok(())
}
#[tauri::command]
pub async fn auth_cancel(rt:tauri::State<'_,Runtime>)->Result<(),String>{let mut a=rt.auth.lock().await;a.generation+=1;a.pending=None;Ok(())}
#[tauri::command]
pub async fn auth_poll(rt:tauri::State<'_,Runtime>,id:String)->Result<Value,String>{
    let mut a=rt.auth.lock().await;let generation=a.generation;let p=a.pending.as_mut().filter(|p|p.id==id).ok_or("Sign-in was canceled. Start again.")?;
    if Instant::now()>=p.expires{a.pending=None;return Err("Sign-in code expired. Start again.".into())}
    if p.polling||Instant::now()<p.next{return Ok(json!({"status":"pending","interval":p.interval}))}
    p.polling=true;let client_id=p.client_id.clone();let device=p.device.clone();drop(a);
    let api=Api::new()?;let res=api.c.post(&api.microsoft).form(&[("client_id",client_id.as_str()),("grant_type","urn:ietf:params:oauth:grant-type:device_code"),("device_code",device.as_str())]).send().await;
    let mut a=rt.auth.lock().await;if a.generation!=generation{return Err("Sign-in canceled".into())}let p=a.pending.as_mut().filter(|p|p.id==id).ok_or("Sign-in canceled")?;p.polling=false;p.next=Instant::now()+Duration::from_secs(p.interval);
    let r=match res{Ok(r)=>r,Err(_)=>return Ok(json!({"status":"pending","interval":p.interval,"message":"Connection interrupted. Retrying…"}))};
    let status=r.status();let v:Value=r.json().await.map_err(|_|"Invalid Microsoft response. Start again.")?;
    match v["error"].as_str(){
        Some("authorization_pending")=>return Ok(json!({"status":"pending","interval":p.interval})),
        Some("slow_down")=>{p.interval=(p.interval+5).min(60);p.next=Instant::now()+Duration::from_secs(p.interval);return Ok(json!({"status":"pending","interval":p.interval}));},
        Some(_)=>{a.pending=None;return Err("Sign-in declined or expired. Start again when ready.".into());},None=>{}
    }
    if !status.is_success(){a.pending=None;return Err(format!("Microsoft sign-in failed (HTTP {})",status.as_u16()))}
    a.pending=None;drop(a);
    let saved=minecraft(&api,&v,&client_id).await?;
    let a=rt.auth.lock().await;if a.generation!=generation{return Err("Sign-in canceled".into())}
    let mut vault=load_vault()?;vault.authorize(saved);save_vault(&vault)?;
    let mut result=vault.snapshot(Some(&client_id));result["status"]=json!("complete");Ok(result)
}
async fn checked(r:reqwest::Response,stage:&str)->Result<Value,String>{
    let status=r.status();let v:Value=r.json().await.unwrap_or(Value::Null);
    if status.is_success(){return if v.is_null(){Err(format!("{stage}: invalid response"))}else{Ok(v)}}
    let registration=["error","errorMessage","developerMessage"].iter().filter_map(|k|v[*k].as_str()).any(|s|{let s=s.to_ascii_lowercase();s.contains("app registration")||s.contains("appreginfo")||s.contains("mce-reviewappid")});
    let msg=match v["XErr"].as_u64(){
        Some(2148916233)=>"Create an Xbox profile at xbox.com first",Some(2148916238)=>"Your Microsoft family organizer must allow Xbox access",
        _=>if stage=="Minecraft authentication"&&status.as_u16()==403&&registration{"This Microsoft application has not been approved for Minecraft Services. Application registration approval is required."}
        else if status.as_u16()==401||v["error"]=="invalid_grant"{"Your session expired. Sign in again."}
        else if stage=="Minecraft profile"&&status.as_u16()==404{"No Java Edition profile found. Check ownership and create your profile at minecraft.net."}
        else if status.as_u16()==429{"Too many requests. Wait a moment before retrying."}
        else{"The account service rejected the request. Please retry."}
    };Err(format!("{stage} (HTTP {}): {msg}",status.as_u16()))
}
async fn post(c:&reqwest::Client,url:&str,body:Value,stage:&str)->Result<Value,String>{checked(c.post(url).header("x-xbl-contract-version","1").json(&body).send().await.map_err(|_|format!("{stage}: connection failed"))?,stage).await}
async fn minecraft(api:&Api,oauth:&Value,client_id:&str)->Result<Stored,String>{
    let user=post(&api.c,&api.xbox,json!({"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":format!("d={}",field(oauth,"access_token")?)},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}),"Xbox sign-in").await?;
    let x=post(&api.c,&api.xsts,json!({"Properties":{"SandboxId":"RETAIL","UserTokens":[field(&user,"Token")?]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}),"Xbox authorization").await?;
    let claims=&x["DisplayClaims"]["xui"][0];let mc=post(&api.c,&format!("{}/authentication/login_with_xbox",api.minecraft),json!({"identityToken":format!("XBL3.0 x={};{}",field(claims,"uhs")?,field(&x,"Token")?)}),"Minecraft authentication").await?;
    let access=field(&mc,"access_token")?.to_owned();
    let ent=checked(api.c.get(format!("{}/entitlements/mcstore",api.minecraft)).bearer_auth(&access).send().await.map_err(|_|"Ownership check: connection failed")?,"Ownership check").await?;
    if !owns_java(&ent){return Err("This account does not have an active Minecraft Java Edition entitlement.".into())}
    let account=profile(api,&access,None).await?;
    Ok(Stored{refresh:field(oauth,"refresh_token")?.to_owned(),access,expires:now()+mc["expires_in"].as_u64().unwrap_or(3600),client_id:client_id.to_owned(),xuid:claims["xid"].as_str().unwrap_or("").to_owned(),account})
}
fn owns_java(ent:&Value)->bool{ent["items"].as_array().is_some_and(|a|a.iter().any(|v|matches!(v["name"].as_str(),Some("game_minecraft"|"product_minecraft"))))}
async fn profile(api:&Api,access:&str,expected:Option<&str>)->Result<Value,String>{
    let p=checked(api.c.get(format!("{}/minecraft/profile",api.minecraft)).bearer_auth(access).send().await.map_err(|_|"Profile connection failed")?,"Minecraft profile").await?;
    account_from_profile(&api.c,&p,expected).await
}
async fn account_from_profile(c:&reqwest::Client,p:&Value,expected:Option<&str>)->Result<Value,String>{
    let id=canonical_uuid(field(p,"id")?)?;if expected.is_some_and(|e|e!=id){return Err("Microsoft returned a different account. Sign in again.".into())}
    let mut a=json!({"name":field(p,"name")?,"uuid":id,"type":"microsoft","skinModel":"classic","skinStatus":"unavailable"});
    if let Some(skin)=p["skins"].as_array().and_then(|arr|arr.iter().find(|s|s["state"]=="ACTIVE")){
        a["skinModel"]=json!(if skin["variant"]=="SLIM"{"slim"}else{"classic"});
        if let Some(url)=skin["url"].as_str(){
            let url=url.strip_prefix("http://textures.minecraft.net/texture/").map(|s|format!("https://textures.minecraft.net/texture/{s}")).unwrap_or_else(||url.to_owned());
            if let Ok(bytes)=download_skin(c,&url).await{a["skin"]=json!(format!("data:image/png;base64,{}",STANDARD.encode(bytes)));a["skinStatus"]=json!("ready");}
        }
    }
    Ok(a)
}
fn skin_url(url:&str)->bool{reqwest::Url::parse(url).is_ok_and(|u|u.scheme()=="https"&&u.host_str()==Some("textures.minecraft.net")&&u.username().is_empty()&&u.password().is_none()&&u.port().is_none()&&u.query().is_none()&&u.fragment().is_none()&&u.path().starts_with("/texture/"))}
async fn download_skin(c:&reqwest::Client,url:&str)->Result<Vec<u8>,String>{
    if !skin_url(url){return Err("Unsupported skin source".into())}
    let mut r=c.get(url).send().await.map_err(|_|"Skin download failed")?;
    if !r.status().is_success()||r.content_length().unwrap_or(0)>MAX_SKIN as u64{return Err("Skin download failed".into())}
    let mut bytes=Vec::new();while let Some(chunk)=r.chunk().await.map_err(|_|"Skin download interrupted")?{if bytes.len()+chunk.len()>MAX_SKIN{return Err("Skin file is too large".into())}bytes.extend_from_slice(&chunk);}
    validate_png(&bytes,"classic")?;Ok(bytes)
}
fn validate_png(bytes:&[u8],model:&str)->Result<(),String>{
    if !["classic","slim"].contains(&model){return Err("Choose Classic or Slim arms".into())}
    if bytes.len()>MAX_SKIN||!bytes.starts_with(b"\x89PNG\r\n\x1a\n"){return Err("Choose a PNG skin under 1 MB".into())}
    let mut decoder=png::Decoder::new(Cursor::new(bytes));decoder.set_limits(png::Limits{bytes:MAX_SKIN});
    let mut reader=decoder.read_info().map_err(|_|"Invalid PNG skin")?;let info=reader.info();
    if info.width!=64||![32,64].contains(&info.height)||info.animation_control.is_some(){return Err("Skins must be a static 64 × 64 or 64 × 32 PNG".into())}
    if model=="slim"&&info.height==32{return Err("Slim skins must be 64 × 64".into())}
    let mut output=vec![0;reader.output_buffer_size()];reader.next_frame(&mut output).map_err(|_|"PNG skin is damaged")?;Ok(())
}
async fn refresh(api:&Api,s:&Stored)->Result<Stored,String>{
    let r=api.c.post(&api.microsoft).form(&[("client_id",s.client_id.as_str()),("grant_type","refresh_token"),("refresh_token",s.refresh.as_str()),("scope",SCOPE)]).send().await.map_err(|_|"Could not refresh your account. Check your connection.")?;
    let mut oauth=checked(r,"Microsoft session refresh").await?;
    if oauth["refresh_token"].is_null(){oauth["refresh_token"]=json!(s.refresh)}
    Ok(Stored{refresh:field(&oauth,"refresh_token")?.into(),access:field(&oauth,"access_token")?.into(),..s.clone()})
}
// Must be called under rt.auth's mutex: refresh rotation, removal and uploads cannot race.
async fn usable(rt:&Runtime,v:&mut Vault,id:&str,api:&Api)->Result<Stored,String>{
    let current=client_id(rt)?;let mut s=v.accounts.iter().find(|s|key(s)==id).cloned().ok_or("Saved account not found. Sign in again.")?;
    if s.client_id!=current||s.refresh.is_empty(){return Err("Sign in with Microsoft again to authorize Unbox. Your previous application login cannot be reused.".into())}
    if s.expires<=now()+120{
        let tokens=match refresh(api,&s).await{Ok(t)=>t,Err(e)=>{if e.contains("Sign in again"){s.refresh.clear();s.access.clear();s.expires=0;v.upsert(s,false);save_vault(v)?;}return Err(e)}};
        // Persist the rotated refresh token even when a later Xbox/Minecraft request fails.
        s.refresh=tokens.refresh.clone();s.expires=0;v.upsert(s.clone(),false);save_vault(v)?;
        let next=minecraft(api,&json!({"access_token":tokens.access,"refresh_token":tokens.refresh}),&s.client_id).await?;
        if next.account["uuid"]!=s.account["uuid"]{return Err("Microsoft returned a different account. Sign in again.".into())}
        s=next;v.upsert(s.clone(),false);save_vault(v)?;
    }Ok(s)
}
pub async fn launch_account(rt:&Runtime)->Result<(Option<Session>,String),String>{
    let _guard=rt.auth.lock().await;let mut v=load_vault()?;
    if let Some(name)=v.local{return Ok((None,name))}
    let id=v.selected.clone().ok_or("Select an account before launching")?;
    if let Some(u)=v.unbox.iter_mut().find(|s|s.account["accountId"]==id){if u.expires<=now()*1000{return Err("Unbox session expired. Sign in again.".into())}
     let response=unbox_request(rt,"accounts/profile",json!({}),Some(&u.token)).await?;
     if response["account"]["uuid"]!=u.account["uuid"]||response["account"]["accountId"]!=id{return Err("Account identity changed".into())}
     u.account=response["account"].clone();let session=Session{name:field(&u.account,"name")?.into(),uuid:field(&u.account,"uuid")?.into(),token:"0".into(),xuid:String::new(),client_id:String::new()};save_vault(&v)?;return Ok((Some(session),String::new()))}
    let s=usable(rt,&mut v,&id,&Api::new()?).await?;
    Ok((Some(Session{name:field(&s.account,"name")?.into(),uuid:field(&s.account,"uuid")?.into(),token:s.access,xuid:s.xuid,client_id:s.client_id}),String::new()))
}
#[tauri::command]
pub async fn auth_refresh_profile(rt:tauri::State<'_,Runtime>,account_id:String)->Result<Value,String>{
    let _guard=rt.auth.lock().await;let mut v=load_vault()?;let api=Api::new()?;let mut s=usable(&rt,&mut v,&account_id,&api).await?;
    let mut a=profile(&api,&s.access,s.account["uuid"].as_str()).await?;
    if a["skin"].is_null()&&!s.account["skin"].is_null(){a["skin"]=s.account["skin"].clone();a["skinModel"]=s.account["skinModel"].clone();a["skinStatus"]=json!("cached");}
    s.account=a;v.upsert(s,false);save_vault(&v)?;Ok(v.snapshot(client_id(&rt).ok().as_deref()))
}
async fn upload_skin(api:&Api,s:&Stored,bytes:Vec<u8>,model:&str)->Result<Value,String>{
    validate_png(&bytes,model)?;
    let form=reqwest::multipart::Form::new().text("variant",model.to_owned()).part("file",reqwest::multipart::Part::bytes(bytes.clone()).file_name("skin.png").mime_str("image/png").map_err(|_|"Could not prepare PNG")?);
    let p=checked(api.c.post(format!("{}/minecraft/profile/skins",api.minecraft)).bearer_auth(&s.access).multipart(form).send().await.map_err(|_|"Skin upload connection failed. Refresh your skin before retrying.")?,"Skin upload").await?;
    // Show the submitted pixels only after the service confirms this exact account's update.
    if canonical_uuid(field(&p,"id")?)?!=s.account["uuid"]{return Err("Skin service returned a different account".into())}
    let mut a=s.account.clone();a["skin"]=json!(format!("data:image/png;base64,{}",STANDARD.encode(bytes)));a["skinModel"]=json!(model);a["skinStatus"]=json!("ready");Ok(a)
}
#[tauri::command]
pub async fn auth_upload_skin(rt:tauri::State<'_,Runtime>,account_id:String,png_base64:String,model:String)->Result<Value,String>{
    if png_base64.len()>MAX_SKIN*4/3+4{return Err("Choose a PNG skin under 1 MB".into())}
    let bytes=STANDARD.decode(png_base64).map_err(|_|"Invalid PNG data")?;validate_png(&bytes,&model)?;
    let _guard=rt.auth.lock().await;let mut v=load_vault()?;let api=Api::new()?;let mut s=usable(&rt,&mut v,&account_id,&api).await?;
    s.account=upload_skin(&api,&s,bytes,&model).await?;v.upsert(s,false);save_vault(&v).map_err(|_|"Skin changed on Minecraft, but Keychain could not cache it. Refresh your skin after unlocking Keychain.")?;
    Ok(v.snapshot(client_id(&rt).ok().as_deref()))
}
#[tauri::command]
pub async fn auth_sign_out(rt:tauri::State<'_,Runtime>,account_id:String)->Result<Value,String>{
    let mut a=rt.auth.lock().await;a.generation+=1;a.pending=None;let mut v=load_vault()?;if let Some(u)=v.unbox.iter().find(|s|s.account["accountId"]==account_id){if u.expires>now()*1000{if let Err(e)=unbox_request(&rt,"accounts/logout",json!({}),Some(&u.token)).await{if e!="Sign in to Unbox friends again"{return Err(e)}}}}remove_legacy()?;v.remove(&account_id);save_vault(&v)?;Ok(v.snapshot(client_id(&rt).ok().as_deref()))
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
    #[tokio::test]async fn generic_minecraft_forbidden_is_not_reported_as_registration_denial(){
        let e=checked(response(403,r#"{"error":"Forbidden","errorMessage":"private server details"}"#).await,"Minecraft authentication").await.unwrap_err();
        assert!(e.contains("HTTP 403"));assert!(!e.contains("not been approved"));assert!(!e.contains("private server details"));
    }
    #[tokio::test]async fn non_json_failure_retains_stage_and_http_status_without_body(){
        let e=checked(response(502,"private gateway details").await,"Xbox sign-in").await.unwrap_err();
        assert!(e.contains("Xbox sign-in"));assert!(e.contains("HTTP 502"));assert!(!e.contains("private gateway details"));
    }
    #[tokio::test]async fn xbox_profile_and_family_errors_are_distinguished(){let e=checked(response(401,r#"{"XErr":2148916233}"#).await,"Xbox authorization").await.unwrap_err();assert!(e.contains("Create an Xbox profile"));let e=checked(response(401,r#"{"XErr":2148916238}"#).await,"Xbox authorization").await.unwrap_err();assert!(e.contains("family organizer"));}
    #[tokio::test]async fn missing_java_profile_has_specific_message(){let e=checked(response(404,"{}").await,"Minecraft profile").await.unwrap_err();assert!(e.contains("No Java Edition profile"));}
    #[test]fn private_token_structure_is_not_the_public_account(){let stored=Stored{refresh:"test-refresh".into(),access:"test-access".into(),expires:0,client_id:"test-app".into(),xuid:"test-xuid".into(),account:json!({"name":"Test","type":"microsoft","uuid":"test-uuid"})};let public=json!({"status":"complete","account":stored.account}).to_string();assert!(!public.contains("test-refresh"));assert!(!public.contains("test-access"));}
}

#[cfg(test)]
mod lifecycle_tests {
    use super::*;
    const PLAYER:&str="00000000000040008000000000000001";
    fn saved(id:&str,provider:&str)->Stored { Stored{refresh:"refresh-private".into(),access:"mc-private".into(),expires:now()+3600,client_id:provider.into(),xuid:"xuid".into(),account:json!({"name":"Fixture","uuid":id,"skinModel":"classic","access_token":"must-not-leak"})} }
    fn png_skin(w:u32,h:u32)->Vec<u8>{let mut bytes=Vec::new();{let mut e=png::Encoder::new(&mut bytes,w,h);e.set_color(png::ColorType::Rgba);e.set_depth(png::BitDepth::Eight);e.write_header().unwrap().write_image_data(&vec![180;(w*h*4)as usize]).unwrap();}bytes}
    #[test]
    fn vault_roundtrip_switch_remove_and_provider_isolation(){
        let a=saved(PLAYER,"app-a");let b=saved("00000000000040008000000000000002","app-a");let mut v=Vault::default();v.upsert(a.clone(),true);v.upsert(b.clone(),false);
        let mut restored:Vault=serde_json::from_slice(&serde_json::to_vec(&v).unwrap()).unwrap();assert_eq!(restored.selected_account(Some("app-a"))["uuid"],PLAYER);
        restored.selected=Some(key(&b));assert_eq!(restored.selected_account(Some("app-a"))["uuid"],b.account["uuid"]);
        restored.remove(&key(&a));assert_eq!(restored.selected,Some(key(&b)));restored.remove(&key(&b));assert!(restored.selected_account(Some("app-a")).is_null());
        v.upsert(saved(PLAYER,"app-b"),true);assert_eq!(v.accounts.len(),3);assert_eq!(v.snapshot(Some("app-b"))["accounts"][0]["needsSignIn"],true);
        let public=v.snapshot(Some("app-b")).to_string();for secret in ["refresh-private","mc-private","must-not-leak"]{assert!(!public.contains(secret));}
        v.local=Some("LocalPlayer".into());assert_eq!(v.selected_account(Some("app-b"))["type"],"local");assert_eq!(v.accounts.len(),3);

    }
    #[tokio::test]
    async fn unbox_registration_ignores_old_config_and_requires_fresh_authorization(){
        let root=std::env::temp_dir().join(uuid::Uuid::new_v4().to_string());std::fs::create_dir_all(&root).unwrap();
        let rt=Runtime{root:root.clone(),resources:root.clone(),job:std::sync::Arc::default(),auth:std::sync::Arc::default(),social:std::sync::Arc::default()};
        assert_eq!(client_id(&rt).unwrap(),UNBOX_CLIENT_ID);
        std::fs::write(root.join("auth-config.json"),r#"{"clientId":"170105bd-9573-4222-b09c-6f24c3b77cd8"}"#).unwrap();
        assert_eq!(client_id(&rt).unwrap(),UNBOX_CLIENT_ID);assert_eq!(auth_info()["configured"],true);
        let old=saved(PLAYER,"170105bd-9573-4222-b09c-6f24c3b77cd8");let mut v=Vault::default();v.upsert(old.clone(),true);
        assert_eq!(v.selected_account(Some(UNBOX_CLIENT_ID))["needsSignIn"],true);
        assert!(usable(&rt,&mut v,&key(&old),&Api::new().unwrap()).await.err().unwrap().contains("authorize Unbox"));
        assert_eq!(v.accounts.len(),1);assert_eq!(v.accounts[0].refresh,"refresh-private");
        v.upsert(saved("00000000000040008000000000000002","old-app"),false);
        v.authorize(saved(PLAYER,UNBOX_CLIENT_ID));assert_eq!(v.accounts.len(),2);
        let restored:Vault=serde_json::from_slice(&serde_json::to_vec(&v).unwrap()).unwrap();
        assert_eq!(restored.selected_account(Some(UNBOX_CLIENT_ID))["needsSignIn"],false);
        assert_eq!(restored.selected_account(Some(UNBOX_CLIENT_ID))["uuid"],PLAYER);
        std::fs::remove_dir_all(root).unwrap();
    }
    #[test]
    fn skin_validation_decodes_pixels_and_rejects_wrong_sizes_and_legacy_slim(){
        assert!(validate_png(&png_skin(64,64),"slim").is_ok());assert!(validate_png(&png_skin(64,32),"classic").is_ok());
        assert!(validate_png(&png_skin(64,32),"slim").is_err());assert!(validate_png(&png_skin(65,64),"classic").is_err());
        let mut broken=png_skin(64,64);broken.truncate(45);assert!(validate_png(&broken,"classic").is_err());assert!(validate_png(b"not png","classic").is_err());
    }
    // Real HTTP serialization against a local server; never touches user credentials or remote accounts.
    async fn mock(replies:Vec<(u16,Value)>)->(Api,tokio::task::JoinHandle<Vec<Vec<u8>>>){
        use tokio::io::{AsyncReadExt,AsyncWriteExt};
        let l=tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();let base=format!("http://{}",l.local_addr().unwrap());
        let task=tokio::spawn(async move{let mut requests=Vec::new();for(status,body)in replies{
            let(mut s,_)=l.accept().await.unwrap();let mut bytes=Vec::new();let mut chunk=[0u8;4096];loop{
                let n=s.read(&mut chunk).await.unwrap();assert!(n>0);bytes.extend_from_slice(&chunk[..n]);
                if let Some(end)=bytes.windows(4).position(|w|w==b"\r\n\r\n"){
                    let headers=String::from_utf8_lossy(&bytes[..end]).to_lowercase();let size=headers.lines().find_map(|line|line.strip_prefix("content-length:").and_then(|v|v.trim().parse::<usize>().ok())).unwrap_or(0);
                    if bytes.len()>=end+4+size{break;}
                }
            }requests.push(bytes);let body=body.to_string();s.write_all(format!("HTTP/1.1 {status} Test\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{body}",body.len()).as_bytes()).await.unwrap();
        }requests});
        (Api{c:client().unwrap(),microsoft:format!("{base}/token"),xbox:format!("{base}/xbox"),xsts:format!("{base}/xsts"),minecraft:base},task)
    }
    #[tokio::test]
    async fn refresh_and_full_auth_chain_keep_tokens_in_correct_stages(){
        let(api,requests)=mock(vec![(200,json!({"access_token":"ms-new","refresh_token":"refresh-rotated"})),(200,json!({"Token":"xbox-token"})),(200,json!({"Token":"xsts-token","DisplayClaims":{"xui":[{"uhs":"hash","xid":"123"}]}})),(200,json!({"access_token":"minecraft-new","expires_in":86400})),(200,json!({"items":[{"name":"game_minecraft"}]})),(200,json!({"id":PLAYER,"name":"Fixture","skins":[]}))]).await;
        let s=refresh(&api,&saved(PLAYER,UNBOX_CLIENT_ID)).await.unwrap();assert_eq!(s.refresh,"refresh-rotated");
        let result=minecraft(&api,&json!({"access_token":s.access,"refresh_token":s.refresh}),UNBOX_CLIENT_ID).await.unwrap();assert_eq!(result.access,"minecraft-new");assert_eq!(result.refresh,"refresh-rotated");assert_eq!(result.account["uuid"],PLAYER);
        let req=requests.await.unwrap().into_iter().map(|b|String::from_utf8(b).unwrap()).collect::<Vec<_>>();
        assert!(req[0].contains("grant_type=refresh_token"));assert!(req[0].contains(UNBOX_CLIENT_ID));assert!(req[1].contains("d=ms-new"));assert!(req[2].contains("xbox-token"));assert!(req[3].contains("XBL3.0 x=hash;xsts-token"));
        assert!(req[4].contains("Bearer minecraft-new"));assert!(req[5].contains("Bearer minecraft-new"));assert!(!req[5].contains("refresh-rotated"));
    }
    #[tokio::test]
    async fn upload_uses_minecraft_token_and_only_returns_preview_after_confirmation(){
        let(api,request)=mock(vec![(200,json!({"id":PLAYER}))]).await;let bytes=png_skin(64,64);
        let result=upload_skin(&api,&saved(PLAYER,UNBOX_CLIENT_ID),bytes.clone(),"slim").await.unwrap();assert_eq!(result["skinModel"],"slim");assert_eq!(result["skin"],format!("data:image/png;base64,{}",STANDARD.encode(&bytes)));
        let req=request.await.unwrap().remove(0);let text=String::from_utf8_lossy(&req);assert!(text.starts_with("POST /minecraft/profile/skins"));assert!(text.contains("Bearer mc-private"));assert!(text.contains("name=\"variant\"\r\n\r\nslim"));assert!(text.contains("filename=\"skin.png\""));assert!(!text.contains("refresh-private"));assert!(req.windows(bytes.len()).any(|w|w==bytes));
        let(api,task)=mock(vec![(200,json!({"id":"00000000000040008000000000000002"}))]).await;assert!(upload_skin(&api,&saved(PLAYER,UNBOX_CLIENT_ID),bytes,"classic").await.unwrap_err().contains("different account"));task.await.unwrap();
    }
    #[tokio::test]
    async fn refresh_revocation_and_missing_entitlement_are_actionable(){
        let(api,t)=mock(vec![(400,json!({"error":"invalid_grant","error_description":"private details"}))]).await;
        let e=refresh(&api,&saved(PLAYER,UNBOX_CLIENT_ID)).await.err().unwrap();assert!(e.contains("Sign in again"));assert!(!e.contains("private details"));t.await.unwrap();
        assert!(!owns_java(&json!({"items":[{"name":"other_product"}]})));assert!(!owns_java(&json!({"items":[]})));
    }
}

// Social proof goes only to Mojang. The friends service receives a hasJoined challenge, never this token.
pub async fn prove_social(rt:&Runtime,challenge:&str)->Result<(),String>{
 if challenge.len()!=32||!challenge.bytes().all(|b|b.is_ascii_hexdigit()){return Err("Invalid account challenge".into())}
 let (session,_)=launch_account(rt).await?;let s=session.ok_or("Microsoft account required")?;
 let response=client()?.post("https://sessionserver.mojang.com/session/minecraft/join").json(&json!({"accessToken":s.token,"selectedProfile":s.uuid,"serverId":challenge})).send().await.map_err(|_|"Minecraft account verification connection failed")?;
 if !response.status().is_success(){return Err(format!("Minecraft account verification failed (HTTP {})",response.status()))}Ok(())
}

// The private player key and Microsoft tokens never leave this process for social proof.
// Only Mojang's certificate and a signature of the Unbox challenge go to our Worker.
pub async fn social_player_proof(rt:&Runtime,challenge:&str,id:&str,token:&str)->Result<Value,String>{
 if challenge.len()!=32||!challenge.bytes().all(|b|b.is_ascii_hexdigit()){return Err("Invalid account challenge".into())}
 let (session,_)=launch_account(rt).await?;let s=session.ok_or("Microsoft account required")?;
 let response=client()?.post("https://api.minecraftservices.com/player/certificates").bearer_auth(&s.token).send().await.map_err(|_|"Minecraft certificate service is unreachable")?;
 if !response.status().is_success(){return Err(format!("Minecraft certificate request failed (HTTP {})",response.status()))}
 let cert:Value=response.json().await.map_err(|_|"Invalid Minecraft certificate response")?;
 make_social_proof(&cert,challenge,id,token,&s.uuid)
}
fn make_social_proof(cert:&Value,challenge:&str,id:&str,token:&str,uuid:&str)->Result<Value,String>{
 use sha2::{Digest,Sha256};
 use ring::{rand::SystemRandom,signature::{RsaKeyPair,RSA_PKCS1_SHA256}};
 let decode=|pem:&str|STANDARD.decode(pem.lines().filter(|l|!l.starts_with("-----")).collect::<String>()).map_err(|_|"Invalid Minecraft player key".to_owned());
 let private=decode(field(&cert["keyPair"],"privateKey")?)?;
 let key=RsaKeyPair::from_pkcs8(&private).map_err(|_|"Invalid Minecraft player key")?;
 let public=decode(field(&cert["keyPair"],"publicKey")?)?;
 let token_hash=format!("{:x}",Sha256::digest(token.as_bytes()));
 let message=["Unbox friends certificate proof v1",id,&token_hash,challenge,uuid].join("\n");
 let mut signature=vec![0;key.public().modulus_len()];
 key.sign(&RSA_PKCS1_SHA256,&SystemRandom::new(),message.as_bytes(),&mut signature).map_err(|_|"Could not sign friend verification")?;
 Ok(json!({"version":1,"publicKey":STANDARD.encode(public),"certificateSignature":field(cert,"publicKeySignatureV2")?,"expiresAt":field(cert,"expiresAt")?,"signature":STANDARD.encode(signature)}))
}

// Passwords are sent over HTTPS and never persisted; only session tokens enter the Keychain vault.
async fn unbox_request(rt:&Runtime,path:&str,b:Value,token:Option<&str>)->Result<Value,String>{
 let mut r=client()?.post(format!("{}/v1/{path}",crate::social::endpoint(rt)?)).json(&b);if let Some(t)=token{r=r.bearer_auth(t)}
 let r=r.send().await.map_err(|_|"Unbox account service is unreachable")?;let status=r.status();let v:Value=r.json().await.map_err(|_|"Invalid account service response")?;
 if !status.is_success(){return Err(if v["error"]=="Unauthorized"&&path!="accounts/logout"{"Account registration is awaiting the service update.".into()}else{v["error"].as_str().unwrap_or("Account request failed").chars().take(160).collect()})}Ok(v)
}
#[tauri::command]
pub async fn auth_unbox_password(rt:tauri::State<'_,Runtime>,email:String,password:String,mode:String)->Result<Value,String>{
 let path=match mode.as_str(){"register"=>"accounts/register","login"=>"accounts/login",_=>return Err("Invalid account action".into())};
 if password.chars().count()<12||password.len()>512{return Err("Use a password with 12–128 characters".into())}
 let mut state=rt.auth.lock().await;
 let response=unbox_request(&rt,path,json!({"email":email,"password":password}),None).await?;
 let u:UnboxStored=serde_json::from_value(response).map_err(|_|"Invalid Unbox account response")?;
 if u.account["type"]!="unbox"||u.token.len()!=64||canonical_uuid(field(&u.account,"uuid")?).is_err(){return Err("Invalid Unbox identity".into())}
 let mut vault=load_vault()?;vault.unbox.retain(|s|s.id!=u.id);vault.selected=Some(field(&u.account,"accountId")?.into());vault.local=None;vault.unbox.push(u);save_vault(&vault)?;
 state.generation+=1;state.pending=None;Ok(vault.snapshot(client_id(&rt).ok().as_deref()))
}
#[tauri::command]
pub async fn auth_unbox_rename(rt:tauri::State<'_,Runtime>,account_id:String,name:String)->Result<Value,String>{
 if rt.job.lock().unwrap()["busy"]==true{return Err("Close the game before changing your player name".into())}
 let _guard=rt.auth.lock().await;let mut v=load_vault()?;
 let u=v.unbox.iter_mut().find(|s|s.account["accountId"]==account_id).ok_or("Unbox account not found")?;
 let response=unbox_request(&rt,"accounts/profile",json!({"name":name}),Some(&u.token)).await?;
 if response["account"]["uuid"]!=u.account["uuid"]||response["account"]["accountId"]!=u.account["accountId"]{return Err("Account identity changed".into())}
 u.account=response["account"].clone();save_vault(&v)?;Ok(v.snapshot(client_id(&rt).ok().as_deref()))
}
pub async fn sync_unbox_name(rt:&Runtime,id:&str,name:&str)->Result<(),String>{
 if !(3..=16).contains(&name.len())||!name.bytes().all(|b|b.is_ascii_alphanumeric()||b==b'_'){return Err("Invalid player name".into())}
 let _guard=rt.auth.lock().await;let mut v=load_vault()?;if let Some(u)=v.unbox.iter_mut().find(|s|s.account["accountId"]==id){if u.account["name"]!=name{u.account["name"]=json!(name);save_vault(&v)?;}}Ok(())
}
pub fn unbox_identity(account:&Value)->Result<(String,String),String>{let vault=load_vault()?;let u=vault.unbox.iter().find(|u|u.account["accountId"]==account["accountId"]).ok_or("Sign in with Unbox again")?;if u.expires<=now()*1000{return Err("Unbox session expired. Sign in again.".into())}Ok((u.id.clone(),u.token.clone()))}

#[cfg(test)]mod unbox_tests{
 use super::*;
 #[test]fn verified_identity_roundtrips_without_leaking_session_and_old_vaults_migrate(){
  let old:Vault=serde_json::from_str(r#"{"accounts":[],"selected":null,"local":"LocalPlayer"}"#).unwrap();assert!(old.unbox.is_empty());
  let mut v=Vault::default();let id="unbox:00000000-0000-4000-8000-000000000001";
  v.unbox.push(UnboxStored{id:"00000000-0000-4000-8000-000000000001".into(),token:"secret-session".into(),expires:(now()+60)*1000,account:json!({"accountId":id,"name":"EmailPlayer","uuid":"00000000000040008000000000000002","type":"unbox"})});v.selected=Some(id.into());
  let restored:Vault=serde_json::from_slice(&serde_json::to_vec(&v).unwrap()).unwrap();let public=restored.snapshot(None);assert_eq!(public["account"]["name"],"EmailPlayer");assert_eq!(public["account"]["needsSignIn"],false);assert!(!public.to_string().contains("secret-session"));
  v.unbox[0].expires=0;assert_eq!(v.snapshot(None)["account"]["needsSignIn"],true);v.remove(id);assert!(v.selected_account(None).is_null());assert!(v.unbox.is_empty());
 }
}

#[cfg(test)]mod player_proof_tests{
 use super::*;
 #[test]fn proof_signs_bound_challenge_and_contains_no_private_credentials(){
  use sha2::{Digest,Sha256};
  use ring::signature::{RsaKeyPair,UnparsedPublicKey,RSA_PKCS1_2048_8192_SHA256};
  let cert:Value=serde_json::from_str(include_str!("../test-data/player-proof-key.json")).unwrap();
  let proof=make_social_proof(&cert,&"b".repeat(32),"test-id","test-social-token",&"c".repeat(32)).unwrap();
  let private=STANDARD.decode(cert["keyPair"]["privateKey"].as_str().unwrap().lines().filter(|l|!l.starts_with("-----")).collect::<String>()).unwrap();
  let key=RsaKeyPair::from_pkcs8(&private).unwrap();
  let message=format!("Unbox friends certificate proof v1\ntest-id\n{:x}\n{}\n{}",Sha256::digest(b"test-social-token"),"b".repeat(32),"c".repeat(32));
  let signature=STANDARD.decode(proof["signature"].as_str().unwrap()).unwrap();
  let verifier=UnparsedPublicKey::new(&RSA_PKCS1_2048_8192_SHA256,key.public().as_ref());
  assert!(verifier.verify(message.as_bytes(),&signature).is_ok());
  assert!(verifier.verify(b"other challenge",&signature).is_err());
  assert!(!proof.to_string().contains("PRIVATE"));assert!(!proof.to_string().contains("test-social-token"));assert!(proof.get("keyPair").is_none());
 }
 #[test]fn invalid_private_certificate_fails_without_exposing_it(){
  let bad=json!({"keyPair":{"privateKey":"private-invalid-value","publicKey":"public"}});
  let error=make_social_proof(&bad,"nonce","id","token","uuid").unwrap_err();assert!(!error.contains("private-invalid-value"));
 }
}
