#!/usr/bin/env python3
"""Run the independent client with official Forge; optionally add the pinned pack.
Uses a fresh directory and test identity, never user worlds or account tokens.
"""
from pathlib import Path
import argparse,importlib.util,json,os,shutil,subprocess,zipfile,concurrent.futures,hashlib,time
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('build',ROOT/'scripts/build-unpvp.py');b=importlib.util.module_from_spec(spec);spec.loader.exec_module(b)
p=argparse.ArgumentParser();p.add_argument('name');p.add_argument('--width',type=int,default=1600);p.add_argument('--gui-scale',type=int,default=2);p.add_argument('--render-distance',type=int,default=8);p.add_argument('--height',type=int,default=900);p.add_argument('--world',action='store_true');p.add_argument('--mods',type=Path);p.add_argument('--legacy-panorama',action='store_true');p.add_argument('--client-jar',type=Path,default=ROOT/'src-tauri/resources/unpvp-forge-1.8.9.jar');p.add_argument('--perf',action='store_true');p.add_argument('--retina',action='store_true');p.add_argument('--display-only',action='store_true');p.add_argument('--presentation',action='store_true');p.add_argument('--load',action='store_true');p.add_argument('--solid',action='store_true');p.add_argument('--pvp',action='store_true');p.add_argument('--response',action='store_true');p.add_argument('--lobby',action='store_true');p.add_argument('--occlusion',action='store_true');p.add_argument('--intel',action='store_true');p.add_argument('--no-culling',action='store_true');p.add_argument('--stock-startup',action='store_true');p.add_argument('--first-thread',action='store_true');p.add_argument('--manual',action='store_true');p.add_argument('--runtime',type=Path,default=ROOT/'.cache/unpvp-launcher-check/runtime');p.add_argument('--social',choices=['host','guest','ui']);p.add_argument('--reject',action='store_true');p.add_argument('--badges',action='store_true');p.add_argument('--combat',action='store_true');p.add_argument('--fullbright',action='store_true');p.add_argument('--g1',action='store_true');p.add_argument('--badge-cost',action='store_true');p.add_argument('--no-badge-hooks',action='store_true');a=p.parse_args()
if a.combat:a.pvp=True
if a.fullbright or a.badge_cost:a.lobby=True
if a.no_badge_hooks and not a.badge_cost:raise SystemExit("--no-badge-hooks requires isolated --badge-cost")
if Path(a.name).name!=a.name:raise SystemExit('Use a single directory name')
run=ROOT/('.cache/unpvp-manual' if a.manual else '.cache/unpvp-tests')/a.name
if run.exists() and not a.manual:raise SystemExit('Choose a new test name; existing test data is preserved')
run.mkdir(parents=True,exist_ok=a.manual);runtime=ROOT/'.cache/unpvp-runtime';runtime.mkdir(exist_ok=True)
arm=None if a.intel else json.loads((ROOT/'src-tauri/resources/unpvp-apple-silicon.json').read_text());lock=json.loads((ROOT/'src-tauri/resources/unpvp-runtime.json').read_text());meta=lock['minecraft'];cp=[]
def get(entry):
 target=runtime/'libraries'/entry['path']
 if target.is_file() and hashlib.sha1(target.read_bytes()).hexdigest()==entry['sha1']:return target
 # Content-addressed original upstream downloads can be reused, never old mod/build outputs.
 for base in [b.WORK/'libraries',a.runtime/'unpvp/libraries']:
  old=base/entry['path']
  if old.is_file() and hashlib.sha1(old.read_bytes()).hexdigest()==entry['sha1']:
   target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(old,target);break
 return b.fetch(entry['url'],target,entry['sha1'])
libs=[v['downloads']['artifact'] for v in meta['libraries'] if 'artifact' in v.get('downloads',{}) and (not v.get('rules') or any(r.get('os',{}).get('name')=='osx' and r['action']=='allow' for r in v['rules']))]
if arm:libs=[v for v in libs if not v['path'].startswith('org/lwjgl/')]+arm['libraries']
libs+=lock['forge']['libraries']
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:cp=list(map(str,pool.map(get,libs)))
natives=runtime/(arm['nativeDirectory'] if arm else 'natives');natives.mkdir(exist_ok=True)
for lib in ([] if arm else meta['libraries']):
 name=lib.get('natives',{}).get('osx')
 if not name:continue
 entry=lib['downloads']['classifiers'][name];jar=get(entry)
 with zipfile.ZipFile(jar) as z:
  for n in z.namelist():
   if n.endswith(('.dylib','.jnilib')):(natives/Path(n).name).write_bytes(z.read(n))
