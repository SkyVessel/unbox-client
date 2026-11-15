#!/usr/bin/env python3
"""Verify beta engines, stable-byte isolation, pinned dependencies and source distributions."""
from pathlib import Path
import json,hashlib,zipfile
root=Path(__file__).resolve().parents[1];res=root/'src-tauri/resources'
manifest=json.loads((res/'beta-release-integrity.json').read_text())
for name,digest in manifest['stable'].items():assert hashlib.sha256((res/name).read_bytes()).hexdigest()==digest,('Stable engine changed',name)
for name,digest in manifest['beta'].items():
 assert hashlib.sha256((res/name).read_bytes()).hexdigest()==digest,name
 with zipfile.ZipFile(res/name) as z:
  assert not any('SilentAgent' in n or 'CustomerSync' in n for n in z.namelist()),name
for entry in json.loads((res/'beta-mods-1.21.11.json').read_text()):
 assert Path(entry['filename']).name==entry['filename']
 if 'resource' in entry:assert hashlib.sha512((res/entry['resource']).read_bytes()).hexdigest()==entry['sha512'],entry['filename']
 else:assert entry['url'].startswith('https://')
 assert len(entry['sha512'])==128
for loader in ['','neoforge-']:
 assert (res/f'unbox-client-{loader}26.3.jar').read_bytes()!=(res/f'unbox-client-{loader}26.3-beta.jar').read_bytes()
for name in ['look-behind-1.21.11','pvp-independent','patcher-performance','unpvp-network']:
 with zipfile.ZipFile(res/(name+'-sources.zip')) as z:assert any(n.endswith('.java') for n in z.namelist())
print('Verified old profile engines unchanged, four beta engines, pinned dependencies and sources')
