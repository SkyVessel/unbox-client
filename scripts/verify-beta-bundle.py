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
for name in ['look-behind-1.21.11','pvp-independent','patcher-performance','unpvp-network','clean-test']:
 with zipfile.ZipFile(res/(name+'-sources.zip')) as z:assert any(n.endswith('.java') for n in z.namelist())
with zipfile.ZipFile(res/'unbox-clean-test-1.8.9.jar') as z:
 names=z.namelist()
 classes=[n for n in names if n.endswith('.class')]
 assert classes and all(n.startswith('dev/unbox/cleantest/') for n in classes)
 for n in classes:
  data=z.read(n)
  for forbidden in [b'java/net/',b'io/netty/',b'Feedback',b'SocialBridge',b'PvpSettings']:
   assert forbidden not in data,(n,forbidden)
 cfg=json.loads(z.read('mixins.unbox-clean-test.json'))
 assert set(cfg['client'])=={'FullbrightMixin','ModelOcclusionMixin','ModelCullingOwnerMixin','ModelNamesAccess'}
 assert 'dev.unbox.cleantest.bootstrap.CleanBootstrap' in z.read('META-INF/MANIFEST.MF').decode()
print('Verified stable/full engines, Clean Test isolation, pinned dependencies and source archives')