if arm:
 with zipfile.ZipFile(get(arm['natives'])) as z:
  for n in z.namelist():
   if n.endswith(('.dylib','.jnilib')):(natives/Path(n).name).write_bytes(z.read(n))
client=b.fetch(meta['downloads']['client']['url'],runtime/'minecraft.jar',meta['downloads']['client']['sha1']);cp.append(str(client))
assets=a.runtime/'assets'
if not assets.exists():raise SystemExit('Test assets missing; prepare through launcher first')
log=meta['logging']['client']['file'];logfile=b.fetch(log['url'],runtime/log['id'],log['sha1'])
java=next((p for p in (a.runtime/'unpvp'/(arm['java']['directory'] if arm else 'java8')).rglob('java') if p.parent.name=='bin' and p.is_file()),None)
if java is None:raise SystemExit('Prepare Java 8 through the launcher, then pass --runtime /path/to/runtime')
if a.legacy_panorama:shutil.copytree(ROOT/'.cache/unpvp-tests/final-artifact/unpvp/panorama',run/'unpvp/panorama',dirs_exist_ok=True)
(run/'mods').mkdir(exist_ok=a.manual);shutil.copy2(a.client_jar,run/'mods/unpvp.jar')
if a.mods:
 (run/'config').mkdir(exist_ok=True)
 if not (run/'config/renderlib.cfg').exists():(run/'config/renderlib.cfg').write_text('general {\n B:mainMenuFPSSynced=true\n}\n')
 if not (run/'optionsof.txt').exists():(run/'optionsof.txt').write_text('ofFastRender:false\nofFastMath:true\nofSmoothFps:false\nofSmoothWorld:false\nofRenderRegions:false\nofDynamicUpdates:false\nofChunkUpdates:1\nofLazyChunkLoading:true\nofAaLevel:0\nofAfLevel:1\n')
 for jar in a.mods.glob('*.jar'):shutil.copy2(jar,run/'mods'/jar.name)
if a.mods:
 for name in ['unpvp-pvp.jar','unpvp-patcher-performance.jar','unpvp-network.jar']:shutil.copy2(ROOT/'src-tauri/resources'/name,run/'mods'/name)
shutil.copy2(a.client_jar,run/'mods/unpvp.jar')
if a.no_badge_hooks:
 jar=run/'mods/unpvp-pvp.jar';temp=jar.with_suffix('.test-tmp')
 with zipfile.ZipFile(jar) as source,zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED) as target:
  for entry in source.infolist():
   data=source.read(entry.filename)
   if entry.filename=='mixins.unbox-pvp.json':
    config=json.loads(data);config['client']=[m for m in config['client'] if m not in ['NameBadgeMixin','BadgeFontMixin']];data=json.dumps(config).encode()
   target.writestr(entry,data)
 temp.replace(jar)
if not a.manual or not (run/'options.txt').exists():
 (run/'options.txt').write_text(f'guiScale:{a.gui_scale}\nmaxFps:120\nenableVsync:false\npauseOnLostFocus:false\nrenderDistance:{a.render_distance}\nmipmapLevels:4\nuseVbo:true\nsoundCategory_master:0.0\n')
if a.manual:
 options=run/'options.txt';lines=[l for l in options.read_text().splitlines() if not l.startswith(('maxFps:','enableVsync:'))];options.write_text('\n'.join(lines+['maxFps:260','enableVsync:false'])+'\n')
args=[str(java),'-Xms256M','-Xmx1536M','-Dunpvp.test=true','-Dunpvp.test.world='+str(a.world).lower(),'-Djava.library.path='+str(natives),'-Dlog4j.configurationFile='+str(logfile),'-cp',os.pathsep.join(cp),lock['forge']['mainClass'],'--tweakClass','net.minecraftforge.fml.common.launcher.FMLTweaker','--username','UIPreview','--uuid','00000000000000000000000000000001','--accessToken','0','--userType','legacy','--version','1.8.9','--gameDir',str(run),'--assetsDir',str(assets),'--assetIndex',meta['assetIndex']['id'],'--width',str(a.width),'--height',str(a.height)]
if a.social:
 args.remove('-Dunpvp.test=true');args.insert(1,'-Dunpvp.test.social='+a.social);args.insert(1,'-Dunbox.voxlinkFixture=true')
 args[args.index('--username')+1]='SocialHost' if a.social=='host' else 'SocialGuest'
 args[args.index('--uuid')+1]='00000000000000000000000000000001' if a.social=='host' else '00000000000000000000000000000002'
