#!/usr/bin/env python3
"""Run a local, isolated world benchmark using already prepared game libraries.
Usage: python3 scripts/benchmark.py baseline|optimized|ui|home|social|relay WORLD_DIRECTORY [run-name] [client-jar]
The source world is copied, never opened or changed in place. Keep game foreground.
"""
import hashlib,json,pathlib,shutil,subprocess,sys,urllib.request,uuid,os,zipfile,re
ROOT=pathlib.Path(__file__).resolve().parents[1]
DATA=pathlib.Path.home()/'Library/Application Support/dev.unbox.client'
game_version=os.environ.get('UNBOX_MINECRAFT_VERSION','26.1')
neo_versions={'26.1':'26.1.0.19-beta','26.1.2':'26.1.2.116','26.3':'26.3.0.86'}
if game_version not in neo_versions:raise SystemExit('Unsupported Minecraft version: '+game_version)
RUNTIME=pathlib.Path(os.environ.get('UNBOX_GAME_RUNTIME',str(ROOT/'.cache/runtime-26.3' if game_version=='26.3' else DATA/'runtime')))
neo_version=neo_versions[game_version]
neo_default=ROOT/('.cache/neoforge/runtime' if game_version=='26.1' else f'.cache/neoforge-{game_version}/runtime')
game_libs=[str(p) for p in (RUNTIME/'libraries').rglob('*.jar')] if game_version=='26.3' else [str(ROOT/'.cache/libs/*')]
loader=os.environ.get('UNBOX_TEST_LOADER','fabric')
if loader not in ('fabric','neoforge'):raise SystemExit('Unsupported test loader')
mode=sys.argv[1]
if mode not in ('baseline','optimized','ui','home','social','relay','private-host','private-guest','survival','visual','features'): raise SystemExit('Choose baseline, optimized, ui, home, social or relay')
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
if os.environ.get('UNBOX_TEST_VANILLA_FONT_ATLAS')=='1':
    (game/'config/immediatelyfast.json').write_text(json.dumps({'font_atlas_resizing':False}))
if sys.platform=='darwin' and game_version=='26.3' and loader=='neoforge':
    fml=game/'config/fml.toml';current=fml.read_bytes().decode() if fml.exists() else '';offset=0;updated=None
    for line in current.splitlines(keepends=True):
        if line.lstrip().startswith('['):break
        key,separator,value=line.partition('=')
        if separator and key.strip() in ('earlyWindowControl','"earlyWindowControl"',"'earlyWindowControl'"):
            match=re.fullmatch(r'(\s*)(true|false)(\s*(?:#[^\r\n]*)?\r?\n?)',value)
            if not match:raise SystemExit('Invalid earlyWindowControl value in NeoForge configuration')
            start=offset+len(key)+1+match.start(2);updated=current[:start]+'false'+current[start+len(match.group(2)):];break
        offset+=len(line)
    if updated is None:updated='earlyWindowControl = false\n'+current
    if updated!=current:fml.write_bytes(updated.encode())
