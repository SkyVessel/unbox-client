#!/usr/bin/env python3
"""Compile Unbox sources against the pinned, native NeoForge client for each version."""
import pathlib,subprocess,os,json,shutil,zipfile,hashlib,urllib.request,sys
ROOT=pathlib.Path(__file__).resolve().parents[1];os.chdir(ROOT)
subprocess.run([sys.executable,str(ROOT/'scripts/prepare-voxlink.py')],check=True)
version=os.environ.get('UNBOX_MINECRAFT_VERSION','26.1')
neo_versions={'26.1':'26.1.0.19-beta','26.1.2':'26.1.2.116','26.3':'26.3.0.86'}
if version not in neo_versions:raise SystemExit('Unsupported Minecraft version: '+version)
neo_version=neo_versions[version];suffix='' if version=='26.1' else '-'+version
runtime=pathlib.Path(os.environ.get('UNBOX_NEO_RUNTIME',str(ROOT/('.cache/neoforge/runtime' if version=='26.1' else f'.cache/neoforge-{version}/runtime'))))
libs=runtime/'libraries';patched=libs/f'net/neoforged/minecraft-client-patched/{neo_version}/minecraft-client-patched-{neo_version}.jar'
if not patched.exists():raise SystemExit(f'Install the pinned NeoForge {version} runtime first; set UNBOX_NEO_RUNTIME if needed.')
cache=ROOT/('.cache/26.3' if version=='26.3' else '.cache');cache.mkdir(parents=True,exist_ok=True)
if version=='26.3':subprocess.run([sys.executable,str(ROOT/'scripts/prepare-version-deps.py'),version,'neoforge'],check=True)
r=next(x for x in json.loads((ROOT/f'src-tauri/resources/neoforge-performance-lock{suffix}.json').read_text()) if x['slug']=='e4mc');dep=cache/'e4mc-neoforge.jar'
if not dep.exists() or hashlib.sha512(dep.read_bytes()).hexdigest()!=r['sha512']:
 data=urllib.request.build_opener(urllib.request.ProxyHandler({})).open(r['url'],timeout=60).read();assert hashlib.sha512(data).hexdigest()==r['sha512'];dep.write_bytes(data)
(cache/'e4mc-neoforge-deps').mkdir(exist_ok=True)
with zipfile.ZipFile(dep) as z:
 for n in z.namelist():
  if n.startswith('META-INF/jars/kaleido-config-') and n.endswith('.jar'):(cache/'e4mc-neoforge-deps'/pathlib.Path(n).name).write_bytes(z.read(n))
build=ROOT/('client-mod/build/neoforge'+suffix);shutil.rmtree(build,ignore_errors=True);build.mkdir(parents=True)
game_runtime=pathlib.Path(os.environ.get('UNBOX_GAME_RUNTIME',str(ROOT/f'.cache/runtime-{version}')))
if version=='26.3':
 neo=json.loads((runtime/f'versions/neoforge-{neo_version}/neoforge-{neo_version}.json').read_text())
 meta=json.loads((ROOT/f'.cache/{version}.json').read_text())
 replaced={':'.join(lib['name'].split(':')[:2]) for lib in neo['libraries']}
 # Installer tools contain their own dependency classes; they are not game libraries.
 neo_libs=[str(libs/lib['downloads']['artifact']['path']) for lib in neo['libraries']]
 neo_libs.append(str(libs/f'net/neoforged/neoforge/{neo_version}/neoforge-{neo_version}-universal.jar'))
 game_libs=[str(game_runtime/'libraries'/lib['downloads']['artifact']['path']) for lib in meta['libraries'] if ':'.join(lib['name'].split(':')[:2]) not in replaced and 'artifact' in lib.get('downloads',{}) and (game_runtime/'libraries'/lib['downloads']['artifact']['path']).is_file()]
else:
 neo_libs=[str(p) for p in libs.rglob('*.jar') if p!=patched]
 game_libs=[str(ROOT/'.cache/libs/*')]
cp=[str(ROOT/'.cache/voxlink/classes'),str(patched),*game_libs,str(dep),*neo_libs,str(cache/'e4mc-neoforge-deps/*')]
sources=list((ROOT/'client-mod/src/main/java').rglob('*.java'))+list((ROOT/'client-mod/src/neoforge/java').rglob('*.java'))
if version=='26.3':
 source_root=pathlib.Path(subprocess.check_output([sys.executable,str(ROOT/'scripts/prepare-version-sources.py'),version,'neoforge'],text=True).strip())
 sources=list(source_root.rglob('*.java'))
subprocess.run(['javac','--release','25','-proc:none','-cp',os.pathsep.join(cp),'-d',str(build),*map(str,sources)],check=True)
for file in build.rglob('*.class'):
    data=file.read_bytes()
    if b'net/fabricmc/' in data or b'freelook/freelook/' in data:
        raise SystemExit('Fabric-only dependency leaked into native NeoForge build: '+str(file))
shutil.copytree(ROOT/'.cache/voxlink/classes',build,dirs_exist_ok=True)
shutil.copytree(ROOT/'client-mod/src/main/resources',build,dirs_exist_ok=True);(build/'fabric.mod.json').unlink()
shutil.copytree(ROOT/'client-mod/src/neoforge/resources',build,dirs_exist_ok=True)
toml=build/'META-INF/neoforge.mods.toml';toml.write_text(toml.read_text().replace('versionRange="[26.1]"',f'versionRange="[{version}]"'))
if version=='26.3':toml.write_text(toml.read_text().replace('versionRange="[26.1.0.19-beta,26.2)"','versionRange="[26.3.0.86,26.4)"'))
p=build/'unbox.mixins.json';d=json.loads(p.read_text());d['client'].remove('FreelookMixin');d['client']+=['NeoFreelookCameraMixin','NeoLoginQueryMixin','NeoLoginAnswerMixin','NeoLoginClientMixin','NeoLoginServerMixin'];p.write_text(json.dumps(d))
subprocess.run(['jar','--create','--file',str(ROOT/('src-tauri/resources/unbox-client-neoforge'+suffix+'.jar')),'-C',str(build),'.'],check=True)
print('Built native NeoForge Unbox module')
