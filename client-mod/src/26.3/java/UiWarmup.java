package dev.unbox.client;
import net.minecraft.client.Minecraft;
/** Idle-title preparation, with the same bounded caches as visible pages. Never warms while playing. */
final class UiWarmup {
 private static String key="";private static SolidScreen pending;private static int stage;
 static void tick(Minecraft mc){
  if(mc.level!=null||!(mc.gui.screen() instanceof UnboxHomeScreen)){if(pending!=null){pending.removed();pending=null;}return;}
  var home=(UnboxHomeScreen)mc.gui.screen();if(!home.surfaceReady()||mc.gui.overlay()!=null)return;
  String next=mc.getWindow().getWidth()+":"+mc.getWindow().getHeight()+":"+mc.options.guiScale().get()+":"+SolidTheme.index();
  if(!next.equals(key)){if(pending!=null)pending.removed();pending=null;key=next;stage=0;}
  if(stage>=2)return;
  if(pending==null){pending=stage==0?new UnboxHomeScreen(true):new VectorModsScreen(home);pending.init(mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());}
  if(pending.prewarm()){pending.removed();pending=null;stage++;}
 }
}
