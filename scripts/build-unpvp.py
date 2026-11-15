#!/usr/bin/env python3
"""Build the isolated Forge 1.8.9 UI and PvP module from upstream inputs and modern UI assets.
No expvp source, build outputs, or network adapters enter this build.
"""
from pathlib import Path
import csv, hashlib, io, json, os, re, shutil, subprocess, urllib.request, zipfile
from unpvp_catalog import catalog_for
ROOT=Path(__file__).resolve().parents[1]
WORK=ROOT/'.cache/unpvp-build'
WORK.mkdir(parents=True,exist_ok=True)
FORGE='1.8.9-11.15.1.2318-1.8.9'

def fetch(url,path,sha=None):
    path=Path(path)
    if path.exists() and (sha is None or hashlib.sha1(path.read_bytes()).hexdigest()==sha):return path
    req=urllib.request.Request(url,headers={'User-Agent':'Unbox-UI-development/1.0'})
    with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(req,timeout=30) as r:data=r.read()
    if sha and hashlib.sha1(data).hexdigest()!=sha:raise ValueError('Checksum mismatch: '+url)
    path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data);return path

def maven(url,name):
    checksum=fetch(url+'.sha1',WORK/(name+'.sha1')).read_text().strip().split()[0]
    if not re.fullmatch('[a-fA-F0-9]{40}',checksum):raise ValueError(url)
    return fetch(url,WORK/name,checksum)

def run(*args):subprocess.run(list(map(str,args)),cwd=ROOT,check=True)

