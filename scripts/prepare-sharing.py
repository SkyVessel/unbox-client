#!/usr/bin/env python3
"""Fetch the pinned, MIT-licensed e4mc compile dependency; verify before extraction."""
import hashlib,json,pathlib,urllib.request,zipfile
root=pathlib.Path(__file__).resolve().parents[1]
record=next(m for m in json.loads((root/'src-tauri/resources/performance-lock.json').read_text()) if m['slug']=='e4mc')
cache=root/'.cache';cache.mkdir(exist_ok=True)
jar=cache/'e4mc.jar'
def valid(data):return hashlib.sha512(data).hexdigest()==record['sha512']
if not jar.exists() or not valid(jar.read_bytes()):
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(urllib.request.Request(record['url'],headers={'User-Agent':'Unbox-client-development'}),timeout=60) as response:data=response.read(32*1024*1024)
    if not valid(data):raise SystemExit('e4mc checksum mismatch; no file installed')
    temporary=jar.with_suffix('.tmp');temporary.write_bytes(data);temporary.replace(jar)
deps=cache/'e4mc-deps';deps.mkdir(exist_ok=True)
with zipfile.ZipFile(jar) as archive:
    for name in archive.namelist():
        if name.startswith('META-INF/jars/kaleido-config-') and name.endswith('.jar'):
            (deps/pathlib.PurePosixPath(name).name).write_bytes(archive.read(name))
