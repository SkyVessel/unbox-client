#!/usr/bin/env python3
"""Offline release preflight: six native engines, bundled ports, hashes and sources."""
from pathlib import Path
import hashlib,json,zipfile,tomllib
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src-tauri/resources'
lock=json.loads((RES/'survival-mods-lock.json').read_text())
expected={(loader,version) for loader in ('fabric','neoforge') for version in ('26.1','26.1.2','26.3')}
assert {(t['loader'],t['minecraft']) for t in lock['targets']}==expected
for target in lock['targets']:
 loader,version=target['loader'],target['minecraft'];ids={i for m in target['mods'] for i in m['modIds']}
 required={'inventoryprofilesnext','libipn','veinminer','veinminer_client','itemphysiclite','fallingtree','mousetweaks','easyshulkerboxes','chiseledbookshelfvisualizer','resourcify','effortlessbuilding','colourscroller', 'status-effect-bars' if loader=='fabric' else 'status_effect_bars','spatial-gui' if loader=='fabric' else 'spatial_gui'}
 assert required<=ids,(loader,version,required-ids)
 assert not target.get('unavailableFeatures'),(loader,version)
 for mod in target['mods']:
  if 'resource' not in mod:continue
  name=mod['resource'];assert Path(name).name==name and name.endswith('.jar')
  blob=(RES/name).read_bytes()
  assert len(blob)==mod['size'] and all(hashlib.new(a,blob).hexdigest()==mod[a] for a in ('sha1','sha512')),name
  with zipfile.ZipFile(RES/name) as z:
   if loader=='fabric': actual={json.loads(z.read('fabric.mod.json'))['id']}
   else: actual={m['modId'] for m in tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())['mods']}
   assert set(mod['modIds'])<=actual,name
 suffix='' if version=='26.1' else '-'+version
 engine=RES/('unbox-client'+('-neoforge' if loader=='neoforge' else '')+suffix+'.jar')
 with zipfile.ZipFile(engine) as z:
  assert 'dev/unbox/client/ClientIntegrations.class' in z.namelist(),engine
  if loader=='fabric':assert json.loads(z.read('fabric.mod.json'))['depends']['minecraft']==version
  else:
   meta=tomllib.loads(z.read('META-INF/neoforge.mods.toml').decode())
   assert any(d['modId']=='minecraft' and d['versionRange']=='['+version+']' for d in meta['dependencies']['unbox'])
for port in (ROOT/'ports').iterdir():
 if not port.is_dir():continue
 with zipfile.ZipFile(RES/(port.name+'-sources.zip')) as z:
  assert 'ports/'+port.name+'/UPSTREAM' in z.namelist()
  assert 'ports/'+port.name+'/PORT.md' in z.namelist()
  assert any(Path(n).name.lower().startswith(('license','copying')) for n in z.namelist())
  assert not any(p.startswith('.env') or p in ('secrets','.git','build','.gradle') for n in z.namelist() for p in Path(n).parts)
assert (RES/'third-party-notices.html').is_file()
print('Verified six native feature sets, engines, bundled hashes and corresponding source archives')
