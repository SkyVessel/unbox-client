package dev.unbox.client;
import net.minecraft.client.Minecraft;
import java.util.*;
final class OptimizationChecks {
 @SuppressWarnings("unchecked")
 static void verify(Minecraft mc,List<String> checks)throws Exception{
  TransferChecks.verify(checks);
  Properties saved=new Properties();saved.putAll(UnboxClient.CONFIG);
  String attack=mc.options.keyAttack.saveString(),forward=mc.options.keyUp.saveString(),zoom=UnboxClient.zoomKey.saveString();
  try{
   UnboxClient.set("zoom.key",forward);ModuleBindings.apply();
   if(!mc.options.keyUp.saveString().equals(forward)||!mc.options.keyAttack.saveString().equals(attack))throw new AssertionError("Applying a binding erased an unrelated control");
   if(!UnboxClient.zoomKey.saveString().equals(zoom)||ModuleBindings.reason("zoom").isEmpty())throw new AssertionError("Conflicting shortcut not rejected with explanation");
   checks.add("Launcher binding conflict preserves both controls and reports the conflict");
   UnboxClient.set("inventory",true);UnboxClient.set("inventory.refill",false);SurvivalClientMods.sync();
   Object features=Class.forName("org.anti_ad.mc.ipnext.config.Features").getField("INSTANCE").get(null);
   Object refill=features.getClass().getMethod("getENABLE_AUTO_REFILL").invoke(features);refill.getClass().getMethod("setValue",boolean.class).invoke(refill,true);
   for(int i=0;i<5;i++)SurvivalClientMods.sync();
   if(!UnboxClient.configured("inventory.refill"))throw new AssertionError("Native refill choice was overwritten");
   checks.add("IPN native refill changes propagate to Unbox instead of being overwritten");
   var errorsField=ClientIntegrations.class.getDeclaredField("errors");errorsField.setAccessible(true);var errors=(Map<String,String>)errorsField.get(null);
   errors.put("resources","fixture failure");UnboxClient.set("resources",true);
   var companion=Class.forName("dev.dediamondpro.resourcify.config.Config").getField("Companion").get(null);Object config=companion.getClass().getMethod("getInstance").invoke(companion);
   config.getClass().getMethod("setResourcePacksEnabled",boolean.class).invoke(config,true);
   if((boolean)config.getClass().getMethod("getResourcePacksEnabled").invoke(config))throw new AssertionError("Failed adapter allowed resource entry");
   ClientIntegrations.refresh("resources");ClientIntegrations.refresh("resources");
   if(!ClientIntegrations.available("resources")||!(boolean)config.getClass().getMethod("getResourcePacksEnabled").invoke(config))throw new AssertionError("Adapter failed to recover");
   checks.add("Integration failure gates native entry; successful retry restores availability");
  }finally{UnboxClient.CONFIG.clear();UnboxClient.CONFIG.putAll(saved);UnboxClient.zoomKey.setKey(com.mojang.blaze3d.platform.InputConstants.getKey(zoom));net.minecraft.client.KeyMapping.resetMapping();mc.options.save();SurvivalClientMods.sync();}
 }
}
