package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** Regression checks against loaded third-party implementations, in disposable feature fixtures only. */
final class ModToggleChecks {
 private static void check(boolean value,String label,List<String> checks){if(!value)throw new IllegalStateException(label);checks.add(label);}
 private static Object field(Object o,String name)throws Exception{return o.getClass().getField(name).get(o);}
 private static Object instance(String type,String name)throws Exception{return Class.forName(type).getField(name).get(null);}
 private static void sync(){for(int i=0;i<5;i++)ClientIntegrations.sync();SurvivalClientMods.sync();}
 static List<String> run(Minecraft mc)throws Exception{
  List<String> checks=new ArrayList<>();Properties saved=new Properties();saved.putAll(UnboxClient.CONFIG);Screen previous=mc.screen;
  ItemStack stack=mc.player.getInventory().getItem(9).copy();
  try{
   for(String id:List.of("mousetweaks","inventory","items","bookshelf","effectbars","resources","spatial","variants"))UnboxClient.set(id,false);
   sync();
   Class<?> mouse=Class.forName("yalter.mousetweaks.Main");Object config=instance(mouse.getName(),"config");
   // Emulate the upstream default file independently of the Unbox switch.
   for(String f:List.of("rmbTweak","lmbTweakWithItem","lmbTweakWithoutItem","wheelTweak"))config.getClass().getField(f).setBoolean(config,true);
   config.getClass().getMethod("save").invoke(config);sync();
   var scroll=mouse.getMethod("onMouseScrolled",Screen.class,double.class,double.class,double.class);
   var update=mouse.getDeclaredMethod("updateScreen",Screen.class);update.setAccessible(true);
   var left=AbstractContainerScreen.class.getDeclaredField("leftPos");var top=AbstractContainerScreen.class.getDeclaredField("topPos");left.setAccessible(true);top.setAccessible(true);
   for(int attempt=0;attempt<2;attempt++){
    var screen=new InventoryScreen(mc.player);mc.setScreen(screen);mc.player.getInventory().setItem(9,new ItemStack(Items.STONE,8));
    var slot=screen.getMenu().slots.stream().filter(s->s.container==mc.player.getInventory()&&s.getContainerSlot()==9).findFirst().orElseThrow();
    double x=left.getInt(screen)+slot.x+8,y=top.getInt(screen)+slot.y+8;
    check(!(boolean)scroll.invoke(null,screen,x,y,-1d),"Disabled Mouse Tweaks does not consume first wheel input after opening inventory #"+attempt,checks);
    // Exercise the reload directly, including configured sub-options while enabled.
    update.invoke(null,screen);
    for(String f:List.of("rmbTweak","lmbTweakWithItem","lmbTweakWithoutItem","wheelTweak"))check(!(boolean)field(config,f),"Upstream screen reload preserves disabled "+f,checks);
    Class<?> button=Class.forName("yalter.mousetweaks.MouseButton");
    for(String entry:List.of("onMouseClicked","onMouseDrag","onMouseReleased"))check(!(boolean)mouse.getMethod(entry,Screen.class,double.class,double.class,button).invoke(null,screen,x,y,button.getField("RIGHT").get(null)),"Disabled Mouse Tweaks leaves "+entry+" to vanilla",checks);
    UnboxClient.set("mousetweaks",true);UnboxClient.set("mousetweaks.scroll",true);mc.player.getInventory().setItem(9,new ItemStack(Items.STONE,8));
    check((boolean)scroll.invoke(null,screen,x,y,-1d),"Re-enabled Mouse Tweaks handles real item scrolling in the same inventory",checks);
    UnboxClient.set("mousetweaks.scroll",false);UnboxClient.set("mousetweaks.rightDrag",false);UnboxClient.set("mousetweaks.leftDrag",false);UnboxClient.set("mousetweaks.collect",false);
    screen=new InventoryScreen(mc.player);mc.setScreen(screen);
    check(!(boolean)scroll.invoke(null,screen,x,y,-1d),"Disabled wheel sub-option survives another screen reload",checks);
    for(String f:List.of("rmbTweak","lmbTweakWithItem","lmbTweakWithoutItem","wheelTweak"))check(!(boolean)field(config,f),"Screen reload preserves disabled sub-option "+f,checks);
    UnboxClient.set("mousetweaks",false);
   }
   sync();
   check(!SurvivalClientMods.sortInventory(),"Disabled IPN refuses sorting dispatch",checks);
   Object features=instance("org.anti_ad.mc.ipnext.config.Features","INSTANCE");
   for(String key:List.of("ENABLE_SORTING_AND_MOVING","ITEM_SCROLLING","ENABLE_AUTO_REFILL","ENABLE_LOCK_SLOTS","ENABLE_PROFILES")){
    Object option=features.getClass().getMethod("get"+key).invoke(features);check(!(boolean)option.getClass().getMethod("getValue").invoke(option),"Disabled IPN clears "+key,checks);
   }
   for(var method:Class.forName("team.creative.itemphysiclite.ItemPhysicLite").getMethods())if(method.getName().equals("submit"))check(Boolean.FALSE.equals(method.invoke(null,new Object[method.getParameterCount()])),"Disabled ItemPhysic exits before custom rendering",checks);
   Class<?> renderer=Class.forName("net.anvian.chiseledbookshelfvisualizer.client.render.BookInfoRenderer");
   renderer.getMethod("hudRender",net.minecraft.client.gui.GuiGraphicsExtractor.class,Minecraft.class).invoke(null,null,null);
   checks.add("Disabled Bookshelf Visualizer exits before HUD rendering");
   Class<?> inspector=Class.forName("net.anvian.chiseledbookshelfvisualizer.client.raycast.BlockInspector");
   Object inspect=inspector.getConstructor(java.util.function.Consumer.class).newInstance((java.util.function.Consumer<Object>)packet->{throw new IllegalStateException("Disabled bookshelf sent a request");});
   inspector.getMethod("inspect",Minecraft.class).invoke(inspect,(Object)null);checks.add("Disabled Bookshelf Visualizer exits before raycast/network inspection");
   Object bars=instance("io.github.a5b84.statuseffectbars.StatusEffectBars","config");
   for(String layout:List.of("hudLayout","inventoryLayout"))check(!(boolean)field(field(bars,layout),"enabled"),"Disabled effect bars suppress "+layout,checks);
   Object companion=instance("dev.dediamondpro.resourcify.config.Config","Companion"),resources=companion.getClass().getMethod("getInstance").invoke(companion);
   for(String kind:List.of("ResourcePacks","DataPacks","ShaderPacks","Worlds","OpenLinkInResourcify"))check(!(boolean)resources.getClass().getMethod("get"+kind+(kind.equals("OpenLinkInResourcify")?"":"Enabled")).invoke(resources),"Disabled Resourcify suppresses "+kind,checks);
   check(!(boolean)Class.forName("org.tastytrash.spatialGUI.client.SpatialGUIClient").getMethod("isEnabled").invoke(null),"Disabled Spatial GUI suppresses its render activation",checks);
   UnboxClient.set("inventory",true);UnboxClient.set("inventory.scrolling",true);SurvivalClientMods.sync();Object scrolling=features.getClass().getMethod("getITEM_SCROLLING").invoke(features);
   check((boolean)scrolling.getClass().getMethod("getValue").invoke(scrolling),"IPN retains its separately enabled wheel feature when Mouse Tweaks is off",checks);
   UnboxClient.set("mousetweaks",true);UnboxClient.set("mousetweaks.scroll",true);SurvivalClientMods.sync();check(!(boolean)scrolling.getClass().getMethod("getValue").invoke(scrolling),"Mouse Tweaks wheel owns scrolling while enabled to avoid double handling",checks);
   return checks;
  }finally{mc.player.getInventory().setItem(9,stack);mc.setScreen(previous);UnboxClient.CONFIG.clear();UnboxClient.CONFIG.putAll(saved);sync();}
 }
}