if a.reject:args.insert(1,"-Dunpvp.test.expectReject=true")
if a.badges:
 args.remove('-Dunpvp.test=true');args.insert(1,'-Dunpvp.test.badges=true')
if a.no_culling:args.insert(1,'-Dunpvp.culling.disabled=true')
if a.manual:args.remove('-Dunpvp.test=true')
if a.presentation:
 args.remove('-Dunpvp.test=true');args.insert(1,'-Dunpvp.test.presentation=true')
if a.display_only:args.insert(1,'-Dunpvp.test.displayOnly=true')
if a.retina:
 args.remove('-Dunpvp.test=true');args.insert(1,'-Dunpvp.test.retina=true')
if a.first_thread:args[1:1]=['-XstartOnFirstThread','-Djava.awt.headless=true']
if a.g1:args[1:1]=["-XX:+UseG1GC","-XX:MaxGCPauseMillis=50"]
if a.combat:args.insert(1,'-Dunpvp.test.combat=true');args.insert(1,'-Dmixin.debug.export=true')
if a.pvp:
 args.remove('-Dunpvp.test=true');args.insert(1,'-Dunpvp.test.pvp=true')
if a.occlusion:args.insert(1,'-Dunpvp.test.occlusion=true')
if a.badge_cost:args.insert(1,'-Dunpvp.test.badgeCost=true')
if a.fullbright:args.insert(1,'-Dunpvp.test.fullbright=true')
if a.lobby:
 args.remove('-Dunpvp.test=true');args.insert(1,'-Dunpvp.test.lobby=true')
if a.response:
 args.remove('-Dunpvp.test=true');args.insert(1,'-Dunpvp.test.response=true')
if a.solid:
 args.remove('-Dunpvp.test=true');args.insert(1,'-Dunpvp.test.solid=true')
if a.load:
 args.remove('-Dunpvp.test=true');args.insert(1,'-Dunpvp.test.load=true')
if a.perf:
 args.remove('-Dunpvp.test=true');args.insert(1,'-Dunpvp.benchmark=true')
 (run/'options.txt').write_text('guiScale:2\nmaxFps:260\nenableVsync:false\npauseOnLostFocus:false\nrenderDistance:8\nsoundCategory_master:0.0\nmipmapLevels:4\nuseVbo:true\n')
if not a.stock_startup:
 (run/'config').mkdir(exist_ok=True)
 (run/'config/splash.properties').write_text('enabled=false\n')
 args.insert(1,'-Dfml.disableVersionCheck=true')
else:args.insert(1,'-Dunpvp.eagerUi=true')
with (run/'launch.log').open('w') as output:
 proc=subprocess.Popen(args,cwd=run,stdout=output,stderr=subprocess.STDOUT)
 (run/'test-process.json').write_text(json.dumps({'pid':proc.pid}))
 if a.manual:
  (ROOT/'.cache/unpvp-manual/current.json').write_text(json.dumps({'pid':proc.pid,'gameDir':str(run)}))
  deadline=time.monotonic()+90
  while proc.poll() is None and time.monotonic()<deadline:
   if '[unpvp-startup] first-frame-ms=' in (run/'launch.log').read_text(errors='replace'):
    print('READY',proc.pid,run);raise SystemExit(0)
   time.sleep(.5)
  print('Not ready',proc.poll(),run);raise SystemExit(1)
 try:status=proc.wait(timeout=180)
 except subprocess.TimeoutExpired:proc.terminate();status=124
print(run,status)
if status != 0:raise SystemExit(1)
if ('[unpvp-badges] PASS checks=18' if a.badges else '[unpvp-presentation] PASS' if a.presentation else '[unpvp-lobby] PASS' if a.lobby else '[unpvp-response] PASS' if a.response else '[unpvp-pvp-test] PASS' if a.pvp else '[unpvp-solid-test] PASS' if a.solid else '[unpvp-load-test] PASS' if a.load else '[unpvp-retina-test] PASS' if a.retina else '[unpvp-benchmark] PASS' if a.perf else '[unpvp-test] PASS') not in (run/'launch.log').read_text(errors='replace'):raise SystemExit(1)
