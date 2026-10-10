#!/usr/bin/env python3
"""Compile common Unbox sources directly against the installed NeoForge 26.1 client."""
import pathlib,subprocess,os,json,shutil,zipfile,hashlib,urllib.request
ROOT=pathlib.Path(__file__).resolve().parents[1];os.chdir(ROOT)
runtime=pathlib.Path(os.environ.get('UNBOX_NEO_RUNTIME',str(ROOT/'.cache/neoforge/runtime')))
libs=runtime/'libraries';patched=libs/'net/neoforged/minecraft-client-patched/26.1.0.19-beta/minecraft-client-patched-26.1.0.19-beta.jar'
if not patched.exists():raise SystemExit('Install the pinned NeoForge 26.1 runtime first; set UNBOX_NEO_RUNTIME if needed.')
r=next(x for x in json.loads((ROOT/'src-tauri/resources/neoforge-performance-lock.json').read_text()) if x['slug']=='e4mc');dep=ROOT/'.cache/e4mc-neoforge.jar'
if not dep.exists() or hashlib.sha512(dep.read_bytes()).hexdigest()!=r['sha512']:
 data=urllib.request.build_opener(urllib.request.ProxyHandler({})).open(r['url'],timeout=60).read();assert hashlib.sha512(data).hexdigest()==r['sha512'];dep.write_bytes(data)
(ROOT/'.cache/e4mc-neoforge-deps').mkdir(exist_ok=True)
with zipfile.ZipFile(dep) as z:
 for n in z.namelist():
  if n.startswith('META-INF/jars/kaleido-config-') and n.endswith('.jar'):(ROOT/'.cache/e4mc-neoforge-deps'/pathlib.Path(n).name).write_bytes(z.read(n))
build=ROOT/'client-mod/build/neoforge';shutil.rmtree(build,ignore_errors=True);build.mkdir(parents=True)
cp=[str(patched),str(dep),*[str(p) for p in libs.rglob('*.jar') if p!=patched],str(ROOT/'.cache/e4mc-neoforge-deps/*')]
sources=list((ROOT/'client-mod/src/main/java').rglob('*.java'))+list((ROOT/'client-mod/src/neoforge/java').rglob('*.java'))
subprocess.run(['javac','--release','25','-proc:none','-cp',os.pathsep.join(cp),'-d',str(build),*map(str,sources)],check=True)
for file in build.rglob('*.class'):
    data=file.read_bytes()
    if b'net/fabricmc/' in data or b'freelook/freelook/' in data:
        raise SystemExit('Fabric-only dependency leaked into native NeoForge build: '+str(file))
shutil.copytree(ROOT/'client-mod/src/main/resources',build,dirs_exist_ok=True);(build/'fabric.mod.json').unlink()
shutil.copytree(ROOT/'client-mod/src/neoforge/resources',build,dirs_exist_ok=True)
p=build/'unbox.mixins.json';d=json.loads(p.read_text());d['client'].remove('FreelookMixin');d['client']+=['NeoFreelookCameraMixin','NeoLoginQueryMixin','NeoLoginAnswerMixin','NeoLoginClientMixin','NeoLoginServerMixin'];p.write_text(json.dumps(d))
subprocess.run(['jar','--create','--file',str(ROOT/'src-tauri/resources/unbox-client-neoforge.jar'),'-C',str(build),'.'],check=True)
print('Built native NeoForge Unbox module')
