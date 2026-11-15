#!/usr/bin/env python3
"""Disposable native launcher overlay. Production sources/resources are never instrumented.
Build outside FPS sampling; run drives the actual Launch button in an isolated profile.
"""
import argparse,json,os,shutil,subprocess,time,zipfile,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('action',choices=['build','prepare','run']);p.add_argument('name');p.add_argument('--baseline',action='store_true');p.add_argument('--loader',default='fabric',choices=['fabric','neoforge']);p.add_argument('--jar');a=p.parse_args()
BASE=ROOT/'.cache/launcher-performance';BASE.mkdir(exist_ok=True)
ID='00000000-0000-4000-8000-000000000093'
source=BASE/a.name
if a.action=='build':
 source.mkdir(exist_ok=True)
 shutil.copytree(ROOT/'src-tauri/src',source/'src',dirs_exist_ok=True)
 if a.baseline:
  for n in ['main.rs','launcher.rs']:shutil.copy2(ROOT/'.cache/ui-performance/round2-baseline'/n,source/'src'/n)
 for n in ['Cargo.toml','Cargo.lock','build.rs']:shutil.copy2(ROOT/'src-tauri'/n,source/n)
 if a.baseline:shutil.copy2(ROOT/'.cache/ui-performance/round2-baseline/Cargo.toml',source/'Cargo.toml')
 (source/'Cargo.toml').write_text((source/'Cargo.toml').read_text()+'\n[features]\ncustom-protocol = ["tauri/custom-protocol"]\n')
 for n in ['icons','capabilities','resources']:
  if not (source/n).exists():(source/n).symlink_to(ROOT/'src-tauri'/n,target_is_directory=True)
 conf=json.loads((ROOT/'src-tauri/tauri.conf.json').read_text());conf['build']['frontendDist']=str(ROOT/'dist');conf['build'].pop('devUrl',None);conf['build'].pop('beforeDevCommand',None);conf['build'].pop('beforeBuildCommand',None)
 (source/'tauri.conf.json').write_text(json.dumps(conf))
 main=(source/'src/main.rs').read_text()
 main=main.replace('let path=rt.root.join("state.json");','if std::env::var_os("UNBOX_BENCH_ROOT").is_some(){return Ok(json!({"state":serde_json::from_slice::<Value>(&fs::read(rt.root.join("state.json")).unwrap()).unwrap(),"dataDirectory":rt.root,"native":true,"version":"26.3"}))}\n    let path=rt.root.join("state.json");',1)
 main=main.replace('memory: u32, prepare_only: bool) -> Result<(),String> {','memory: u32, prepare_only: bool, requested_at: Option<u64>) -> Result<(),String> {',1)
 main=main.replace('    launch_profile(rt.inner().clone(),app,id,username,memory,prepare_only)','    atomic(&rt.root.join("click.json"),&serde_json::to_vec(&json!({"clickedAt":requested_at,"nativeReceivedAt":std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis()})).unwrap())?;\n    launch_profile(rt.inner().clone(),app,id,username,memory,prepare_only)',1)
 main=main.replace('prepare_only,false,&app).await?','prepare_only,true,&app).await?')
 main=main.replace('        let mut job=runtime.job.lock().unwrap();','        let code=if result.is_ok(){0}else{eprintln!("BENCH_ERROR {:?}",result);1};app.exit(code);\n        let mut job=runtime.job.lock().unwrap();',1)
 main=main.replace('let root=app.path().app_data_dir()?;','let root=PathBuf::from(std::env::var("UNBOX_BENCH_ROOT").expect("isolated benchmark root"));assert!(root.to_string_lossy().contains("/.cache/launcher-performance/"));')
 main=main.replace('resources:app.path().resource_dir()?','resources:root.join("bundled")').replace('Runtime {root,resources:root.join("bundled")','Runtime {root:root.clone(),resources:root.join("bundled")')
 main=main.replace('fn main() {','''#[tauri::command]
fn bench_click(rt:tauri::State<Runtime>,at:u64,message:String){eprintln!("BENCH_WEB {message}");if at>0{let _=atomic(&rt.root.join("button.json"),&serde_json::to_vec(&json!({"clickedAt":at})).unwrap());}}
fn main() {''')
 main=main.replace('tauri::generate_handler![','tauri::generate_handler![bench_click,',1)
 main=main.replace('"clickedAt":requested_at','"clickedAt":requested_at.or_else(||fs::read(rt.root.join("button.json")).ok().and_then(|b|serde_json::from_slice::<Value>(&b).ok()).and_then(|v|v["clickedAt"].as_u64()))')
 inject='''let window=app.get_webview_window("main").unwrap();tauri::async_runtime::spawn(async move{tokio::time::sleep(std::time::Duration::from_secs(3)).await;let _=window.eval(r#"(()=>{let tries=0;const timer=setInterval(()=>{const button=document.querySelector('button.launch');if(button&&!button.disabled&&!button.textContent.includes('CREATE')){clearInterval(timer);window.__TAURI_INTERNALS__.invoke('bench_click',{at:Date.now(),message:'Launch button click'}).then(()=>button.click());}else if(++tries>50){clearInterval(timer);window.__TAURI_INTERNALS__.invoke('bench_click',{at:0,message:document.body.innerText.slice(0,1500)});}},100);})()"#);});app.manage(rt);Ok(())''' 
 main=main.replace('social::start(rt.clone());skin_library::start(rt.clone());app.manage(rt);Ok(())',inject)
 (source/'src/main.rs').write_text(main)
 auth=(source/'src/auth.rs').read_text()
 auth=auth.replace('fn load_accounts(rt:&Runtime)->Result<Vault,String>{','''fn load_accounts(rt:&Runtime)->Result<Vault,String>{
 if std::env::var_os("UNBOX_BENCH_ROOT").is_some(){return Ok(Vault{selected:Some("local:benchmark".into()),offline:vec![json!({"accountId":"local:benchmark","name":"UnboxUITest","uuid":"00000000000000000000000000000001","type":"local","provider":"Local"})],..Vault::default()})}
''')
 auth=auth.replace('fn load_vault()->Result<Vault,String>{','fn load_vault()->Result<Vault,String>{if std::env::var_os("UNBOX_BENCH_ROOT").is_some(){return Ok(Vault::default())}')
 (source/'src/auth.rs').write_text(auth)
 launch=(source/'src/launcher.rs').read_text()
 launch=launch.replace('    let version=profile["version"]','    let bench_start=std::time::Instant::now();\n    let version=profile["version"]',1)
 for marker,label in [('    let java=java(','account'),('    phase(rt,&format!("Downloading Minecraft','java_metadata'),('    let assets=shared.join','libraries'),('    let mut main=meta[','assets'),('    if let Some(loader @','loader_mods'),('    if profile["loader"]!="neoforge"','survival'),('    let log=fs::File::create','ready')]:
  launch=launch.replace(marker,f'    eprintln!("BENCH_STAGE {label} {{}}",bench_start.elapsed().as_secs_f64());\n'+marker,1)
 marker='    let log=fs::File::create'
 extra='''    let clicked:Value=serde_json::from_slice(&fs::read(rt.root.join("click.json")).unwrap()).unwrap();
    let at=clicked["clickedAt"].as_u64().unwrap();
    argv.insert(0,format!("-Dunbox.benchmarkClickedAt={at}"));argv.insert(0,"-Dunbox.fixedViewBench=true".into());argv.insert(0,"-Dunbox.startupOnly=true".into());
    argv.extend(["--width".into(),"1280".into(),"--height".into(),"720".into()]);
    atomic(&rt.root.join("prepared.json"),&serde_json::to_vec(&json!({"preparationMs":bench_start.elapsed().as_secs_f64()*1000.0,"clickedAt":at})).unwrap())?;
'''
 launch=launch.replace(marker,extra+marker,1);(source/'src/launcher.rs').write_text(launch)
 env=os.environ.copy();env['CARGO_TARGET_DIR']=str(ROOT/'src-tauri/target')
 subprocess.run(['cargo','build','--manifest-path',str(source/'Cargo.toml'),'--features','custom-protocol'],env=env,check=True)
 (source/'unbox-benchmark').unlink(missing_ok=True)
 shutil.copy2(ROOT/'src-tauri/target/debug/unbox-client',source/'unbox-benchmark')
 print(source/'unbox-benchmark')
