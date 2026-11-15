package dev.unbox.client;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
/** Opt-in menu-only integration checks, using the isolated test profile. */
final class PvpSettingsChecks {
 static void verify(String id) throws Exception {
  if(!Boolean.getBoolean("unbox.portUiTest"))throw new IllegalStateException("Test fixture only");
  if(!(Minecraft.getInstance().screen instanceof UnboxScreen))throw new AssertionError("Foreign settings screen: "+id);
  if(PvpSettings.notice(id).startsWith("Settings unavailable"))throw new AssertionError(PvpSettings.notice(id));
  var options=PvpSettings.tabs(id).stream().flatMap(t->PvpSettings.options(id,t).stream()).toList();
  if(options.isEmpty()&&!id.equals("statuseffecttimer"))throw new AssertionError("No controls: "+id);
  var option=options.stream().filter(o->o.kind().equals("toggle")).findFirst().orElse(null);
  if(option!=null){
   String before=ModOptions.value(option),next=""+!Boolean.parseBoolean(before);Path file=file(id);byte[] original=Files.exists(file)?Files.readAllBytes(file):null;
   var screen=(UnboxScreen)Minecraft.getInstance().screen;
   var widget=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.AbstractWidget a&&a.getMessage().getString().equals(option.label())).findFirst().orElseThrow();
   var mouse=new net.minecraft.client.input.MouseButtonEvent((widget.getX()+widget.getWidth()/2.)*screen.menuScale(),(widget.getY()+widget.getHeight()/2.)*screen.menuScale(),new net.minecraft.client.input.MouseButtonInfo(0,0));
   screen.mouseClicked(mouse,false);screen.mouseReleased(mouse);
   if(!ModOptions.value(option).equals(next)||PvpSettings.notice(id).startsWith("Could not"))throw new AssertionError("Write failed: "+id+" "+PvpSettings.notice(id));
   if(!Files.exists(file)||(original!=null&&Arrays.equals(original,Files.readAllBytes(file))))throw new AssertionError("Upstream file unchanged: "+id);
   UnboxClient.set(option.key(),before);UnboxClient.save();
   if(!ModOptions.value(option).equals(before))throw new AssertionError("Restore failed: "+id);
  }
  for(String kind:List.of("slider","decimal","color","choice","text","keybind")){
   var o=options.stream().filter(x->x.kind().equals(kind)).findFirst().orElse(null);if(o==null)continue;String before=ModOptions.value(o),next=switch(kind){case "slider"->""+(Integer.parseInt(before)==o.max()?o.min():Integer.parseInt(before)+1);case "decimal"->"0.75";case "color"->before.equals("#123456")?"#654321":"#123456";case "choice"->Arrays.stream(o.choices()).filter(v->!v.equals(before)).findFirst().orElse(before);case "keybind"->"key.keyboard.f8";default->before;};
   UnboxClient.set(o.key(),next);UnboxClient.save();if(!ModOptions.value(o).equals(next)||PvpSettings.notice(id).startsWith("Could not"))throw new AssertionError(id+" "+kind+": "+ModOptions.value(o)+" != "+next+" "+PvpSettings.notice(id));UnboxClient.set(o.key(),before);UnboxClient.save();
  }
  var keyOption=options.stream().filter(x->x.kind().equals("keybind")).findFirst().orElse(null);
  if(keyOption!=null){var screen=(UnboxScreen)Minecraft.getInstance().screen;click(screen,"Keys");String before=ModOptions.value(keyOption);String label=com.mojang.blaze3d.platform.InputConstants.getKey(before).getDisplayName().getString();click(screen,label);screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0));if(Minecraft.getInstance().screen!=screen||!ModOptions.value(keyOption).equals(before))throw new AssertionError("Cancel keybind capture");click(screen,label);screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_F13,0,0));if(!ModOptions.value(keyOption).equals("key.keyboard.f13"))throw new AssertionError("Own keybind capture: "+id);UnboxClient.set(keyOption.key(),before);UnboxClient.save();click(screen,"General");}
  if(id.equals("crosshairaddons")){
   var o=options.stream().filter(x->x.kind().equals("icon")).findFirst().orElseThrow();Object animation=PvpSettings.icon(o.key()),grid=PvpSettings.call(animation,"getFrame",0);int before=(int)PvpSettings.call(grid,"getColor",0,0);PvpSettings.call(grid,"setColor",0,0,0xff123456);PvpSettings.iconChanged(o.key());PvpSettings.flush();if((int)PvpSettings.call(grid,"getColor",0,0)!=0xff123456)throw new AssertionError("Icon edit");PvpSettings.call(grid,"setColor",0,0,before);PvpSettings.iconChanged(o.key());PvpSettings.flush();
  }
  System.out.println("[unbox-pvp-settings] "+id+" controls="+options.size()+" upstream save/restore passed");
 }
 private static void click(UnboxScreen screen,String label){var w=(net.minecraft.client.gui.components.AbstractWidget)screen.children().stream().filter(a->a instanceof net.minecraft.client.gui.components.AbstractWidget b&&b.getMessage().getString().equals(label)).findFirst().orElseThrow();var e=new net.minecraft.client.input.MouseButtonEvent((w.getX()+w.getWidth()/2.)*screen.menuScale(),(w.getY()+w.getHeight()/2.)*screen.menuScale(),new net.minecraft.client.input.MouseButtonInfo(0,0));screen.mouseClicked(e,false);screen.mouseReleased(e);}
 static void openIcon(){var o=PvpSettings.options("crosshairaddons","Appearance").stream().filter(x->x.kind().equals("icon")).findFirst().orElseThrow();PvpSettings.openIcon(o.key(),Minecraft.getInstance().screen);if(!(Minecraft.getInstance().screen instanceof PvpIconScreen))throw new AssertionError("Own icon editor");}
 private static Path file(String id){String name=switch(id){case "combat-hitboxes"->"combat-hitboxes.json";case "appleskin","shulkerboxtooltip"->id+".json5";case "handtweaks"->"handtweaks.json";case "elytrapitch"->"elytrapitch.toml";case "betterhitreg"->"hitreg.properties";default->id+".json";};return Platform.gameDir().resolve("config").resolve(name);}
}
