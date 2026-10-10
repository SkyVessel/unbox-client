#!/usr/bin/env python3
"""Run a local, isolated 26.1 world benchmark using already prepared game libraries.
Usage: python3 scripts/benchmark.py baseline|optimized|ui|home|social|relay WORLD_DIRECTORY [run-name] [client-jar]
The source world is copied, never opened or changed in place. Keep game foreground.
"""
import hashlib,json,pathlib,shutil,subprocess,sys,urllib.request,uuid,os
ROOT=pathlib.Path(__file__).resolve().parents[1]
DATA=pathlib.Path.home()/'Library/Application Support/dev.unbox.client'
RUNTIME=DATA/'runtime'
loader=os.environ.get('UNBOX_TEST_LOADER','fabric')
if loader not in ('fabric','neoforge'):raise SystemExit('Unsupported test loader')
mode=sys.argv[1]
if mode not in ('baseline','optimized','ui','home','social','relay','private-host','private-guest'): raise SystemExit('Choose baseline, optimized, ui, home, social or relay')
source=pathlib.Path(sys.argv[2]).resolve()
if not (source/'level.dat').is_file(): raise SystemExit('A saved world with level.dat is required')
name=sys.argv[3] if len(sys.argv)>3 else mode
if not name.replace('-','').isalnum():raise SystemExit('Invalid run name')
game=pathlib.Path(os.environ['UNBOX_BENCH_GAME_DIR']) if 'UNBOX_BENCH_GAME_DIR' in os.environ else ROOT/'.cache/benchmarks'/name
resume=os.environ.get('UNBOX_BENCH_RESUME')=='1'
if resume and mode!='private-guest':raise SystemExit('Resume is limited to private guest fixtures')
if not resume:
    if game.exists():raise SystemExit('Choose a fresh run name; existing results are preserved')
    (game/'saves').mkdir(parents=True)
    shutil.copytree(source,game/'saves/Benchmark',ignore=shutil.ignore_patterns('session.lock'))
(game/'mods').mkdir(exist_ok=True);(game/'config').mkdir(exist_ok=True)
if os.environ.get('UNBOX_SYNC_FIXTURE')=='1' and mode=='private-host':
    import zipfile
    fixture_classes=game/'fixture-classes';fixture_classes.mkdir()
    fixture_cp=[str(ROOT/'.cache/client-26.1.jar'),str(ROOT/'.cache/fabric-loader.jar'),str(ROOT/'.cache/libs/*')]
    if loader=='neoforge':
        neo_libs=pathlib.Path(os.environ.get('UNBOX_NEO_RUNTIME',str(ROOT/'.cache/neoforge/runtime')))/'libraries'
        fixture_cp=[str(p) for p in neo_libs.rglob('*.jar')]+[str(ROOT/'.cache/libs/*')]
    fixture_sources=list((ROOT/'tests/fixtures/common').rglob('*.java'))+list((ROOT/'tests/fixtures'/loader).rglob('*.java'))
    subprocess.run(['javac','--release','25','-proc:none','-cp',os.pathsep.join(fixture_cp),'-d',str(fixture_classes),*map(str,fixture_sources)],check=True)
    with zipfile.ZipFile(game/'mods/shared-fixture.jar','w') as z:
        if loader=='neoforge':
            z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[11,)"\nlicense="MIT"\n[[mods]]\nmodId="unbox_sync_fixture"\nversion="1.0.0"\n')
        else:
            z.writestr('fabric.mod.json',json.dumps({'schemaVersion':1,'id':'unbox_sync_fixture','version':'1.0.0','name':'Shared Gameplay Fixture','environment':'*','entrypoints':{'main':['dev.unbox.fixture.SharedItemFixture']}}))
        z.writestr('assets/unbox_sync_fixture/items/shared_item.json',json.dumps({'model':{'type':'minecraft:model','model':'minecraft:item/stick'}}))
        z.writestr('fixture.bin',bytes(range(256))*8192)
        for cls in fixture_classes.rglob('*.class'):z.write(cls,str(cls.relative_to(fixture_classes)))