if os.environ.get('UNBOX_SYNC_FIXTURE')=='1' and mode=='private-host':
    import zipfile
    fixture_classes=game/'fixture-classes';fixture_classes.mkdir()
    fixture_cp=[str(ROOT/f'.cache/client-{game_version}.jar'),*game_libs]
    if game_version!='26.3':fixture_cp.append(str(ROOT/'.cache/fabric-loader.jar'))
    if loader=='neoforge':
        fixture_neo_root=pathlib.Path(os.environ.get('UNBOX_NEO_RUNTIME',str(neo_default)))
        neo_libs=fixture_neo_root/'libraries'
        if game_version=='26.3':
            neo_meta=json.loads((fixture_neo_root/f'versions/neoforge-{neo_version}/neoforge-{neo_version}.json').read_text())
            base_meta=json.loads((ROOT/f'.cache/{game_version}.json').read_text())
            replaced={':'.join(lib['name'].split(':')[:2]) for lib in neo_meta['libraries']}
            fixture_cp=[str(neo_libs/f'net/neoforged/minecraft-client-patched/{neo_version}/minecraft-client-patched-{neo_version}.jar')]
            fixture_cp.append(str(neo_libs/f'net/neoforged/neoforge/{neo_version}/neoforge-{neo_version}-universal.jar'))
            fixture_cp.extend(str(neo_libs/lib['downloads']['artifact']['path']) for lib in neo_meta['libraries'])
            fixture_cp.extend(str(RUNTIME/'libraries'/lib['downloads']['artifact']['path']) for lib in base_meta['libraries'] if ':'.join(lib['name'].split(':')[:2]) not in replaced and 'artifact' in lib.get('downloads',{}) and (RUNTIME/'libraries'/lib['downloads']['artifact']['path']).is_file())
        else:fixture_cp=[str(p) for p in neo_libs.rglob('*.jar')]+game_libs
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
    # Host-only, original test packs: a visible item name and a minimal passthrough shader.
    for folder in ('resourcepacks','shaderpacks'):(game/folder).mkdir(exist_ok=True)
    with zipfile.ZipFile(ROOT/f'.cache/client-{game_version}.jar') as client_zip:
        pack_version=json.loads(client_zip.read('version.json'))['pack_version']
        resource_format=[pack_version['resource_major'],pack_version['resource_minor']]
    with zipfile.ZipFile(game/'resourcepacks/Unbox Fixture.zip','w') as z:
        z.writestr('pack.mcmeta',json.dumps({'pack':{'description':'Unbox shared resource test','min_format':resource_format,'max_format':resource_format}}))
        z.writestr('assets/unbox_sync_fixture/lang/en_us.json',json.dumps({'item.unbox_sync_fixture.shared_item':'Shared Pack Item'}))
        z.writestr('assets/unbox_sync_fixture/fixture.txt','Shared pack loaded')
    with zipfile.ZipFile(game/'shaderpacks/Unbox Light.zip','w') as z:
        z.writestr('shaders/final.vsh','#version 120\nvarying vec2 texcoord;\nvoid main(){gl_Position=ftransform();texcoord=gl_MultiTexCoord0.xy;}')
        z.writestr('shaders/final.fsh','#version 120\nuniform sampler2D colortex0;\nvarying vec2 texcoord;\nvoid main(){gl_FragData[0]=texture2D(colortex0,texcoord);}')
    (game/'config/iris.properties').write_text('enableShaders=true\nshaderPack=Unbox Light.zip\n')
op=urllib.request.build_opener(urllib.request.ProxyHandler({}))
suffix='' if game_version=='26.1' else '-'+game_version
lock=json.loads((ROOT/f'src-tauri/resources/{"neoforge-" if loader=="neoforge" else ""}performance-lock{suffix}.json').read_text())
wick=next(m for m in json.loads((ROOT/"src-tauri/resources/wick-lock.json").read_text()) if m["loader"]==loader and game_version in m["gameVersions"])
wick["slug"]="wick"
if mode!="baseline":lock.append(wick)
for mod in lock:
    # Interactive regression fixtures need ticks even when their test window loses focus.
    if (mode in ('visual','features','survival') or mode.startswith('private-')) and mod['slug']=='dynamic-fps':continue
    # DynamicFPS is a background limiter, not a foreground FPS optimization.
    if mod['slug']=='dynamic-fps' and mode not in ('ui','home','social','relay','private-host','private-guest','survival','visual','features') or mode=='baseline' and mod['slug'] not in ('fabric-api','freelook'):continue
    target=ROOT/'.cache/benchmark-mods'/mod['filename'];target.parent.mkdir(parents=True,exist_ok=True)
    if not target.exists() or hashlib.sha1(target.read_bytes()).hexdigest()!=mod['sha1']:
        content=op.open(urllib.request.Request(mod['url'],headers={'User-Agent':'Unbox-development-benchmark'}),timeout=60).read()
        if hashlib.sha1(content).hexdigest()!=mod['sha1']:raise SystemExit('Checksum mismatch')
        target.write_bytes(content)
    shutil.copy2(target,game/'mods'/target.name)
