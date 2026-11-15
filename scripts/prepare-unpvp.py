#!/usr/bin/env python3
"""Resolve official runtime inputs for the independent UI preview (no gameplay mods)."""
from pathlib import Path
import importlib.util,json,hashlib,zipfile,concurrent.futures
ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('build',ROOT/'scripts/build-unpvp.py');build=importlib.util.module_from_spec(spec);spec.loader.exec_module(build)
fetch=build.fetch;work=build.WORK
installer=build.maven(f'https://maven.minecraftforge.net/net/minecraftforge/forge/{build.FORGE}/forge-{build.FORGE}-installer.jar','forge-installer.jar')
with zipfile.ZipFile(installer) as z:forge=json.loads(z.read('install_profile.json'))['versionInfo']
def artifact(lib):
    g,a,v=lib['name'].split(':');path=f'{g.replace(".","/")}/{a}/{v}/{a}-{v}.jar'
    if a=='forge':path=path[:-4]+'-universal.jar'
    url=lib.get('url','https://libraries.minecraft.net/').rstrip('/')+'/'+path
    try:sha=fetch(url+'.sha1',work/'checksums'/(hashlib.sha1(url.encode()).hexdigest()+'.sha1')).read_text().strip().split()[0]
    except Exception:
        p=fetch(url,work/'libraries'/path);sha=hashlib.sha1(p.read_bytes()).hexdigest()
    return dict(path=path,url=url,sha1=sha)
with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:libraries=list(pool.map(artifact,forge['libraries']))
manifest=json.loads((work/'manifest.json').read_text());version=next(v for v in manifest['versions'] if v['id']=='26.1');modern=json.loads(fetch(version['url'],work/'modern.json',version['sha1']).read_text())
java={}
for os in ['mac','windows','linux']:
    data=json.loads(fetch(f'https://api.adoptium.net/v3/assets/latest/8/hotspot?architecture=x64&image_type=jre&os={os}',work/f'java8-{os}.json').read_text())
    package=data[0]['binary']['package'];java[os]=dict(url=package['link'],sha256=package['checksum'],name=package['name'])
index=json.loads(fetch(modern['assetIndex']['url'],work/'modern-assets.json',modern['assetIndex']['sha1']).read_text())
background=[]
for name,entry in index['objects'].items():
 if name.startswith('minecraft/textures/gui/title/background/panorama_'):
  h=entry['hash'];background.append(dict(path=name.rsplit('/',1)[1],url=f'https://resources.download.minecraft.net/{h[:2]}/{h}',sha1=h))
lock=dict(minecraft=json.loads((work/'minecraft.json').read_text()),forge=dict(version=build.FORGE,mainClass=forge['mainClass'],libraries=libraries),java=java,panorama=background)
p=ROOT/'src-tauri/resources/unpvp-runtime.json';p.write_text(json.dumps(lock,indent=2)+'\n');print(p)
