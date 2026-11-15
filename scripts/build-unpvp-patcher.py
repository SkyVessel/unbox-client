#!/usr/bin/env python3
"""Build the attributed Patcher subset without Essential or OneConfig.
Run build-unpvp.py and prepare-unpvp-performance.py first to obtain the upstream toolchain.
"""
from pathlib import Path
import json, os, shutil, subprocess
ROOT=Path(__file__).resolve().parents[1]
base=ROOT/'.cache/unpvp-build';work=ROOT/'.cache/unpvp-performance/patcher-build';work.mkdir(parents=True,exist_ok=True)
src=ROOT/'ports/patcher-performance';classes=work/'classes'
if classes.exists():shutil.rmtree(classes)
classes.mkdir()
deps=sorted((base/'libraries').rglob('*.jar'))
mix=next((ROOT/'.cache/unpvp-performance/jars').glob('RenderLib-*.jar'))
named=base/'minecraft-forge-named.jar';cp=os.pathsep.join(map(str,[named,mix,*deps]))
shutil.copytree(src/'src/main/resources',classes,dirs_exist_ok=True)
subprocess.run(['javac','--release','8','-encoding','UTF-8','-cp',cp,'-processorpath',cp,'-processor','org.spongepowered.tools.obfuscation.MixinObfuscationProcessorInjection,org.spongepowered.tools.obfuscation.MixinObfuscationProcessorTargets','-AreobfSrgFile='+str(base/'named-srg.srg'),'-AoutRefMapFile='+str(classes/'mixins.unbox-patcher.refmap.json'),'-AoutSrgFile='+str(work/'mixins.srg'),'-d',str(classes),*map(str,sorted((src/'src/main/java').rglob('*.java')))],check=True)
shutil.copy2(src/'LICENSE',classes/'LICENSE-Patcher.txt');shutil.copy2(src/'UPSTREAM.json',classes/'Patcher-UPSTREAM.json')
manifest=work/'manifest.mf';manifest.write_text('Manifest-Version: 1.0\nTweakClass: org.spongepowered.asm.launch.MixinTweaker\nMixinConfigs: mixins.unbox-patcher.json\nTweakOrder: 0\nForceLoadAsMod: true\n\n')
subprocess.run(['jar','cfm',str(work/'named.jar'),str(manifest),'-C',str(classes),'.'],check=True)
mapping=work/'combined.srg';mapping.write_text((base/'named-srg.srg').read_text()+(work/'mixins.srg').read_text())
output=ROOT/'src-tauri/resources/unpvp-patcher-performance.jar'
subprocess.run(['java','-cp',os.pathsep.join([str(base/'specialsource.jar'),cp]),'net.md_5.specialsource.SpecialSource','-i',str(work/'named.jar'),'-o',str(output),'-m',str(mapping),'--live','-q'],check=True)
print(output)