if mode in ('survival','features') or os.environ.get('UNBOX_SURVIVAL_MODS')=='1':
    subprocess.run([sys.executable,str(ROOT/'scripts/prepare-survival-mods.py'),loader,game_version,'--output-dir',str(game/'mods')],check=True)
if mode=='ui' and (ROOT/'.cache/friend-face-fixture.json').exists():shutil.copy2(ROOT/'.cache/friend-face-fixture.json',game/'ui-friends-fixture.json')
jar_source=pathlib.Path(sys.argv[4]).resolve() if len(sys.argv)>4 else ROOT/f'src-tauri/resources/unbox-client{"-neoforge" if loader=="neoforge" else ""}{suffix}.jar'
shutil.copy2(jar_source,game/'mods/unbox-client.jar')
(game/'client-build.json').write_text(json.dumps({'sha256':hashlib.sha256(jar_source.read_bytes()).hexdigest(),'mode':mode,'source':str(jar_source)}))
options={'fullscreen':'false','enableVsync':'false','pauseOnLostFocus':'false','renderDistance':'12','simulationDistance':'8','maxFps':'260','inactivityFpsLimit':'"minimized"','guiScale':'2','graphicsMode':'1','fov':'0.0','tutorialStep':'none','onboardAccessibility':'false','soundCategory_master':'0.0'}
if os.environ.get('UNBOX_SYNC_FIXTURE')=='1' and mode=='private-host':options['resourcePacks']=json.dumps(['vanilla','file/Unbox Fixture.zip'])
if os.environ.get('UNBOX_TEST_FONT_PACK')=='1':
    # A pack overriding the core text shader triggers ImmediatelyFast's compatibility
    # fallback even when the shader itself is unchanged. Never override Unbox assets.
    (game/'resourcepacks').mkdir(exist_ok=True)
    with zipfile.ZipFile(ROOT/f'.cache/client-{game_version}.jar') as client_zip,zipfile.ZipFile(game/'resourcepacks/Font Compatibility Fixture.zip','w') as pack:
        pv=json.loads(client_zip.read('version.json'))['pack_version'];fmt=[pv['resource_major'],pv['resource_minor']]
        pack.writestr('pack.mcmeta',json.dumps({'pack':{'description':'Unbox text shader compatibility fixture','min_format':fmt,'max_format':fmt}}))
        shader='assets/minecraft/shaders/core/text.vsh';pack.writestr(shader,client_zip.read(shader))
        if os.environ.get('UNBOX_TEST_LOGO_OVERRIDE')=='1':
            # Valid but deliberately wrong identity art/font at the selected pack's top priority.
            pack.writestr('assets/unbox/font/name_badge.json',json.dumps({'providers':[{'type':'space','advances':{'\ue000':1,'\ue001':1,'\ue002':1}}]}))
            for path in ['ui/badge.png','ui/unbox-creator-crown.png','ui/unbox-fluttering-cash.png','font/unbox-creator-crown.png','font/unbox-fluttering-cash.png']:
                pack.writestr('assets/unbox/textures/'+path,(ROOT/'client-mod/src/main/resources/assets/unbox/textures/ui/close.png').read_bytes())
            pack.writestr('assets/unbox/textures/ui/badge.png.mcmeta',json.dumps({'texture':{'blur':False}}))
    options['resourcePacks']=json.dumps(['vanilla','file/Font Compatibility Fixture.zip'])
