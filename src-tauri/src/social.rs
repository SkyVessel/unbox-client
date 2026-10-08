use crate::{auth, profile_dir, Runtime};
use base64::{engine::general_purpose::STANDARD, Engine};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{
    fs,
    time::{Duration, SystemTime, UNIX_EPOCH},
};
#[derive(Clone, Serialize, Deserialize)]
struct Identity {
    id: String,
    token: String,
}
#[derive(Default)]
pub struct State {
    pub view: Value,
    last_command: String,
    last_room: String,
    account_key: String,
    io: std::sync::Arc<tokio::sync::Mutex<()>>,
}
fn atomic(path:&std::path::Path,data:&[u8])->Result<(),String>{
    use std::io::Write;
    let tmp=path.with_file_name(format!(".unbox-{}.tmp",uuid::Uuid::new_v4()));
    let mut options=fs::OpenOptions::new();options.write(true).create_new(true);
    #[cfg(unix)]{use std::os::unix::fs::OpenOptionsExt;options.mode(0o600);}
    let result=(||->std::io::Result<()>{let mut f=options.open(&tmp)?;f.write_all(data)?;fs::rename(&tmp,path)})();
    if result.is_err(){let _=fs::remove_file(&tmp);}result.map_err(|_|"Could not write private game bridge".into())
}
fn now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs()
        * 1000
}
pub(crate) fn endpoint(rt: &Runtime) -> Result<String, String> {
    let p = rt.resources.join("resources/social-config.json");
    let v: Value = serde_json::from_slice(&fs::read(p).unwrap_or_default()).unwrap_or(Value::Null);
    let s = v["url"]
        .as_str()
        .filter(|s| s.starts_with("https://") && s.ends_with(".workers.dev"))
        .ok_or("Friends service is not deployed. Cloudflare authorization is required.")?;
    Ok(s.into())
}
fn identity(key: &str) -> Result<Identity, String> {
    #[cfg(target_os = "macos")]
    {
        let name = format!("friends-{key}");
        match security_framework::passwords::get_generic_password("dev.unbox.client.social", &name)
        {
            Ok(b) => {
                return serde_json::from_slice(&b)
                    .map_err(|_| "Saved friend identity is invalid".into())
            }
            Err(e) if e.code() == -25300 => {}
            Err(_) => return Err("Allow Unbox access to macOS Keychain".into()),
        }
        let i = Identity {
            id: uuid::Uuid::new_v4().to_string(),
            token: format!(
                "{}{}",
                uuid::Uuid::new_v4().simple(),
                uuid::Uuid::new_v4().simple()
            ),
        };
        security_framework::passwords::set_generic_password(
            "dev.unbox.client.social",
            &name,
            &serde_json::to_vec(&i).unwrap(),
        )
        .map_err(|_| "Could not save friend identity in Keychain")?;
        Ok(i)
    }
    #[cfg(not(target_os = "macos"))]
    Err("Secure friend identity storage is currently macOS only".into())
}
async fn account(rt: &Runtime) -> Result<Value, String> {
    let _a = rt.auth.lock().await;
    let a = auth::restore_account(rt, &Value::Null)?;
    if a.is_null() {
        return Err("Choose an account to use friends".into());
    }
    Ok(a)
}
async fn request(url: &str, i: &Identity, path: &str, body: Value) -> Result<Value, String> {
    let r = reqwest::Client::builder()
        .no_proxy()
        .timeout(Duration::from_secs(15))
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .map_err(|_| "Connection unavailable")?
        .post(format!("{url}/v1/{path}"))
        .bearer_auth(&i.token)
        .json(&body)
        .send()
        .await
        .map_err(|_| "Friends service is unreachable. Retrying automatically.")?;
    let status = r.status();
    let v: Value = r
        .json()
        .await
        .map_err(|_| "Friends service returned an invalid response")?;
    if !status.is_success() {
        return Err(v["error"]
            .as_str()
            .unwrap_or("Friends service unavailable")
            .chars()
            .take(160)
            .collect());
    }
    Ok(v)
}
fn face(a: &Value) -> Option<String> {
    let s = a["skin"].as_str()?.strip_prefix("data:image/png;base64,")?;
    let bytes = STANDARD.decode(s).ok()?;
    let mut d = png::Decoder::new(std::io::Cursor::new(bytes));
    d.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    let mut r = d.read_info().ok()?;
    if r.info().width != 64 || ![32, 64].contains(&r.info().height) {
        return None;
    }
    let mut pixels = vec![0; r.output_buffer_size()];
    let info = r.next_frame(&mut pixels).ok()?;
    let channels = match info.color_type {
        png::ColorType::Rgba => 4,
        png::ColorType::Rgb => 3,
        _ => return None,
    };
    let mut f = vec![0u8; 8 * 8 * 4];
    for y in 0..8 {
        for x in 0..8 {
            let p = ((y + 8) * 64 + x + 8) * channels;
            let hat = ((y + 8) * 64 + x + 40) * channels;
            let a = if channels == 4 {
                pixels[hat + 3] as u16
            } else {
                255
            };
            for c in 0..3 {
                f[(y * 8 + x) * 4 + c] =
                    ((pixels[hat + c] as u16 * a + pixels[p + c] as u16 * (255 - a)) / 255) as u8
            }
            f[(y * 8 + x) * 4 + 3] = 255;
        }
    }
    let mut b = Vec::new();
    {
        let mut e = png::Encoder::new(&mut b, 8, 8);
        e.set_color(png::ColorType::Rgba);
        e.set_depth(png::BitDepth::Eight);
        e.write_header().ok()?.write_image_data(&f).ok()?
    }
    Some(format!("data:image/png;base64,{}", STANDARD.encode(b)))
}
fn profile(a: &Value) -> Value {
    json!({"name":a["name"],"uuid":a["uuid"].as_str().unwrap_or(""),"kind":a["type"],"face":face(a)})
}
fn account_identity(a:&Value)->Result<Identity,String>{if a["type"]=="unbox"{let(id,token)=auth::unbox_identity(a)?;Ok(Identity{id,token})}else{identity(&key(a))}}
fn key(a: &Value) -> String {
    if a["type"]=="unbox"{return a["accountId"].as_str().unwrap_or("").to_owned()}
    if a["type"] == "microsoft" {
        format!("msa-{}", a["uuid"].as_str().unwrap_or(""))
    } else {
        format!("local-{}", a["name"].as_str().unwrap_or(""))
    }
}
fn game(rt: &Runtime) -> Option<std::path::PathBuf> {
    let j = rt.job.lock().unwrap();
    if j["stage"] != "Playing" {
        return None;
    }
    profile_dir(rt, j["profileId"].as_str()?).ok()
}
fn game_status(rt: &Runtime, a: &Value) -> Value {
    game(rt)
        .and_then(|g| fs::read(g.join("unbox-world.json")).ok())
        .and_then(|b| serde_json::from_slice::<Value>(&b).ok())
        .filter(|s| {
            s["updated"]
                .as_u64()
                .is_some_and(|n| now().saturating_sub(n) < 10000)
                && s["accountName"] == a["name"]
                && (a["type"] == "local" || s["accountUuid"] == a["uuid"])
        })
        .unwrap_or(Value::Null)
}
async fn sync(rt: &Runtime) -> Result<Value, String> {
    let io = rt.social.lock().await.io.clone();
    let _guard = io.lock().await;
    sync_inner(rt).await
}
async fn sync_inner(rt: &Runtime) -> Result<Value, String> {
    let url = endpoint(rt)?;
    let a = account(rt).await?;
    let i = account_identity(&a)?;
    let mut b = profile(&a);
    let world = game_status(rt, &a);
    b["state"] = json!(if !world["room"].is_null() {
        "Hosting"
    } else if game(rt).is_some() {
        "Playing"
    } else {
        "Launcher"
    });
    let result = request(&url, &i, "sync", b.clone()).await;
    let mut result = match result {
        Ok(v) => v,
        Err(e) if e == "Sign in to Unbox friends again" && a["type"]!="unbox" => {
            if a["type"] == "microsoft" {
                let c = request(&url, &i, "challenge", json!({"id":i.id})).await?;
                auth::prove_social(
                    rt,
                    c["challenge"]
                        .as_str()
                        .ok_or("Invalid verification challenge")?,
                )
                .await?;
            }
            b["id"] = json!(i.id);
            request(&url, &i, "register", b.clone()).await?;
            request(&url, &i, "sync", b).await?
        }
        Err(e) => return Err(e),
    };
    if a["type"]=="unbox"{auth::sync_unbox_name(rt,a["accountId"].as_str().unwrap_or(""),result["self"]["name"].as_str().unwrap_or("")).await?;}
    result["connected"] = json!(true);
    result["updated"] = json!(now());
    result["hosting"] = json!(!world["room"].is_null());
    result["inWorld"] = json!(world["inWorld"] == true);
    result["canInvite"] = json!(world["canInvite"] == true);
    result["gameError"] = world["error"].clone();
    if key(&account(rt).await?) != key(&a) {
        return Err("Account changed. Reconnecting friends…".into());
    }
    let room = world["room"].to_string();
    let changed = {
        let state = rt.social.lock().await;
        state.last_room != room || state.account_key != key(&a)
    };
    if changed {
        request(&url, &i, "room", json!({"room":world["room"]})).await?;
    }
    if key(&account(rt).await?) != key(&a) {
        return Err("Account changed. Reconnecting friends…".into());
    }
    let mut state = rt.social.lock().await;
    state.last_room = room;
    state.account_key = key(&a);
    state.view = result.clone();
    drop(state);
    if let Some(g) = game(rt) {
        let game_view = if world.is_null() {
            json!({"connected":false,"friends":[],"invites":[],"updated":now()})
        } else {
            result.clone()
        };
        atomic(
            &g.join("unbox-social-view.json"),
            &serde_json::to_vec(&game_view).unwrap(),
        )?
    }
    Ok(result)
}
async fn action(rt: &Runtime, op: &str, id: &str) -> Result<Value, String> {
    let io = rt.social.lock().await.io.clone();
    let _guard = io.lock().await;
    let url = endpoint(rt)?;
    let a = account(rt).await?;
    let i = account_identity(&a)?;
    let (path, b) = match op {
        "request" => (
            "friends/request",
            json!({"code":id.trim().replace(' ',"").to_uppercase()}),
        ),
        "accept" => ("friends/accept", json!({"id":id})),
        "remove" => ("friends/remove", json!({"id":id})),
        "relay" => ("invites/relay", json!({"id":id})),
        "dismiss" => ("invites/dismiss", json!({"id":id})),
        "send" => {
            let world = game_status(rt, &a);
            if world["room"].is_null() {
                return Err("Open a singleplayer world before inviting friends".into());
            }
            ("invites/send", json!({"id":id,"room":world["room"],"joinSecret":world["grants"][id]}))
        }
        _ => return Err("Unknown friend action".into()),
    };
    request(&url, &i, path, b).await?;
    sync_inner(rt).await
}
fn public_view(mut v:Value)->Value{if let Some(invites)=v["invites"].as_array_mut(){for i in invites{if let Some(o)=i.as_object_mut(){o.remove("join_secret");}}}v}
#[tauri::command]
pub async fn social_status(rt: tauri::State<'_, Runtime>) -> Result<Value, String> {
    let a = account(&rt).await.ok();
    let s = rt.social.lock().await;
    let mut view = if a.as_ref().is_some_and(|a| key(a) == s.account_key) {
        s.view.clone()
    } else {
        Value::Null
    };
    drop(s);
    if view.is_null() {
        view = json!({"connected":false,"friends":[],"invites":[],"error":endpoint(&rt).err().unwrap_or_else(||if a.is_none(){"Choose an account to use friends".into()}else{"Connecting…".into()})})
    }
    Ok(public_view(view))
}
#[tauri::command]
pub async fn social_action(
    rt: tauri::State<'_, Runtime>,
    op: String,
    id: String,
) -> Result<Value, String> {
    if op == "invite" || op == "join" {
        let g = game(&rt).ok_or("Launch a Fabric profile first")?;
        let a = account(&rt).await?;
        let world = game_status(&rt, &a);
        if world.is_null() {
            return Err("The running game is not connected to this launcher account".into());
        }
        let view = rt.social.lock().await.view.clone();
        let collection = if op == "invite" { "friends" } else { "invites" };
        if !view[collection]
            .as_array()
            .is_some_and(|v| v.iter().any(|p| p["id"] == id))
        {
            return Err("Friend or invitation not found".into());
        }
        atomic(
            &g.join("unbox-social-action.json"),
            &serde_json::to_vec(
                &json!({"requestId":uuid::Uuid::new_v4(),"op":op,"id":id,"at":now()}),
            )
            .unwrap(),
        )?;
        return Ok(public_view(view));
    }
    action(&rt, &op, &id).await.map(public_view)
}
pub fn start(rt: Runtime) {
    tauri::async_runtime::spawn(async move {
        let mut ticks = 20;
        loop {
            tokio::time::sleep(Duration::from_secs(1)).await;
            ticks += 1;
            if let Some(g) = game(&rt) {
                if let Ok(b) = fs::read(g.join("unbox-social-command.json")) {
                    if b.len() < 16384 {
                        if let Ok(c) = serde_json::from_slice::<Value>(&b) {
                            let id = c["requestId"].as_str().unwrap_or("");
                            let mut state = rt.social.lock().await;
                            let fresh = c["at"]
                                .as_u64()
                                .is_some_and(|n| now().saturating_sub(n) < 15000)
                                && !id.is_empty()
                                && id != state.last_command;
                            if fresh {
                                state.last_command = id.into();
                            }
                            drop(state);
                            if fresh {
                                let active = account(&rt)
                                    .await
                                    .ok()
                                    .is_some_and(|a| !game_status(&rt, &a).is_null());
                                let result = if !active {
                                    Err("Game account changed".into())
                                } else {
                                    action(
                                        &rt,
                                        c["op"].as_str().unwrap_or(""),
                                        c["id"].as_str().unwrap_or(""),
                                    )
                                    .await
                                };
                                let ack = json!({"requestId":id,"ok":result.is_ok(),"error":result.err(),"at":now()});
                                let _ = atomic(
                                    &g.join("unbox-social-ack.json"),
                                    &serde_json::to_vec(&ack).unwrap(),
                                );
                                ticks = 0;
                            }
                        }
                    }
                }
            }
            if ticks >= 20 {
                ticks = 0;
                if let Err(e) = sync(&rt).await {
                    let current = account(&rt).await.ok();
                    let mut s = rt.social.lock().await;
                    s.account_key = current.as_ref().map(key).unwrap_or_default();
                    s.view = json!({"connected":false,"friends":[],"invites":[],"error":e,"updated":now()});
                    if let Some(g) = game(&rt) {
                        let _ = atomic(
                            &g.join("unbox-social-view.json"),
                            &serde_json::to_vec(&s.view).unwrap(),
                        );
                    }
                }
            }
        }
    });
}
