#!/usr/bin/env python3
"""Prepare pinned relocated network dependencies; do not load e4mc's entrypoint or bundled Mixin."""
from pathlib import Path
import hashlib,json,urllib.request,zipfile
ROOT=Path(__file__).resolve().parents[1]
port=ROOT/'ports/unpvp-network';meta=json.loads((port/'UPSTREAM.json').read_text());entry=meta['artifact']
cache=ROOT/'.cache/unpvp-social'/entry['filename'];cache.parent.mkdir(parents=True,exist_ok=True)
if not cache.exists() or hashlib.sha512(cache.read_bytes()).hexdigest()!=entry['hashes']['sha512']:
    with urllib.request.build_opener(urllib.request.ProxyHandler({})).open(entry['url'],timeout=60) as r:data=r.read()
    if hashlib.sha512(data).hexdigest()!=entry['hashes']['sha512']:raise ValueError('Network dependency hash mismatch')
    cache.write_bytes(data)
output=ROOT/'src-tauri/resources/unpvp-network.jar'
with zipfile.ZipFile(cache) as source,zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED) as target:
    for name in source.namelist():
        if name.startswith(('dev/xhyrom/e4mc/shadow/','META-INF/native/','META-INF/license/','META-INF/licenses/')) or ('LICENSE' in name and name.startswith('META-INF/')):
            target.writestr(name,source.read(name))
    for name in ['LICENSE','LICENSE.e4mc','UPSTREAM.json']:target.writestr('META-INF/unbox-network/'+name,(port/name).read_bytes())
    target.writestr('META-INF/unbox-network/UnboxRelay.java',(port/'src/dev/unbox/unpvp/UnboxRelay.java').read_bytes())
import subprocess
subprocess.run(['python3',str(ROOT/'scripts/prepare-unpvp-voxlink.py')],check=True)
print(output)
