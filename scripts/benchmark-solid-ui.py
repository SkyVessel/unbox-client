#!/usr/bin/env python3
"""Inject the same fixed-view harness into before/after copies, never production artifacts."""
import argparse,json,subprocess,shutil,os,zipfile,hashlib,time
from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('name');p.add_argument('--jar',required=True);p.add_argument('--loader',default='fabric',choices=['fabric','neoforge']);p.add_argument('--fixture');p.add_argument('--create',action='store_true');p.add_argument('--jfr',action='store_true');p.add_argument('--compat-only',action='store_true');p.add_argument('--extra-mod',action='append',default=[]);p.add_argument('--full-profile');p.add_argument('--world-only',action='store_true');a=p.parse_args()
run=root/'.cache/ui-performance'/a.name
if run.exists():raise SystemExit('Use a fresh run name')
run.mkdir(parents=True);(run/'mods').mkdir();(run/'config').mkdir()
if a.fixture:shutil.copytree(Path(a.fixture),run/'saves/Benchmark',ignore=shutil.ignore_patterns('session.lock'))
# Reuse the verified native runtime command and all existing test dependencies.
reference=root/'.cache/solid-ui'/('hud-entry-'+a.loader)
args=json.loads((reference/'launch-args.json').read_text())
args=[s.replace(str(reference),str(run)) for s in args if not s.startswith('-Dunbox.solidUiTest')]
args=[('-Xmx4096M' if s.startswith('-Xmx') else s) for s in args]
for flag,value in [('--width','1280'),('--height','720')]:args[args.index(flag)+1]=value
args.insert(1,'-Dunbox.fixedViewBench=true')
if a.create:args.insert(1,'-Dunbox.createFixture=true')
if a.compat_only:args.insert(1,'-Dunbox.compatOnly=true')
if a.world_only:args.insert(1,'-Dunbox.worldOnly=true')
if a.jfr:args.insert(1,'-XX:StartFlightRecording=filename='+str(run/'recording.jfr')+',settings=profile,dumponexit=true')
for mod in (reference/'mods').glob('*.jar'):
 if mod.name!='unbox-client.jar' and not mod.name.startswith('dynamic-fps'):shutil.copy2(mod,run/'mods'/mod.name)
if a.full_profile:
 for mod in (Path(a.full_profile)/'mods').glob('*.jar'):
  if mod.name!='unbox-client.jar' and not mod.name.startswith('dynamic-fps'):shutil.copy2(mod,run/'mods'/mod.name)
for mod in a.extra_mod:shutil.copy2(mod,run/'mods'/Path(mod).name)
if a.loader=='neoforge':(run/'config/fml.toml').write_text('earlyWindowControl = false\n')
(run/'options.txt').write_text('guiScale:2\nmaxFps:260\nenableVsync:false\npauseOnLostFocus:false\nrenderDistance:12\nsimulationDistance:8\nsoundCategory_master:0.0\nonboardAccessibility:false\ninactivityFpsLimit:minimized\n')
(run/'config/unbox.properties').write_text('ui.theme=2\nui.opacity=55\nfps=true\nminimap=true\ncoordinates=true\narmor=true\n')
jar=Path(a.jar).resolve();classes=run/'harness';classes.mkdir()
cp=str(root/'.cache/client-26.3.jar')+os.pathsep+args[args.index('-cp')+1]+os.pathsep+str(jar)
subprocess.run(['javac','--release','25','-proc:none','-cp',cp,'-d',str(classes),str(root/'tests/performance-26.3/FrameBenchmark.java')],check=True)
with zipfile.ZipFile(jar) as old,zipfile.ZipFile(run/'mods/unbox-client.jar','w',zipfile.ZIP_DEFLATED) as new:
 for info in old.infolist():
  if info.filename!='dev/unbox/client/FrameBenchmark.class':new.writestr(info,old.read(info))
 new.write(classes/'dev/unbox/client/FrameBenchmark.class','dev/unbox/client/FrameBenchmark.class')
(run/'provenance.json').write_text(json.dumps({'sourceJar':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'fixture':a.fixture,'fullProfile':a.full_profile,'cameraInput':False,'args':args},indent=2))
print(run,flush=True)
with (run/'launch.log').open('w') as log:
 start=time.monotonic();proc=subprocess.Popen(args,cwd=run,stdout=log,stderr=subprocess.STDOUT);(run/'pid').write_text(str(proc.pid))
 try:code=proc.wait(timeout=420)
 except subprocess.TimeoutExpired:proc.terminate();code=124
print('exit',code,'elapsed',round(time.monotonic()-start,2),flush=True)
result=run/('fixture-ready.txt' if a.create else 'bench/results.json')
if not result.exists():raise SystemExit('No result: '+str(run))
print(result.read_text())
