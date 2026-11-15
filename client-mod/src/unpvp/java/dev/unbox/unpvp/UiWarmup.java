package dev.unbox.unpvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
/** Prepare the two common menus while idle; snapshots never allocate GPU textures. */
final class UiWarmup {
 private static String signature="";private static int stage;private static SolidScreen pending;
 static void tick(){Minecraft mc=Minecraft.getMinecraft();if(Boolean.getBoolean("unpvp.benchmark"))return;
  if(mc.theWorld==null&&!(mc.currentScreen instanceof SolidScreen&&((SolidScreen)mc.currentScreen).surfaceReady()))return;
  String next=mc.displayWidth+":"+mc.displayHeight+":"+mc.gameSettings.guiScale+":"+SolidTheme.index();
  if(!next.equals(signature)){SolidTexture.clearIdle();SolidText.clear();SolidGeometry.clear();if(pending!=null)pending.stopWarm();pending=null;stage=0;signature=next;}
  if(stage==2){ScaledResolution r=new ScaledResolution(mc);ModsScreen sample=new ModsScreen(null,"fps");sample.prepare(mc,r.getScaledWidth(),r.getScaledHeight());java.util.Set<String> labels=new java.util.LinkedHashSet<>(java.util.Arrays.asList("Back","Enabled","Disabled","General","Toggle module","Activation key","NONE","Search settings...","Reset settings","LIVE PREVIEW","ABOUT","Saved automatically   ·   Click a number to type","Click preview to edit HUD layout","Personal settings apply immediately.","Your changes are saved automatically."));for(UiState.Module m:UiState.MODULES){labels.add(m.title);labels.add(m.title.toUpperCase(java.util.Locale.ROOT));labels.add(m.hint);for(java.util.Map.Entry<String,java.util.List<UiState.Option>> tab:m.tabs.entrySet()){labels.add(tab.getKey());for(UiState.Option o:tab.getValue()){labels.add(o.label);labels.add(o.value());java.util.Collections.addAll(labels,o.choices);}}}SolidText.prepare(labels,r.getScaleFactor()*sample.scale);stage++;}if(stage>=2)return;if(pending==null){ScaledResolution r=new ScaledResolution(mc);pending=stage==0?new HomeScreen(true):new VectorModsScreen(null);pending.prepare(mc,r.getScaledWidth(),r.getScaledHeight());}
  if(pending.warm()){pending.stopWarm();pending=null;stage++;}
 }
}