if not resume:(game/'options.txt').write_text('\n'.join(k+':'+v for k,v in options.items())+'\n')
(game/'config/unbox.properties').write_text(('minimap=true\nping=true\n' if os.environ.get('UNBOX_MAP_BENCH')=='1' else '')+'fps=true\ncps=true\ncoordinates=true\narmor=true\ncape=false\nfreelook=false\n')
meta=json.loads((ROOT/f'.cache/{game_version}.json').read_text()) if game_version!='26.1' else json.loads((RUNTIME/'26.1.json').read_text());fabric=json.loads((RUNTIME/'fabric-0.19.5.json').read_text());cp=[]
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
cp.append(str(ROOT/f'.cache/client-{game_version}.jar'))
neo=None
if loader=='neoforge':
    neo_root=pathlib.Path(os.environ.get('UNBOX_NEO_RUNTIME',str(neo_default)))
    neo=json.loads((neo_root/f'versions/neoforge-{neo_version}/neoforge-{neo_version}.json').read_text())
    replaced={':'.join(v['name'].split(':')[:2]) for v in neo['libraries']}
    replaced_paths={str(RUNTIME/'libraries'/v['downloads']['artifact']['path']) for v in meta['libraries'] if ':'.join(v['name'].split(':')[:2]) in replaced}
    vanilla_paths={str(RUNTIME/'libraries'/v['downloads']['artifact']['path']) for v in meta['libraries'] if 'artifact' in v.get('downloads',{})}
    cp=[p for p in cp if p in vanilla_paths and p not in replaced_paths]
    cp.extend(str(neo_root/'libraries'/v['downloads']['artifact']['path']) for v in neo['libraries'])
    fabric=neo

for lib in meta['libraries']:
    artifact=lib.get('downloads',{}).get('artifact')
    if not artifact:continue
    target=RUNTIME/'libraries'/artifact['path']
    if str(target) in cp and not target.exists():
        data=op.open(artifact['url'],timeout=60).read();assert hashlib.sha1(data).hexdigest()==artifact['sha1'];target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(data)
java=pathlib.Path(subprocess.check_output(['/usr/libexec/java_home','-v','25'],text=True).strip())/'bin/java'
raw=bytearray(hashlib.md5(b'OfflinePlayer:UnboxTest').digest());raw[6]=(raw[6]&15)|48;raw[8]=(raw[8]&63)|128
args=[str(java),'-XstartOnFirstThread','-Xmx4096M','-Xms512M',('-Dunbox.featureSmoke=true' if mode=='features' else '-Dunbox.visualSmoke=true' if mode=='visual' else '-Dunbox.survivalSmoke=true' if mode=='survival' else '-Dunbox.socialSmoke=true' if mode in ('social','relay') else '-Dunbox.homeSmoke=true' if mode=='home' else '-Dunbox.uiSmoke=true' if mode=='ui' else f'-Dunbox.benchmark={name}'),'-cp',':'.join(cp),fabric['mainClass'],'--username','UnboxTest','--version',game_version,'--gameDir',str(game),'--assetsDir',str(RUNTIME/'assets'),'--assetIndex',meta['assetIndex']['id'],'--uuid',uuid.UUID(bytes=bytes(raw)).hex,'--accessToken','0','--userType','legacy','--versionType','release','--width','1280','--height','720','--quickPlaySingleplayer','Benchmark']
if game_version=='26.3':
    # SDL and the Java foreign-function API require the current vanilla JVM options.
    vanilla_jvm=[]
    for argument in meta['arguments']['jvm']:
        if not isinstance(argument,str) or argument in ('-cp','${classpath}'):continue
        for key,value in {'natives_directory':str(game/'natives'),'launcher_name':'Unbox-development','launcher_version':'local'}.items():argument=argument.replace('${'+key+'}',value)
        vanilla_jvm.append(argument)
    args[1:1]=vanilla_jvm