def main():
    manifest=json.loads(fetch('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',WORK/'manifest.json').read_text())
    v=next(v for v in manifest['versions'] if v['id']=='1.8.9')
    meta=json.loads(fetch(v['url'],WORK/'minecraft.json',v['sha1']).read_text())
    client=fetch(meta['downloads']['client']['url'],WORK/'minecraft.jar',meta['downloads']['client']['sha1'])
    userdev=maven(f'https://maven.minecraftforge.net/net/minecraftforge/forge/{FORGE}/forge-{FORGE}-userdev.jar','forge-userdev.jar')
    mcp=maven('https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp/1.8.9/mcp-1.8.9-srg.zip','mcp.zip')
    stable=maven('https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_stable/22-1.8.9/mcp_stable-22-1.8.9.zip','names.zip')
    ss=maven('https://repo.maven.apache.org/maven2/net/md-5/SpecialSource/1.11.4/SpecialSource-1.11.4-shaded.jar','specialsource.jar')
    with zipfile.ZipFile(stable) as z:
        names={row['searge']:row['name'] for fn in ['fields.csv','methods.csv'] for row in csv.DictReader(io.StringIO(z.read(fn).decode()))}
    with zipfile.ZipFile(mcp) as z:lines=z.read('joined.srg').decode().splitlines()
    forward=[];reverse=[]
    for line in lines:
        p=line.split()
        if p[0]=='FD:':
            srg=p[2];named=srg.rsplit('/',1)[0]+'/'+names.get(srg.rsplit('/',1)[1],srg.rsplit('/',1)[1]);forward.append('FD: '+p[1]+' '+named);reverse.append('FD: '+named+' '+srg)
        elif p[0]=='MD:':
            srg=p[3];named=srg.rsplit('/',1)[0]+'/'+names.get(srg.rsplit('/',1)[1],srg.rsplit('/',1)[1]);forward.append('MD: '+p[1]+' '+p[2]+' '+named+' '+p[4]);reverse.append('MD: '+named+' '+p[4]+' '+srg+' '+p[4])
        else:forward.append(line)
    (WORK/'obf-named.srg').write_text('\n'.join(forward)+'\n');(WORK/'named-srg.srg').write_text('\n'.join(reverse)+'\n')
    with zipfile.ZipFile(userdev) as z:
        dev=json.loads(z.read('dev.json'));forge_classes=z.read('classes.jar')
    named=WORK/'minecraft-forge-named.jar'
    if not named.exists():
        entries={}
        for source in [client,io.BytesIO(forge_classes)]:
            with zipfile.ZipFile(source) as z:
                entries.update({n:z.read(n) for n in z.namelist() if not n.startswith('META-INF/') and not n.endswith('/')})
        with zipfile.ZipFile(WORK/'combined.jar','w') as z:
            for n,b in entries.items():z.writestr(n,b)
        run('java','-jar',ss,'-i',WORK/'combined.jar','-o',named,'-m',WORK/'obf-named.srg','--kill-lvt','--kill-generics','-q')
    libs=[]
    for lib in meta['libraries']:
        a=lib.get('downloads',{}).get('artifact')
        if a:libs.append(fetch(a['url'],WORK/'libraries'/a['path'],a['sha1']))
    for lib in dev['libraries']:
        g,a,v=lib['name'].split(':');rel=f'{g.replace(".","/")}/{a}/{v}/{a}-{v}.jar'
        target=WORK/'libraries'/rel
        if not target.exists():
            for base in ['https://maven.minecraftforge.net/','https://libraries.minecraft.net/','https://repo.maven.apache.org/maven2/']:
                try:fetch(base+rel,target);break
                except Exception:pass
            else:raise RuntimeError('Unavailable library '+rel)
        libs.append(target)
    run('python3',ROOT/'scripts/build-unpvp-network.py')
    libs.append(ROOT/'src-tauri/resources/unpvp-network.jar')
    classes=WORK/'classes'
    if classes.exists():shutil.rmtree(classes)
    classes.mkdir()
    cp=os.pathsep.join(map(str,[named,*libs]))
    run('javac','--release','8','-encoding','UTF-8','-proc:none','-cp',cp,'-d',classes,*sorted((ROOT/'client-mod/src/unpvp/java').rglob('*.java')),*sorted((ROOT/'ports/unpvp-network/src').rglob('*.java')))
    assets=classes/'assets/unbox';assets.mkdir(parents=True)
    for part in ['textures/ui','textures/font','ui']:
        shutil.copytree(ROOT/'client-mod/src/main/resources/assets/unbox'/part,assets/part,dirs_exist_ok=True)
    shutil.copytree(ROOT/'client-mod/src/unpvp/resources',classes,dirs_exist_ok=True)
    shutil.copytree(ROOT/'ports/pvp-independent/src/main/resources/META-INF/unbox-pvp',classes/'META-INF/unbox-pvp',dirs_exist_ok=True)
    shutil.copy2(ROOT/'client-mod/src/unpvp/java/dev/unbox/unpvp/ExtraHud.java',classes/'META-INF/unbox-pvp/ExtraHud.java')
    shutil.copy2(ROOT/'client-mod/src/unpvp/java/dev/unbox/unpvp/ModelOcclusion.java',classes/'META-INF/unbox-pvp/ModelOcclusion.java')
    # Export UI descriptors, never load modern gameplay implementations in the old game.
    options=json.loads((ROOT/'app/src/mod-options.json').read_text())
    source=ROOT/'client-mod/src/main/java/dev/unbox/client/ModOptions.java'
    if options['sourceSha256']!=hashlib.sha256(source.read_bytes()).hexdigest():raise RuntimeError('Run scripts/export-mod-options.py to refresh modern UI descriptors')
    catalog=[]
    for id,title,group,hint in re.findall(r'new Mod\("([^"]+)","([^"]+)","([^"]+)","([^"]+)"\)',source.read_text()):
        if id in {'freecam','particles'}:continue
        tabs=options['modules'][id]
        for tab,values in tabs.items():
            tabs[tab]=[o for o in values if o['key'] not in {'armor.offhand','crosshair.attack'}]
        catalog.append(dict(id=id,title=title,group=group,hint=hint,tabs=tabs))
    (assets/'ui/catalog.json').write_text(json.dumps(catalog_for(catalog),ensure_ascii=False))
    (classes/'mcmod.info').write_text(json.dumps([dict(modid='unpvp',name='Unbox unpvp',version='0.2.0',mcversion='1.8.9',description='Unbox vector interface, live HUDs, PvP controls and resource-pack browser.',clientSideOnly=True)]))
    (WORK/'manifest.mf').write_text('Manifest-Version: 1.0\nFMLCorePlugin: dev.unbox.unpvp.bootstrap.UiBootstrap\nFMLCorePluginContainsFMLMod: true\n\n')
    run('jar','cfm',WORK/'unpvp-named.jar',WORK/'manifest.mf','-C',classes,'.')
    output=ROOT/'src-tauri/resources/unpvp-forge-1.8.9.jar'
    # Include inheritance information while remapping inherited GuiScreen methods.
    run('java','-cp',os.pathsep.join(map(str,[ss,named,*libs])),'net.md_5.specialsource.SpecialSource','-i',WORK/'unpvp-named.jar','-o',output,'-m',WORK/'named-srg.srg','--live','-q')
    (WORK/'build-inputs.json').write_text(json.dumps({'modernOptions':options['sourceSha256'],'outputSha256':hashlib.sha256(output.read_bytes()).hexdigest(),'sources':[str(p.relative_to(ROOT)) for p in sorted((ROOT/'client-mod/src/unpvp/java').rglob('*.java'))]},indent=2))
    print('Built',output)
if __name__=='__main__':main()
