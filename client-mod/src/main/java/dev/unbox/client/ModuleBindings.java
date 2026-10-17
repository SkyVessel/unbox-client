package dev.unbox.client;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import java.util.*;
/** Same persisted settings whether changed in the launcher or inside the game. */
final class ModuleBindings {
 private static final Map<String,String> applied=new HashMap<>();
 static void apply(){var mc=Minecraft.getInstance();
  for(KeyMapping key:mc.options.keyMappings)if(key.getName().equals("key.unbox.menu")&&!key.saveString().equals("key.keyboard.right.shift")){key.setKey(InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT));KeyMapping.resetMapping();mc.options.save();}
  for(String id:List.of("zoom","freelook","minimap","freecam","veinminer")){
  String value=UnboxClient.CONFIG.getProperty(id+".key");
  KeyMapping binding=id.equals("zoom")?UnboxClient.zoomKey:id.equals("minimap")?UnboxClient.mapKey:id.equals("freecam")?FreecamController.key():id.equals("veinminer")?SurvivalHostMods.key():Arrays.stream(mc.options.keyMappings).filter(k->k.getName().equals("freelook.key.activate")).findFirst().orElse(null);if(binding==null)continue;
  if(value!=null&&value.matches("-?\\d+")){String migrated=KeyBindingNames.migrate(value,binding.saveString(),binding.getDefaultKey().getName());if(migrated==null)continue;value=migrated;UnboxClient.set(id+".key",value);UnboxClient.save();}
  if("key.keyboard.right.shift".equals(value)||binding.saveString().equals("key.keyboard.right.shift")){binding.setKey(InputConstants.UNKNOWN);KeyMapping.resetMapping();mc.options.save();value=InputConstants.UNKNOWN.getName();UnboxClient.set(id+".key",value);UnboxClient.save();}
  if(value==null||value.equals(applied.get(id))){String actual=binding.saveString();if(!actual.equals(value)){UnboxClient.set(id+".key",actual);UnboxClient.save();}applied.put(id,actual);continue;}
  try{var key=InputConstants.getKey(value);
   for(KeyMapping other:mc.options.keyMappings)if(other!=binding&&!key.equals(InputConstants.UNKNOWN)&&other.saveString().equals(key.getName()))other.setKey(InputConstants.UNKNOWN);
   binding.setKey(key);KeyMapping.resetMapping();mc.options.save();applied.put(id,value);
  }catch(IllegalArgumentException ignored){}
 }
 var cfg=Freelook.config;
 String mode=UnboxClient.CONFIG.getProperty("freelook.mode"),camera=UnboxClient.CONFIG.getProperty("freelook.camera");
 if(mode==null){mode=cfg.isToggle()?"Toggle":"Hold";UnboxClient.set("freelook.mode",mode);UnboxClient.save();}
 if(camera==null){camera=cfg.getPerspective().name();UnboxClient.set("freelook.camera",camera);UnboxClient.save();}
 if(mode!=null&&!mode.equals(applied.get("mode"))){cfg.setToggle(mode.equals("Toggle"));cfg.save();applied.put("mode",mode);}
 if(camera!=null&&!camera.equals(applied.get("camera"))){cfg.setPerspective(switch(camera){case "FIRST_PERSON"->1;case "THIRD_PERSON_FRONT"->2;default->3;});cfg.save();applied.put("camera",camera);}
 }
}