args.insert(1,'-Dunbox.startupDiagnostics=true')
args.insert(1,'-Dunbox.testUpgradeBackup=true')
if os.environ.get('UNBOX_SURVIVAL_LAN')=='host':
    args=[a for a in args if not a.startswith('-Dunbox.benchmark=')]
    args.insert(1,'-Dunbox.survivalLan=host')
if os.environ.get('UNBOX_VANILLA_INTEROP')=='1':args.insert(1,'-Dunbox.vanillaInterop=true')
if os.environ.get('UNBOX_SYNC_FIXTURE')=='1':args.insert(1,'-Dunbox.syncFixture=true')
if 'UNBOX_SHARED_CACHE' in os.environ:args.insert(1,'-Dunbox.sharedCache='+os.environ['UNBOX_SHARED_CACHE'])
if resume or os.environ.get('UNBOX_COLD_JOIN')=='1':args.insert(1,'-Dunbox.syncResume=true')
if mode=='relay':args.insert(1,'-Dunbox.relaySmoke=true')
if os.environ.get('UNBOX_PERFORMANCE_BENCHMARK')=='1':args.insert(1,'-Dunbox.performanceBenchmark=true')
if os.environ.get('UNBOX_PERFORMANCE_SMOKE')=='1':args.insert(1,'-Dunbox.performanceSmoke=true')
if os.environ.get('UNBOX_TOGGLE_ONLY')=='1':args.insert(1,'-Dunbox.toggleOnly=true')
if mode in ('home','private-guest'):args=args[:-2]
if mode.startswith('private-'):
    fixture=json.loads(os.environ['UNBOX_TEST_PROFILE'])
    args.insert(1,'-Dunbox.privateSmoke='+mode.removeprefix('private-'))
    args.insert(1,'-Dunbox.privateSmokeTicks='+os.environ.get('UNBOX_PRIVATE_TEST_TICKS','100'))
    args.insert(1,'-Dunbox.accountKind='+json.loads(os.environ.get('UNBOX_TEST_PROFILE','{}')).get('type','local'))
    args=[a for a in args if not a.startswith('-Dunbox.benchmark=')]
    args[args.index('--username')+1]=fixture['name']
    args[args.index('--uuid')+1]=fixture['uuid']
    args[args.index('-Xmx4096M')]='-Xmx2048M'
if neo:
    args[1:1]=[v.replace('${library_directory}',str(neo_root/'libraries')) for v in neo['arguments']['jvm']]
    args.extend(neo['arguments']['game'])
(game/'logs').mkdir(exist_ok=True)
(game/'test-launch-args.json').write_text(json.dumps(args))
print('Starting',name,'in isolated copy',game,flush=True)
with (game/'logs/launch.log').open('w') as log:
    p=subprocess.Popen(args,cwd=game,stdout=log,stderr=log)
    print('Java PID',p.pid,flush=True)
    try:code=p.wait(timeout=240)
    except subprocess.TimeoutExpired:
        p.terminate();code=p.wait(timeout=30)
result=game/('logs/survival-lan-host.json' if os.environ.get('UNBOX_SURVIVAL_LAN')=='host' else 'logs/feature-smoke.json' if mode=='features' else 'logs/visual-smoke.json' if mode=='visual' else 'logs/survival-smoke.json' if mode=='survival' else 'logs/vanilla-interop.json' if os.environ.get('UNBOX_VANILLA_INTEROP')=='1' else 'logs/private-world-smoke.json' if mode.startswith('private-') else 'logs/social-smoke.json' if mode in ('social','relay') else 'logs/home-smoke.json' if mode=='home' else 'logs/ui-smoke.json' if mode=='ui' else 'logs/benchmark.json')
report_ok=False
if result.exists():
    report_text=result.read_text();print(report_text,flush=True)
    try:report_ok=json.loads(report_text).get('ok',True) is not False
    except (ValueError,AttributeError):report_ok=False
else:print('No benchmark result; inspect',game/'logs/launch.log',flush=True)
raise SystemExit(code if code else (0 if report_ok else 1))
