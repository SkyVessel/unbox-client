#!/usr/bin/env python3
"""Package already built native ports and corresponding source; update pinned hashes."""
import hashlib, json, shutil, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RES=ROOT/'src-tauri/resources'
COPIES={
 'puzzleslib-neoforge-26.1.jar':'ports/puzzleslib/NeoForge/build/libs/puzzleslib-v26.1.16-mc26.1.x+neoforge.jar',
 'iteminteractions-neoforge-26.1.jar':'ports/iteminteractions/NeoForge/build/libs/iteminteractions-v26.1.9-mc26.1.x+neoforge.jar',
 'easyshulkerboxes-neoforge-26.1.jar':'ports/easyshulkerboxes/NeoForge/build/libs/easyshulkerboxes-v26.1.7-mc26.1.x+neoforge.jar',
 'InventoryProfilesNext-neoforge-26.1.jar':'ports/inventory-profiles-next/platforms/neoforge-26.1.2/build/libs/InventoryProfilesNext-neoforge-26.1.2-2.3.8-SNAPSHOT-shaded.jar',
 'libIPN-neoforge-26.1.jar':'ports/libipn/libIPN/neoforge-26.1.2/build/libs/libIPN-neoforge-26.1.2-6.9.0-SNAPSHOT-shaded.jar',
 'effortlessbuilding-fabric-26.1.jar':'ports/effortless-building/fabric/build/libs/effortlessbuilding-fabric-26.1-4.3.jar',
 'effortlessbuilding-neoforge-26.1.jar':'ports/effortless-building/neoforge/build/libs/effortlessbuilding-neoforge-26.1-4.3.jar',
 'resourcify-neoforge-26.3.jar':'ports/resourcify/jars/Resourcify (26.3-neoforge)-1.8.7.jar',
 'veinminer-neoforge-26.1.jar':'ports/veinminer/veinminer/neoforge/build/libs/veinminer-neoforge-2.10.4.jar',
 'veinminer-client-neoforge-26.1.jar':'ports/veinminer/veinminer-client/neoforge/build/libs/veinminer-client-neoforge-2.10.4.jar',
}
EXCLUDE={'.git','.gradle','.kotlin','build','jars','logs','.idea','node_modules','__pycache__'}
def main():
 for name,source in COPIES.items():
  path=ROOT/source
  if not path.is_file():raise SystemExit('Build the port first: '+source)
  shutil.copy2(path,RES/name)
  if name.startswith("veinminer"):
   with zipfile.ZipFile(RES/name,"a",zipfile.ZIP_DEFLATED) as jar:
    jar.write(ROOT/"ports/veinminer/COPYING","META-INF/UNBOX-PORT-COPYING")
 # Include original and modified sources, wrappers, copyright and license texts.
 for port in sorted((ROOT/'ports').iterdir()):
  if not port.is_dir():continue
  with zipfile.ZipFile(RES/(port.name+'-sources.zip'),'w',zipfile.ZIP_DEFLATED) as z:
   for path in sorted(port.rglob('*')):
    rel=path.relative_to(port)
    if path.is_file() and not any(part in EXCLUDE for part in rel.parts) and not path.name.startswith('.env') and path.name not in ('.DS_Store',):z.write(path,path.relative_to(ROOT))
   for script in ('package-client-ports.py','build-status-effect-bars.py','build-colour-scroller.py'):
    z.write(ROOT/'scripts'/script,'scripts/'+script)
 lock=RES/'survival-mods-lock.json';data=json.loads(lock.read_text())
 for target in data['targets']:
  for entry in target['mods']:
   if 'resource' not in entry:continue
   content=(RES/entry['resource']).read_bytes()
   entry.update(size=len(content),sha1=hashlib.sha1(content).hexdigest(),sha512=hashlib.sha512(content).hexdigest())
 lock.write_text(json.dumps(data,indent=2)+'\n')
 print('Native port binaries, corresponding sources and lock hashes packaged')
if __name__=='__main__':main()