op=urllib.request.build_opener(urllib.request.ProxyHandler({}))
lock=json.loads((ROOT/('src-tauri/resources/neoforge-performance-lock.json' if loader=='neoforge' else 'src-tauri/resources/performance-lock.json')).read_text())
for mod in lock:
    # DynamicFPS is a background limiter, not a foreground FPS optimization.
    if mod['slug']=='dynamic-fps' and mode not in ('ui','home','social','relay','private-host','private-guest') or mode=='baseline' and mod['slug'] not in ('fabric-api','freelook'):continue
    target=ROOT/'.cache/benchmark-mods'/mod['filename'];target.parent.mkdir(parents=True,exist_ok=True)
    if not target.exists() or hashlib.sha1(target.read_bytes()).hexdigest()!=mod['sha1']:
        content=op.open(urllib.request.Request(mod['url'],headers={'User-Agent':'Unbox-development-benchmark'}),timeout=60).read()
        if hashlib.sha1(content).hexdigest()!=mod['sha1']:raise SystemExit('Checksum mismatch')
        target.write_bytes(content)
    shutil.copy2(target,game/'mods'/target.name)
if mode=='ui' and (ROOT/'.cache/friend-face-fixture.json').exists():shutil.copy2(ROOT/'.cache/friend-face-fixture.json',game/'ui-friends-fixture.json')
jar_source=pathlib.Path(sys.argv[4]).resolve() if len(sys.argv)>4 else ROOT/('src-tauri/resources/unbox-client-neoforge.jar' if loader=='neoforge' else 'src-tauri/resources/unbox-client.jar')
shutil.copy2(jar_source,game/'mods/unbox-client.jar')
(game/'client-build.json').write_text(json.dumps({'sha256':hashlib.sha256(jar_source.read_bytes()).hexdigest(),'mode':mode,'source':str(jar_source)}))
options={'fullscreen':'false','enableVsync':'false','pauseOnLostFocus':'false','renderDistance':'12','simulationDistance':'8','maxFps':'260','inactivityFpsLimit':'"minimized"','guiScale':'2','graphicsMode':'1','fov':'0.0','tutorialStep':'none','onboardAccessibility':'false','soundCategory_master':'0.0'}
(game/'options.txt').write_text('\n'.join(k+':'+v for k,v in options.items())+'\n')
(game/'config/unbox.properties').write_text(('minimap=true\nping=true\n' if os.environ.get('UNBOX_MAP_BENCH')=='1' else '')+'fps=true\ncps=true\ncoordinates=true\narmor=true\ncape=false\nfreelook=false\n')
meta=json.loads((RUNTIME/'26.1.json').read_text());fabric=json.loads((RUNTIME/'fabric-0.19.5.json').read_text());cp=[]
for lib in meta['libraries']:
    allow=True
    if 'rules' in lib:
        allow=False
        for r in lib['rules']:
            if r.get('os',{}).get('name','osx')!='osx' or 'features' in r:continue
            allow=r['action']=='allow'
    if allow and 'artifact' in lib.get('downloads',{}):cp.append(str(RUNTIME/'libraries'/lib['downloads']['artifact']['path']))
for lib in fabric['libraries']:
    group,artifact,version=lib['name'].split(':');cp.append(str(RUNTIME/'libraries'/group.replace('.','/')/artifact/version/f'{artifact}-{version}.jar'))
