#!/usr/bin/env python3
"""26.3 production module, isolated menu checks or an uninstrumented manual profile."""
from pathlib import Path
import argparse,json,os,shutil,subprocess,datetime
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--loader',choices=['fabric','neoforge'],default='fabric');p.add_argument('--manual',action='store_true');p.add_argument('--theme',type=int,choices=[1,2],default=1);p.add_argument('--checks-only',action='store_true');p.add_argument('--layout-checks',action='store_true');p.add_argument('--hud-checks',action='store_true');p.add_argument('--polish-checks',action='store_true');p.add_argument('--name');p.add_argument('--gui-scale',type=int,default=0);p.add_argument('--width',type=int,default=960);p.add_argument('--height',type=int,default=540);a=p.parse_args()
run=ROOT/'.cache/solid-ui'/('manual-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S') if a.manual else a.name or 'check-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
if run.exists():raise SystemExit('Use a fresh run name')
(run/'mods').mkdir(parents=True)
(run/'config').mkdir();(run/'config/unbox.properties').write_text('ui.theme='+str(a.theme)+'\n')
if a.loader=='neoforge':
 (run/'config/fml.toml').write_text('earlyWindowControl = false\n')
shutil.copy2(ROOT/'src-tauri/resources'/('unbox-client'+('-neoforge' if a.loader=='neoforge' else '')+'-26.3.jar'),run/'mods/unbox-client.jar')
lock=json.loads((ROOT/'src-tauri/resources'/('neoforge-performance-lock-26.3.json' if a.loader=='neoforge' else 'performance-lock-26.3.json')).read_text())
for item in lock:
 if not a.manual and item['slug']=='dynamic-fps':continue
 source=ROOT/'.cache/26.3/mods'/item['filename']
 if source.exists():
  import hashlib
  if hashlib.sha512(source.read_bytes()).hexdigest()!=item['sha512']:raise SystemExit('Dependency checksum mismatch '+item['filename'])
  shutil.copy2(source,run/'mods'/item['filename'])
 else:print('Not in local test cache:',item['slug'])
(run/'options.txt').write_text('guiScale:'+str(a.gui_scale)+'\nmaxFps:120\nenableVsync:false\npauseOnLostFocus:false\nrenderDistance:4\nsoundCategory_master:0.0\nonboardAccessibility:false\ninactivityFpsLimit:minimized\n')
runtime=ROOT/'.cache/runtime-26.3';meta=json.loads((runtime/'26.3.json').read_text());fabric=json.loads((runtime/'fabric-0.19.5.json').read_text());libs=[]
for lib in meta['libraries']:
 rules=lib.get('rules');allowed=not rules
 for rule in rules or []:
  if rule.get('os',{}).get('name','osx')=='osx':allowed=rule['action']=='allow'
 if allowed and 'artifact' in lib.get('downloads',{}):libs.append(runtime/'libraries'/lib['downloads']['artifact']['path'])
for lib in fabric['libraries']:
 g,n,v=lib['name'].split(':');libs.append(runtime/'libraries'/g.replace('.','/')/n/v/(n+'-'+v+'.jar'))
libs.append(ROOT/'.cache/client-26.3.jar')
main=fabric['mainClass'];extra=[];loader_flags=[]
if a.loader=='neoforge':
 neo_root=ROOT/'.cache/neoforge-26.3/runtime';neo=json.loads((neo_root/'versions/neoforge-26.3.0.86/neoforge-26.3.0.86.json').read_text())
 replaced={':'.join(lib['name'].split(':')[:2]) for lib in neo['libraries']}
 libs=[runtime/'libraries'/lib['downloads']['artifact']['path'] for lib in meta['libraries'] if ':'.join(lib['name'].split(':')[:2]) not in replaced and 'artifact' in lib.get('downloads',{}) and (runtime/'libraries'/lib['downloads']['artifact']['path']).exists()]
 libs += [neo_root/'libraries'/lib['downloads']['artifact']['path'] for lib in neo['libraries']]
 main=neo['mainClass'];extra=neo['arguments']['game'];loader_flags=[arg.replace('${library_directory}',str(neo_root/'libraries')) for arg in neo['arguments']['jvm']]

flags=['-Djava.awt.headless=true','-Xms256M','-Xmx2048M','-XstartOnFirstThread','--enable-native-access=ALL-UNNAMED','--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED','-Dorg.lwjgl.system.SharedLibraryExtractPath='+str(run/'natives')]
if not a.manual:flags.append('-Dunbox.solidUiTest=true')
if a.checks_only:flags.append('-Dunbox.solidUiTestStart=18')
if a.layout_checks:flags.append('-Dunbox.solidUiTestStart=70')
if a.hud_checks:flags.append('-Dunbox.solidUiTestStart=100')
if a.polish_checks:flags.append('-Dunbox.solidUiTestStart=110')
args=[shutil.which('java'),*flags,*loader_flags,'-cp',os.pathsep.join(map(str,libs)),main,*extra,'--username','UnboxUITest','--uuid','00000000000000000000000000000001','--accessToken','0','--version','26.3','--gameDir',str(run),'--assetsDir',str(runtime/'assets'),'--assetIndex',meta['assetIndex']['id'],'--width',str(a.width),'--height',str(a.height)]
(run/'launch-args.json').write_text(json.dumps(args,indent=2))
with (run/'launch.log').open('w') as log:
 proc=subprocess.Popen(args,cwd=run,stdout=log,stderr=subprocess.STDOUT,start_new_session=True);(run/'pid').write_text(str(proc.pid));print(run,flush=True);print('PID',proc.pid,flush=True)
 if a.manual:raise SystemExit(0)
 try:code=proc.wait(timeout=360)
 except subprocess.TimeoutExpired:proc.terminate();code=124
print('exit',code,'checks',(run/'solid-proof/checks.json').exists())
if not (run/'solid-proof/checks.json').exists():raise SystemExit(1)
