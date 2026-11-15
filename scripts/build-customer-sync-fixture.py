#!/usr/bin/env python3
"""Replace only the opt-in smoke driver in a disposable copy; never the delivered JAR."""
from pathlib import Path
import subprocess,os,zipfile
r=Path(__file__).resolve().parents[1];c=r/'.cache/customer-sync';c.mkdir(parents=True,exist_ok=True);out=c/'classes-26.3';out.mkdir(exist_ok=True)
src=c/'PrivateWorldSmokeTest.java';src.write_text((r/'tests/customer-sync/PrivateWorldSmokeTest.java').read_text().replace('mc.screen','mc.gui.screen()').replace('mc.getOverlay()','mc.gui.overlay()').replace('false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.gamerules.GameRules(data.enabledFeatures()),data','new net.minecraft.world.level.LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL,false,false),true,data').replace('::createFlatWorldDimensions','::createNormalWorldDimensions').replace('org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER','com.mojang.blaze3d.platform.InputConstants.KEY_RETURN'))
cp=[r/'.cache/client-26.3.jar',r/'src-tauri/resources/unbox-client-26.3.jar',*(r/'.cache/runtime-26.3/libraries').rglob('*.jar')]
subprocess.run(['javac','--release','25','-proc:none','-cp',os.pathsep.join(map(str,cp)),'-d',str(out),str(src)],check=True)
with zipfile.ZipFile(r/'src-tauri/resources/unbox-client-26.3.jar') as z,zipfile.ZipFile(c/'unbox-test-26.3.jar','w',zipfile.ZIP_DEFLATED) as dest:
 for i in z.infolist():
  if not i.filename.startswith('dev/unbox/client/PrivateWorldSmokeTest'):dest.writestr(i,z.read(i))
 for cls in out.rglob('*.class'):dest.write(cls,cls.relative_to(out))
