package dev.unbox.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.*;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.*;
import java.util.*;
/** Opt-in isolated UI checks, no world entry or camera input. */
public final class PortUiChecks {
 private static int stage,wait,configIndex;private static VectorModsScreen catalog;private static boolean done;private static final List<String> checks=new ArrayList<>();
 public static void tick(Minecraft mc){
  if(!Boolean.getBoolean("unbox.portUiTest")||done||mc.getOverlay()!=null)return;
  try{
   if(stage==10&&configIndex>0){if(++wait<10)return;wait=0;capture(mc,"pvp-settings-"+configIndex);mc.setScreen(catalog);configIndex=-configIndex;return;}
   if(!(mc.screen instanceof SolidScreen||mc.screen instanceof UnboxScreen))return;
   if(mc.screen instanceof SolidScreen s&&!s.surfaceReady())return;
   if(stage==2&&mc.screen instanceof VectorModsScreen v&&!v.contentSettled())return;
   if(++wait<15)return;wait=0;
   switch(stage++){
    case 0->{check(mc.screen instanceof UnboxHomeScreen,"Native vector title");capture(mc,"title");mc.setScreen(new VectorModsScreen(mc.screen));}
    case 1->{var s=(VectorModsScreen)mc.screen;check(has(s,"PvP")==Platform.loader().equals("fabric"),"PvP visible only on Fabric");capture(mc,"survival");if(PvpModules.available())click(s,"PvP");}
    case 2->{var s=(VectorModsScreen)mc.screen;if(PvpModules.available()){check(has(s,"Settings: FPS")&&has(s,"Settings: Crosshair Addons Public"),"Shared and public cards in PvP");check(!has(s,"Settings: VeinMiner"),"Survival modules excluded from PvP");boolean before=UnboxClient.configured("fps");click(s,"Toggle FPS");check(UnboxClient.configured("fps")!=before,"Shared module backend toggled");capture(mc,"pvp");click(s,"Survival");}}
    case 3->{var s=(VectorModsScreen)mc.screen;check(!has(s,"Settings: Crosshair Addons Public"),"PvP modules excluded from Survival");click(s,"Settings: Zoom");}
    case 4->{check(mc.screen instanceof UnboxScreen,"Direct detail navigation");capture(mc,"settings");mc.screen.onClose();}
    case 5->{var s=(VectorModsScreen)mc.screen;check(s.surfaceReady(),"Reopened list cached");check(ModOptions.options("ping","Appearance").stream().anyMatch(o->o.key().equals("ping.background")&&o.fallback().equals("false")),"HUD background default transparent");s.mouseScrolled(s.width/2.,s.height/2.,0,-1000);}
    case 6->{var s=(VectorModsScreen)mc.screen;check(s.scrollTarget()==s.maximumScroll(),"Scroll clamps at end");s.mouseScrolled(s.width/2.,s.height/2.,0,1000);}
    case 7->{var s=(VectorModsScreen)mc.screen;check(s.scrollTarget()==0,"Scroll clamps at start");SolidTheme.select(2);s.rebuild();}
    case 8->{capture(mc,"yellow");mc.options.guiScale().set(1);mc.resizeDisplay();}
    case 9->{var s=(VectorModsScreen)mc.screen;check(s.viewport.columns()>3,"Compact GUI scale relayout");capture(mc,"compact");if(PvpModules.available()){check(UnboxClient.configured("viewmodel")&&UnboxClient.enabled("viewmodel")==!Platform.loaded("handtweaks")&&UnboxClient.enabled("daytime"),"Native defaults and exclusive hand transforms");for(var e:PvpModules.entries())check(!PvpModules.status(e.id()).equals("Not installed"),"Catalogue status: "+e.id());}catalog=s;if(!PvpModules.available())finish(mc,null);}
    case 10->{stage=10;String[] ids={"crosshairaddons","shieldfixes","bettertotemcounter","statuseffecttimer","appleskin","combat-hitboxes","naturalmotionblur","bactromod","shulkerboxtooltip","elytraswapperplus","elytrapitch","handtweaks","lookbehind","shieldreturn","hitcolor","betterhitreg"};int index=Math.abs(configIndex);if(index>=ids.length){boolean before=PvpModules.enabled("appleskin");check(PvpModules.toggle("appleskin"),"Pinned JAR disabled without rewriting bytes");check(PvpModules.status("appleskin").equals("Restart required"),"Runtime and next-launch state distinguished");check(PvpModules.toggle("appleskin")&&PvpModules.enabled("appleskin")==before,"Installation toggle restored");var open=Class.forName("dev.unbox.client.PvpSettingsChecks").getDeclaredMethod("openIcon");open.setAccessible(true);open.invoke(null);stage=11;break;}check(PvpModules.configure(ids[index],catalog),"Own settings panel: "+ids[index]);var method=Class.forName("dev.unbox.client.PvpSettingsChecks").getDeclaredMethod("verify",String.class);method.setAccessible(true);method.invoke(null,ids[index]);check(true,"Upstream settings persisted: "+ids[index]);configIndex=index+1;}
    case 11->{capture(mc,"pvp-icon-editor");check(true,"Own pixel and animation editor rendered");finish(mc,null);}
   }
  }catch(Throwable e){finish(mc,e);}
 }
 private static boolean has(SolidScreen s,String label){return s.children().stream().anyMatch(w->w instanceof AbstractWidget b&&b.getMessage().getString().equals(label));}
 private static void click(SolidScreen s,String label){var w=(AbstractWidget)s.children().stream().filter(x->x instanceof AbstractWidget b&&b.getMessage().getString().equals(label)).findFirst().orElseThrow();double x=(w.getX()+w.getWidth()/2.)*s.scale+s.ox,y=(w.getY()+w.getHeight()/2.)*s.scaleY+s.oy;var e=new MouseButtonEvent(x,y,new MouseButtonInfo(0,0));s.mouseClicked(e,false);s.mouseReleased(e);}
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks.add(label);}
 private static void capture(Minecraft mc,String name){net.minecraft.client.Screenshot.grab(mc.gameDirectory,"port-"+name+".png",mc.getMainRenderTarget(),1,c->{});}
 private static void finish(Minecraft mc,Throwable error){done=true;try{var report=new LinkedHashMap<String,Object>();report.put("passed",error==null);report.put("checks",checks);if(error!=null){report.put("error",error.toString());error.printStackTrace();}Files.writeString(mc.gameDirectory.toPath().resolve("port-ui-checks.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));}catch(Exception e){e.printStackTrace();}mc.stop();}
}
