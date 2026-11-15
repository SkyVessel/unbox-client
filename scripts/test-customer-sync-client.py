#!/usr/bin/env python3
"""Isolated real client for test-customer-sync.mjs; does not touch user profiles."""
import json, os, pathlib, shutil, subprocess, sys, zipfile, hashlib, urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1]
v=os.environ['UNBOX_MINECRAFT_VERSION'];role=sys.argv[1].removeprefix('private-');source=pathlib.Path(sys.argv[2]);game=pathlib.Path(os.environ['UNBOX_BENCH_GAME_DIR']);resume=os.environ.get('UNBOX_BENCH_RESUME')=='1'
cache=ROOT/'.cache/customer-sync';runtime=ROOT/f'.cache/runtime-{v}'
if not resume:
 if game.exists():raise RuntimeError('Fresh test directory required')
 (game/'mods').mkdir(parents=True);(game/'config').mkdir();(game/'logs').mkdir()
 # Fresh worlds are created by the test driver; no user save is opened.
 (game/'options.txt').write_text('onboardAccessibility:false\nskipMultiplayerWarning:true\nguiScale:2\nrenderDistance:4\nsimulationDistance:4\npauseOnLostFocus:false\nsoundCategory_master:0.0\nmaxFps:30\n')
 (game/'config/unbox.properties').write_text('cape=false\n')
 (game/'config/wover').mkdir(exist_ok=True)
 (game/'config/wover/client.json').write_text(json.dumps({'internal':{'did_present_welcome_screen':True},'loading':{'disable_experimental_warning':True}}))
 if role=='host':
  for jar in pathlib.Path(os.environ['UNBOX_CUSTOMER_MODS']).glob('*.jar'):os.link(jar,game/'mods'/jar.name)
lock=json.loads((ROOT/('src-tauri/resources/performance-lock-26.3.json' if v=='26.3' else f'client-mod/runtime-lock-{v}-fabric.json')).read_text())
if isinstance(lock,dict):lock=lock['mods']
if v=='26.3':
 wick=next(m for m in json.loads((ROOT/'src-tauri/resources/wick-lock.json').read_text()) if m['loader']=='fabric' and v in m['gameVersions']);lock.append({**wick,'slug':'wick'})
 lock.extend(next(t for t in json.loads((ROOT/'src-tauri/resources/survival-mods-lock.json').read_text())['targets'] if t['loader']=='fabric' and t['minecraft']==v)['mods'])
op=urllib.request.build_opener(urllib.request.ProxyHandler({}))
for item in lock:
 if item['slug']=='dynamic-fps':continue
 filename=item['filename'];dest=game/'mods'/filename
 if dest.exists():continue
 candidates=[ROOT/'src-tauri/resources'/item.get('resource','__none__'),ROOT/'.cache/survival-mods'/filename,pathlib.Path('/Users/ty/Documents/ChatGPT/启动器/.cache/survival-mods')/filename,ROOT/f'.cache/{v}/runtime-mods'/filename,ROOT/'.cache/benchmark-mods'/filename,pathlib.Path('/Users/ty/Documents/ChatGPT/启动器/.cache/benchmark-mods')/filename,cache/'dependencies'/filename]
 jar=next((p for p in candidates if p.is_file() and hashlib.sha1(p.read_bytes()).hexdigest()==item['sha1']),None)
 if jar is None:
  jar=candidates[-1];jar.parent.mkdir(parents=True,exist_ok=True);data=op.open(urllib.request.Request(item['url'],headers={'User-Agent':'Mozilla/5.0 Unbox-test'}),timeout=60).read();assert hashlib.sha1(data).hexdigest()==item['sha1'];jar.write_bytes(data)
 os.link(jar,dest)
shutil.copy2(cache/f'unbox-test-{v}.jar',game/'mods/unbox-client.jar')
if not resume:
 inventory=[{'filename':p.name,'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted((game/'mods').glob('*.jar'))]
 customer_hashes={hashlib.sha256(p.read_bytes()).hexdigest() for p in pathlib.Path(os.environ['UNBOX_CUSTOMER_MODS']).glob('*.jar')}
 if role=='guest' and any(p['sha256'] in customer_hashes for p in inventory):raise RuntimeError('Guest already contains a customer mod')
 (game/'logs/initial-mods.json').write_text(json.dumps(inventory,indent=2))
meta=json.loads((runtime/f'{v}.json').read_text());fabric=json.loads((runtime/'fabric-0.19.5.json').read_text());cp=[]
for lib in meta['libraries']:
 allow=True
 if 'rules' in lib:
  allow=False
  for r in lib['rules']:
   if r.get('os',{}).get('name','osx')=='osx' and 'features' not in r:allow=r['action']=='allow'
 if allow and 'artifact' in lib.get('downloads',{}):cp.append(str(runtime/'libraries'/lib['downloads']['artifact']['path']))
for lib in fabric['libraries']:
 g,a,release=lib['name'].split(':');cp.append(str(runtime/'libraries'/g.replace('.','/')/a/release/f'{a}-{release}.jar'))
cp.append(str(ROOT/f'.cache/client-{v}.jar'))
java=pathlib.Path(subprocess.check_output(['/usr/libexec/java_home','-v','25'],text=True).strip())/'bin/java' if v=='26.3' else pathlib.Path('/Users/ty/.gradle/jdks/eclipse_adoptium-21-aarch64-os_x.2/jdk-21.0.12+8/Contents/Home/bin/java')
identity=json.loads(os.environ['UNBOX_TEST_PROFILE'])
args=[str(java),'-XstartOnFirstThread','-Xmx4G','-Djava.awt.headless=true','-Dapple.awt.UIElement=true','-Dunbox.privateSmoke='+role,'-Dunbox.accountKind=local','-Dunbox.sharedCache='+os.environ['UNBOX_SHARED_CACHE'],'-Dunbox.testUpgradeBackup=true','-Dunbox.startupDiagnostics=true']
agent=ROOT/'.cache/customer-sync/silent-agent/agent.jar'
args.append('-javaagent:'+str(agent))
if resume:args.append('-Dunbox.syncResume=true')
if v=='26.3':
 for a in meta['arguments']['jvm']:
  if isinstance(a,str) and a not in ('-cp','${classpath}'):
   for k,val in {'natives_directory':str(game/'natives'),'launcher_name':'Unbox-test','launcher_version':'local'}.items():a=a.replace('${'+k+'}',val)
   args.append(a)
args+=['-cp',os.pathsep.join(cp),fabric['mainClass'],'--username',identity['name'],'--uuid',identity['uuid'],'--accessToken','0','--userType','legacy','--version',v,'--gameDir',str(game),'--assetsDir',str(runtime/'assets'),'--assetIndex',meta['assetIndex']['id'],'--width','960','--height','600']

(game/'test-launch-args.json').write_text(json.dumps(args));(game/'logs').mkdir(exist_ok=True)
print('Starting',v,role,flush=True)
with (game/'logs/launch.log').open('w') as log:
 p=subprocess.Popen(args,cwd=game,stdout=log,stderr=log)
 print('Fixture Java PID',p.pid,flush=True)
 try:code=p.wait(timeout=1000)
 except subprocess.TimeoutExpired:p.terminate();code=p.wait(timeout=20)
sys.exit(code)
