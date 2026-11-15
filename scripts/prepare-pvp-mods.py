#!/usr/bin/env python3
"""Install the pinned 1.21.11 Fabric PvP set; preserve existing config and disabled JARs."""
import argparse,hashlib,json,pathlib,urllib.request,zipfile,shutil
ROOT=pathlib.Path(__file__).resolve().parents[1]
def prepare(game):
    lock=json.loads((ROOT/'client-mod/pvp-lock-1.21.11-fabric.json').read_text())
    mods=game/'mods';mods.mkdir(parents=True,exist_ok=True)
    cache=ROOT/'.cache/pvp-1.21.11/jars';cache.mkdir(parents=True,exist_ok=True)
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    installed={}
    for jar in mods.glob('*.jar'):
        with zipfile.ZipFile(jar) as z:
            if 'fabric.mod.json' in z.namelist():installed[json.loads(z.read('fabric.mod.json'),strict=False)['id']]=jar
    for e in lock['mods']:
        # Reuse the already verified core dependencies. Never install duplicate mod IDs.
        if not e['primary'] and e['id'] in installed:continue
        filename=e['filename'];assert pathlib.Path(filename).name==filename
        active=mods/filename;disabled=mods/(filename+'.disabled')
        if e['id'] in installed and installed[e['id']].name!=filename:raise ValueError('Existing different version preserved: '+e['id'])
        dest=active if active.exists() else disabled if disabled.exists() else active if e['defaultEnabled'] else disabled
        source=dest if dest.exists() else ROOT/e['localArtifact'] if 'localArtifact' in e else cache/filename
        if not source.exists():source.write_bytes(opener.open(urllib.request.Request(e['url'],headers={'User-Agent':'Unbox-local-development'}),timeout=90).read())
        if hashlib.sha512(source.read_bytes()).hexdigest()!=e['sha512']:raise ValueError('Checksum mismatch; existing file preserved: '+str(source))
        if not dest.exists():shutil.copy2(source,dest)
    config=game/'config';config.mkdir(exist_ok=True)
    # These defaults are only seeded on new files. User configuration is never overwritten.
    hitreg=config/'hitreg.properties'
    if not hitreg.exists():hitreg.write_text('renderHitbox=false\nrenderCross=false\nRenderServerHitbox=false\nRenderApproachHitbox=false\nRenderYourReach=false\nRenderTheirReach=false\nRenderYourJump=false\nRenderTheirJump=false\nPerfectHitColor=false\nJumpResetColor=false\n')
    # BactroMod 3.5 has no small-totem option; the selected counter owns this effect.
    totem=config/'bettertotemcounter.json'
    if not totem.exists():totem.write_text(json.dumps({'popAnimationScale':0.5},indent=2)+'\n')
    return lock
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('game_dir',type=pathlib.Path);args=p.parse_args();prepare(args.game_dir.resolve())
