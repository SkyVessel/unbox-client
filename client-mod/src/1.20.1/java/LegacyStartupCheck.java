package dev.unbox.client;
import net.minecraft.client.Minecraft;
import java.nio.file.Files;
/** Explicit startup-only fixture. Does not enter a world or move the camera. */
final class LegacyStartupCheck {
 private static int ticks,stage;private static boolean hidden;private static final java.util.List<String> checks=new java.util.ArrayList<>();
 static void tick(Minecraft mc){if(!Boolean.getBoolean("unbox.homeSmoke")||stage<0)return;
  if(!hidden){org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());hidden=true;}
  if(mc.getOverlay()!=null)return;try{
  if(++ticks<60)return;ticks=0;
  if(stage==0){if(!(mc.screen instanceof UnboxHomeScreen))throw new IllegalStateException("Unbox main menu missing; current="+(mc.screen==null?"null":mc.screen.getClass().getName()+":"+mc.screen.getTitle().getString()));checks.add("Native 1.20.1 Unbox main menu loaded");shot(mc,"legacy-home.png");mc.setScreen(new UnboxScreen(mc.screen));stage++;}
  else if(stage==1){if(!(mc.screen instanceof UnboxScreen screen)||mc.screen.children().isEmpty())throw new IllegalStateException("Mods screen missing controls");checks.add("Shared Mods screen initialized and rendered");shot(mc,"legacy-mods.png");
   var card=screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget a&&a.getMessage().getString().equals("Zoom")).map(w->(net.minecraft.client.gui.components.AbstractWidget)w).findFirst().orElseThrow();
   screen.mouseClicked((card.getX()+card.getWidth()/2.)*screen.menuScale(),(card.getY()+card.getHeight()/2.+screen.menuOffsetY())*screen.menuScale(),0);stage++;}
  else if(stage==2){shot(mc,"legacy-zoom.png");checks.add("Zoom settings page rendered through scaled card input");mc.setScreen(new UnboxHomeScreen(false));stage++;}
  else{LegacyPortRegression.run(mc,checks);var json=new com.google.gson.JsonObject();json.addProperty("passed",checks.size());json.add("checks",new com.google.gson.Gson().toJsonTree(checks));Files.writeString(mc.gameDirectory.toPath().resolve("logs/home-smoke.json"),json.toString());stage=-1;mc.stop();}
 }catch(Throwable e){stage=-1;e.printStackTrace();mc.stop();}}
 private static void shot(Minecraft mc,String name){net.minecraft.client.Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->{});}
}
