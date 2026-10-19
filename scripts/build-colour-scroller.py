#!/usr/bin/env python3
"""Compile the MIT ColourScroller shape engine independently for each native target."""
from pathlib import Path
import os,subprocess,shutil,json,zipfile
ROOT=Path(__file__).resolve().parents[1]
PORT=ROOT/'ports/colour-scroller'
for version in ('26.1','26.1.2','26.3'):
    build=ROOT/f'.cache/ports/colour-scroller-{version}'
    shutil.rmtree(build,ignore_errors=True);build.mkdir(parents=True)
    libs=[ROOT/f'.cache/client-{version}.jar',*list((ROOT/'.cache/libs').glob('*.jar')),*list((ROOT/f'.cache/runtime-{version}/libraries').rglob('*.jar'))]
    subprocess.run(['javac','--release','25','-proc:none','-cp',os.pathsep.join(map(str,libs)),'-d',str(build),*map(str,(PORT/'src').rglob('*.java'))],check=True)
    for loader in ('fabric','neoforge'):
        dest=ROOT/f'src-tauri/resources/colour-scroller-{loader}-{version}.jar'
        with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED) as z:
            if loader=='fabric':
                z.writestr('fabric.mod.json',json.dumps(dict(schemaVersion=1,id='colourscroller',version='1.0.0-unbox.1',name='ColourScroller (Unbox shape preset)',environment='client',license='MIT',depends={'minecraft':version})))
            else:
                z.writestr('META-INF/neoforge.mods.toml',f'modLoader="javafml"\nloaderVersion="[1,)"\nlicense="MIT"\n[[mods]]\nmodId="colourscroller"\nversion="1.0.0-unbox.1"\ndisplayName="ColourScroller (Unbox shape preset)"\n[[dependencies.colourscroller]]\nmodId="minecraft"\ntype="required"\nversionRange="[{version}]"\nside="CLIENT"\n')
            for p in build.rglob('*.class'):z.write(p,p.relative_to(build))
            for name in ('LICENSE','UPSTREAM','PORT.md'):z.write(PORT/name,name)
        print('Built',dest)
with zipfile.ZipFile(ROOT/'src-tauri/resources/colour-scroller-sources.zip','w',zipfile.ZIP_DEFLATED) as z:
    for p in PORT.rglob('*'):
        if p.is_file() and not any(part in {"build",".gradle",".git",".kotlin"} for part in p.relative_to(PORT).parts):z.write(p,p.relative_to(ROOT))
    z.write(__file__,'scripts/build-colour-scroller.py')
