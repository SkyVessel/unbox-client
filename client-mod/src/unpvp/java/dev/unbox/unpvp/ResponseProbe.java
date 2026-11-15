package dev.unbox.unpvp;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;
/** Same real navigation sequence before/after the renderer changes; first drawable frame, not fade completion. */
final class ResponseProbe {
 private static int step,settle,frames,beforeUploads;private static long began;private static String action;
 static void frame(){Minecraft mc=Minecraft.getMinecraft();try{
  if(!(mc.currentScreen instanceof UiScreen))return;
  if(mc.currentScreen instanceof SolidScreen&&!((SolidScreen)mc.currentScreen).surfaceReady()){if(began!=0)frames++;return;}
  if(began!=0){frames++;System.out.println("[unpvp-response] "+action+" ms="+(System.nanoTime()-began)/1000000.+" frames="+frames+" uploads="+(SolidTexture.uploads-beforeUploads));began=0;settle=0;}
  if(++settle<(step==0?180:20))return;
  if(step==24){System.out.println("[unpvp-response] PASS");step++;mc.shutdown();return;}if(step>24)return;
  int route=step%6;action=(step/6)+":"+new String[]{"quick","mods","detail","detail-tab","back-mods","back-quick"}[route];beforeUploads=SolidTexture.uploads;frames=0;began=System.nanoTime();
  if(route==0)mc.displayGuiScreen(new HomeScreen(true));
  else if(route==1)press("Client Mods");
  else if(route==2)press("Settings: FPS");
  else if(route==3){ModsScreen s=(ModsScreen)mc.currentScreen;String last="General";for(String t:UiState.module("fps").tabs.keySet())last=t;press(last);}
  else press("Back");
  step++;
 }catch(Throwable e){e.printStackTrace();System.out.println("[unpvp-response] FAILED");mc.shutdown();step=25;}}
 private static void press(String label){UiScreen s=(UiScreen)Minecraft.getMinecraft().currentScreen;for(UiScreen.Control c:s.controls)if(c.label.equals(label)){c.key((char)0,Keyboard.KEY_RETURN);return;}throw new IllegalStateException("Missing control "+label);}
}
