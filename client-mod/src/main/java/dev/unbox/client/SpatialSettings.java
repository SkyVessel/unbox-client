package dev.unbox.client;

import java.util.*;

/** Two-way settings bridge; upstream advanced settings remain owned by Spatial GUI. */
final class SpatialSettings {
 private static final Map<String,String> applied=new HashMap<>();
 private record Setting(String key,String field,String fallback,int min,int max,boolean percent){}
 private static final List<Setting> SETTINGS=List.of(
  new Setting("spatial","enabled","false",0,0,false),
  new Setting("spatial.inventory","inventory","true",0,0,false),new Setting("spatial.containers","containers","true",0,0,false),
  new Setting("spatial.autoCamera","autoDetectCameraMode","true",0,0,false),
  new Setting("spatial.firstInventory","firstPersonModeInventory","false",0,0,false),new Setting("spatial.firstContainers","firstPersonModeContainers","false",0,0,false),
  new Setting("spatial.crosshair","useCrosshairForFirstPerson","false",0,0,false),
  new Setting("spatial.disableFirst","disableFirstPerson","false",0,0,false),new Setting("spatial.disableThird","disableThirdPerson","false",0,0,false),
  new Setting("spatial.hideHud","hideHud","false",0,0,false),new Setting("spatial.hideHotbar","hideHotbar","false",0,0,false),
  new Setting("spatial.blur","blurWorldBackground","false",0,0,false),new Setting("spatial.opacity","screenAlpha","100",20,100,true),
  new Setting("spatial.transition","transitionDurationMs","200",0,600,false));
 static String open(net.minecraft.client.gui.screens.Screen parent){
  try{sync();Object handler=Class.forName("org.tastytrash.spatialGUI.client.SpatialGUIConfig").getField("HANDLER").get(null);
   Object gui=Class.forName("dev.isxander.yacl3.config.v2.api.ConfigClassHandler").getMethod("generateGui").invoke(handler);
   var screen=(net.minecraft.client.gui.screens.Screen)Class.forName("dev.isxander.yacl3.api.YetAnotherConfigLib").getMethod("generateScreen",net.minecraft.client.gui.screens.Screen.class).invoke(gui,parent);
   net.minecraft.client.Minecraft.getInstance().setScreen(screen);return "";
  }catch(ReflectiveOperationException|RuntimeException e){System.err.println("[Unbox] Spatial settings: "+e);return "Could not open Spatial GUI settings";}
 }
 static void sync()throws ReflectiveOperationException{
  Class<?> type=Class.forName("org.tastytrash.spatialGUI.client.SpatialGUIConfig");Object config=type.getMethod("get").invoke(null);
  boolean saveNative=false,saveUnbox=false;
  for(Setting s:SETTINGS){
   var field=type.getField(s.field);Object raw=field.get(config);String nativeValue=raw.toString();
   if(s.percent)nativeValue=""+Math.round(((Number)raw).intValue()*100f/255);
   String previous=applied.get(s.key),local=UnboxClient.CONFIG.getProperty(s.key);
   // First use inherits native options when Unbox has no saved value. Keep module opt-in.
   if(previous==null&&local==null&&!s.key.equals("spatial")||previous!=null&&Objects.equals(local,previous)&&!nativeValue.equals(previous)){
    local=nativeValue;UnboxClient.set(s.key,local);saveUnbox=true;
   }
   if(local==null)local=s.fallback;
   Object wanted;
   if(field.getType()==boolean.class){wanted=Boolean.parseBoolean(local);local=wanted.toString();}
   else{int n;try{n=Integer.parseInt(local);}catch(NumberFormatException e){n=Integer.parseInt(s.fallback);}n=Math.clamp(n,s.min,s.max);local=""+n;wanted=s.percent?Math.round(n*255f/100):n;}
   if(!raw.equals(wanted)&&!nativeValue.equals(local)){field.set(config,wanted);saveNative=true;}
   if(!local.equals(UnboxClient.CONFIG.getProperty(s.key))){UnboxClient.set(s.key,local);saveUnbox=true;}
   applied.put(s.key,local);
  }
  if(saveNative){Object handler=type.getField("HANDLER").get(null);Class.forName("dev.isxander.yacl3.config.v2.api.ConfigClassHandler").getMethod("save").invoke(handler);}
  if(saveUnbox)UnboxClient.save();
 }
}
