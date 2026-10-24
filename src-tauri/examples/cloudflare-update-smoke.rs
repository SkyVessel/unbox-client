//! Real updater transport/signature/install test against a disposable app copy.
//! Run with: cargo run --example cloudflare-update-smoke -- <fixture app executable>
use std::{path::PathBuf, time::Duration};
use tauri_plugin_updater::UpdaterExt;

fn main() {
    let executable = PathBuf::from(std::env::args().nth(1).expect("Disposable app executable required"));
    let executable = executable.canonicalize().unwrap();
    let normalized = executable.to_string_lossy().replace('\\', "/");
    assert!(normalized.contains("/.cache/cloudflare/update-fixture/") || normalized.contains("/unbox-update-fixture/"));
    let endpoint = std::env::args().nth(2);
    let endpoint = endpoint.filter(|s| s != "-");
    let current = std::env::args().nth(3).as_deref() == Some("--current");
    if let Some(ref url) = endpoint { assert!(url.starts_with("https://printoria.printoria-studio.workers.dev/unbox/releases/")); }
    let mut context = tauri::generate_context!();
    context.config_mut().app.windows.clear();
    // Model an installed earlier version without changing production metadata.
    if !current { context.package_info_mut().version = "0.2.20".parse().unwrap(); }
    tauri::Builder::default()
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(move |app| {
            let app = app.handle().clone();
            tauri::async_runtime::spawn(async move {
                let result: Result<(), Box<dyn std::error::Error + Send + Sync>> = async {
                    let builder = || {
                        let mut builder = app.updater_builder().no_proxy().timeout(Duration::from_secs(300)).executable_path(&executable).installer_arg("/S").restart_after_install(false);
                        if let Some(ref url) = endpoint { builder = builder.endpoints(vec![url.parse().unwrap()]).unwrap(); }
                        builder
                    };
                    let update = builder().build()?.check().await?;
                    if current { assert!(update.is_none()); println!("Current version correctly reports no update"); return Ok(()); }
                    let update = update.expect("Expected a newer release");
                    assert_eq!(update.version, "0.2.21");
                    assert!(update.download_url.as_str().starts_with("https://printoria.printoria-studio.workers.dev/unbox/releases/v0.2.21/"));
                    println!("Cloudflare manifest: {}", update.version);
                    let bytes = update.download(|_, _| {}, || {}).await?;
                    println!("Cloudflare download and signature passed: {} bytes", bytes.len());
                    let mut invalid = builder().build()?.check().await?.unwrap();
                    invalid.signature = "invalid-signature".into();
                    assert!(invalid.download(|_, _| {}, || {}).await.is_err());
                    println!("Invalid signature rejected");
                    update.install(bytes)?;
                    println!("Real Tauri update installed into disposable app");
                    Ok(())
                }.await;
                if let Err(error) = &result { eprintln!("Update smoke test failed: {error}"); }
                app.exit(if result.is_ok() { 0 } else { 1 });
            });
            Ok(())
        })
        .run(context).expect("Updater test runtime failed");
}