cp.append(str(RUNTIME/'26.1.jar'))
neo=None
if loader=='neoforge':
    neo_root=pathlib.Path(os.environ.get('UNBOX_NEO_RUNTIME',str(ROOT/'.cache/neoforge/runtime')))
    neo=json.loads((neo_root/'versions/neoforge-26.1.0.19-beta/neoforge-26.1.0.19-beta.json').read_text())
    replaced={':'.join(v['name'].split(':')[:2]) for v in neo['libraries']}
    replaced_paths={str(RUNTIME/'libraries'/v['downloads']['artifact']['path']) for v in meta['libraries'] if ':'.join(v['name'].split(':')[:2]) in replaced}
    vanilla_paths={str(RUNTIME/'libraries'/v['downloads']['artifact']['path']) for v in meta['libraries'] if 'artifact' in v.get('downloads',{})}
    cp=[p for p in cp if p in vanilla_paths and p not in replaced_paths]
    cp.extend(str(neo_root/'libraries'/v['downloads']['artifact']['path']) for v in neo['libraries'])
    fabric=neo

java=pathlib.Path(subprocess.check_output(['/usr/libexec/java_home','-v','25'],text=True).strip())/'bin/java'
raw=bytearray(hashlib.md5(b'OfflinePlayer:UnboxTest').digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
args=[str(java),'-XstartOnFirstThread','-Xmx4096M','-Xms512M',('-Dunbox.socialSmoke=true' if mode in ('social','relay') else '-Dunbox.homeSmoke=true' if mode=='home' else '-Dunbox.uiSmoke=true' if mode=='ui' else f'-Dunbox.benchmark={name}'),'-cp',':'.join(cp),fabric['mainClass'],'--username','UnboxTest','--version','26.1','--gameDir',str(game),'--assetsDir',str(RUNTIME/'assets'),'--assetIndex',meta['assetIndex']['id'],'--uuid',uuid.UUID(bytes=bytes(raw)).hex,'--accessToken','0','--userType','legacy','--versionType','release','--width','1280','--height','720','--quickPlaySingleplayer','Benchmark']
if os.environ.get('UNBOX_SYNC_FIXTURE')=='1':args.insert(1,'-Dunbox.syncFixture=true')
if 'UNBOX_SHARED_CACHE' in os.environ:args.insert(1,'-Dunbox.sharedCache='+os.environ['UNBOX_SHARED_CACHE'])
if resume:args.insert(1,'-Dunbox.syncResume=true')
if mode=='relay':args.insert(1,'-Dunbox.relaySmoke=true')
if mode in ('home','private-guest'):args=args[:-2]
if mode.startswith('private-'):
    fixture=json.loads(os.environ['UNBOX_TEST_PROFILE'])
    args.insert(1,'-Dunbox.privateSmoke='+mode.removeprefix('private-'))
    args=[a for a in args if not a.startswith('-Dunbox.benchmark=')]
    args[args.index('--username')+1]=fixture['name']
    args[args.index('--uuid')+1]=fixture['uuid']
    args[args.index('-Xmx4096M')]='-Xmx2048M'
if neo:
    args[1:1]=[v.replace('${library_directory}',str(neo_root/'libraries')) for v in neo['arguments']['jvm']]
    args.extend(neo['arguments']['game'])
(game/'logs').mkdir(exist_ok=True)
print('Starting',name,'in isolated copy',game,flush=True)
with (game/'logs/launch.log').open('w') as log:
    p=subprocess.Popen(args,cwd=game,stdout=log,stderr=log)
    print('Java PID',p.pid,flush=True)
    try:code=p.wait(timeout=240)
    except subprocess.TimeoutExpired:
        p.terminate();code=p.wait(timeout=30)
result=game/('logs/private-world-smoke.json' if mode.startswith('private-') else 'logs/social-smoke.json' if mode in ('social','relay') else 'logs/home-smoke.json' if mode=='home' else 'logs/ui-smoke.json' if mode=='ui' else 'logs/benchmark.json')
if result.exists():print(result.read_text(),flush=True)
else:print('No benchmark result; inspect',game/'logs/launch.log',flush=True)
raise SystemExit(code if code else (0 if result.exists() else 1))
