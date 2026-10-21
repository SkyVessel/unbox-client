#!/usr/bin/env python3
import pathlib,subprocess,zipfile,struct
root=pathlib.Path(__file__).resolve().parents[1]
asm=next((root/'.cache/neoforge/runtime/libraries/org/ow2/asm/asm').rglob('asm-9.10.1.jar'))
classes=root/'.cache/silent-agent/classes';classes.mkdir(parents=True,exist_ok=True)
subprocess.run(['javac','-cp',str(asm),'-d',str(classes),str(root/'tests/silent/SilentGameAgent.java')],check=True)
def shade(data):
    # Relocate ASM so Fabric's duplicate-class guard and the game's ASM version remain independent.
    out=bytearray(data[:10]);offset=10;index=1;count=struct.unpack('>H',data[8:10])[0]
    while index<count:
        tag=data[offset];out.append(tag);offset+=1
        if tag==1:
            size=struct.unpack('>H',data[offset:offset+2])[0];offset+=2
            text=data[offset:offset+size].replace(b'org/objectweb/asm',b'unbox/silent/asm').replace(b'org.objectweb.asm',b'unbox.silent.asm');offset+=size
            out.extend(struct.pack('>H',len(text)));out.extend(text)
        else:
            size={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}[tag]
            out.extend(data[offset:offset+size]);offset+=size
            if tag in (5,6):index+=1
        index+=1
    out.extend(data[offset:]);return out
jar=root/'.cache/silent-agent/agent.jar'
with zipfile.ZipFile(jar,'w') as z:
 z.writestr('META-INF/MANIFEST.MF','Manifest-Version: 1.0\nPremain-Class: SilentGameAgent\n\n')
 for p in classes.rglob('*.class'):z.writestr(str(p.relative_to(classes)),shade(p.read_bytes()))
 with zipfile.ZipFile(asm) as dep:
  for name in dep.namelist():
   if name.endswith('.class') and name!='module-info.class':z.writestr(name.replace('org/objectweb/asm','unbox/silent/asm'),shade(dep.read(name)))
print(jar)