elif a.action=='prepare':
 run=source;run.mkdir(exist_ok=True)
 runtime=run/'runtime';runtime.mkdir(exist_ok=True)
 reference=ROOT/'.cache/runtime-26.3'
 for n in ['26.3.json','libraries','assets']:
  target=runtime/n
  if not target.exists():target.symlink_to(reference/n,target_is_directory=(reference/n).is_dir())
 for n,ref in [('26.3.jar',ROOT/'.cache/client-26.3.jar'),('fabric-26.3-0.19.5.json',reference/'fabric-0.19.5.json')]:
  if not (runtime/n).exists():(runtime/n).symlink_to(ref)
 (runtime/'neoforge').mkdir(exist_ok=True)
 if not (runtime/'neoforge/26.3.0.86').exists():(runtime/'neoforge/26.3.0.86').symlink_to(ROOT/'.cache/neoforge-26.3/runtime',target_is_directory=True)
 bundled=run/'bundled';bundled.mkdir(exist_ok=True)
 if not (bundled/'resources').exists():shutil.copytree(ROOT/'src-tauri/resources',bundled/'resources',copy_function=os.link)
 profile={'id':ID,'name':'Isolated launch timing','version':'26.3','loader':a.loader,'icon':'grass_block'}
 game=run/'profiles'/ID;game.mkdir(parents=True,exist_ok=True);(game/'profile.json').write_text(json.dumps(profile))
 state={'profiles':[profile],'selected':ID,'settings':{'memory':4096,'minimize':True},'account':{'name':'UnboxUITest','type':'local','uuid':'00000000000000000000000000000001'}}
 (run/'state.json').write_text(json.dumps(state));(game/'options.txt').write_text('guiScale:2\nmaxFps:260\nenableVsync:false\nsoundCategory_master:0.0\nonboardAccessibility:false\n')
 # Inject readiness detection into the isolated resource, not the installed client.
 args=json.loads((ROOT/'.cache/solid-ui'/('hud-entry-'+a.loader)/'launch-args.json').read_text());jar=Path(a.jar).resolve();classes=run/'harness';classes.mkdir(exist_ok=True)
 cp=str(ROOT/'.cache/client-26.3.jar')+os.pathsep+args[args.index('-cp')+1]+os.pathsep+str(jar)
 subprocess.run(['javac','--release','25','-proc:none','-cp',cp,'-d',str(classes),str(ROOT/'tests/performance-26.3/FrameBenchmark.java')],check=True)
 dest=bundled/'resources'/('unbox-client'+('-neoforge' if a.loader=='neoforge' else '')+'-26.3.jar');dest.unlink()
 with zipfile.ZipFile(jar) as old,zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as new:
  for info in old.infolist():
   if info.filename!='dev/unbox/client/FrameBenchmark.class':new.writestr(info,old.read(info))
  new.write(classes/'dev/unbox/client/FrameBenchmark.class','dev/unbox/client/FrameBenchmark.class')
 (run/'source.json').write_text(json.dumps({'jar':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()}))
 print(run)
else:
 run=BASE/a.jar;assert run.is_dir(),'--jar identifies prepared fixture name for run'
 env=os.environ.copy();env['UNBOX_BENCH_ROOT']=str(run)
 stamp=str(time.time_ns());log=run/('launcher-'+stamp+'.log');result=run/'profiles'/ID/'bench/results.json';result.unlink(missing_ok=True)
 with log.open('w') as f:
  proc=subprocess.Popen([str(source/'unbox-benchmark')],env=env,stdout=f,stderr=subprocess.STDOUT)
  try:code=proc.wait(timeout=420)
  except subprocess.TimeoutExpired:proc.terminate();raise
 if code or not result.exists():raise SystemExit(f'Failed: {log}')
 report=json.loads(result.read_text());report.update(json.loads((run/'prepared.json').read_text()));report.update(json.loads((run/'click.json').read_text()));report['binary']=a.name;report['fixture']=run.name;report['source']=json.loads((run/'source.json').read_text());report['binarySha256']=hashlib.sha256((source/'unbox-benchmark').read_bytes()).hexdigest()
 path=run/('result-'+stamp+'.json');path.write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
