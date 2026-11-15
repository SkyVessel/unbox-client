#!/usr/bin/env python3
"""Build only the two Clean Test features and retained display/model-culling adapters."""
from pathlib import Path
import argparse,os,shutil,subprocess,json,hashlib,zipfile
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser();p.add_argument('--cache',type=Path,default=root/'.cache');args=p.parse_args()
base=args.cache/'unpvp-build';work=args.cache/'clean-test-build';classes=work/'classes';work.mkdir(parents=True,exist_ok=True)
if classes.exists():shutil.rmtree(classes)
classes.mkdir();source=root/'client-mod/src/clean-test'
shutil.copytree(source/'resources',classes,dirs_exist_ok=True)
libs=sorted((base/'libraries').rglob('*.jar'));mix=next((args.cache/'unpvp-performance/jars').glob('RenderLib-*.jar'))
cp=os.pathsep.join(map(str,[base/'minecraft-forge-named.jar',mix,*libs]))
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',cp,'-processorpath',cp,'-processor','org.spongepowered.tools.obfuscation.MixinObfuscationProcessorInjection,org.spongepowered.tools.obfuscation.MixinObfuscationProcessorTargets','-AreobfSrgFile='+str(base/'named-srg.srg'),'-AoutRefMapFile='+str(classes/'mixins.unbox-clean-test.refmap.json'),'-AoutSrgFile='+str(work/'mixins.srg'),'-d',str(classes),*map(str,sorted((source/'java').rglob('*.java')))],check=True)
manifest=work/'manifest.mf';manifest.write_text('Manifest-Version: 1.0\nTweakClass: org.spongepowered.asm.launch.MixinTweaker\nMixinConfigs: mixins.unbox-clean-test.json\nTweakOrder: 0\nForceLoadAsMod: true\nFMLCorePlugin: dev.unbox.cleantest.bootstrap.CleanBootstrap\nFMLCorePluginContainsFMLMod: true\n\n')
subprocess.run(['jar','cfm',str(work/'named.jar'),str(manifest),'-C',str(classes),'.'],check=True)
mapping=work/'combined.srg';mapping.write_text((base/'named-srg.srg').read_text()+(work/'mixins.srg').read_text())
res=root/'src-tauri/resources';output=res/'unbox-clean-test-1.8.9.jar'
subprocess.run(['java','-cp',os.pathsep.join([str(base/'specialsource.jar'),cp]),'net.md_5.specialsource.SpecialSource','-i',str(work/'named.jar'),'-o',str(output),'-m',str(mapping),'--live','-q'],check=True)
with zipfile.ZipFile(res/'clean-test-sources.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in sorted(source.rglob('*')):
  if f.is_file():z.write(f,str(f.relative_to(root)))
 z.write(__file__,'scripts/build-clean-test.py')
lock=res/'beta-release-integrity.json';v=json.loads(lock.read_text());v['beta'][output.name]=hashlib.sha256(output.read_bytes()).hexdigest();lock.write_text(json.dumps(v,indent=2)+'\n')
print(output)
