package dev.unbox.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Opt-in checks against loaded upstream code and the actual integrated server. */
final class FeatureSmokeTest {
 private static int ticks,stage,wait,browseWait;
 private static CompletableFuture<Void> pending;
 private static final List<String> checks=new ArrayList<>();
 private static HappyGhast ghast,calledGhast;
 private static double callStartDistance;
 private static double originalSpeed;
 private static boolean finished;
 private interface Work {void run() throws Exception;}
 private static void check(boolean ok,String label){if(!ok)throw new IllegalStateException(label);checks.add(label);}
 private static ServerPlayer player(Minecraft mc){return mc.getSingleplayerServer().getPlayerList().getPlayer(mc.getUser().getProfileId());}
 private static Object value(String type,String field)throws Exception{return Class.forName(type).getField(field).get(null);}
 private static Object field(Object obj,String name)throws Exception{return obj.getClass().getField(name).get(obj);}
 private static Object enumeration(String type,String name)throws Exception{for(Object e:Class.forName(type).getEnumConstants())if(((Enum<?>)e).name().equals(name))return e;throw new IllegalArgumentException(name);}
 private static void server(Minecraft mc,Work work){pending=new CompletableFuture<>();mc.getSingleplayerServer().execute(()->{try{work.run();pending.complete(null);}catch(Throwable e){pending.completeExceptionally(e);}});}
 static void tick(Minecraft mc){
  if(!Boolean.getBoolean("unbox.featureSmoke")||finished)return;
  if(Boolean.getBoolean("unbox.performanceSmoke")){PerformanceSmokeTest.tick(mc);return;}
  mc.getFramerateLimitTracker().onInputReceived();
  if(mc.player==null||mc.getSingleplayerServer()==null)return;
  try{
   if(++ticks>1800)throw new IllegalStateException("Feature check timed out at "+stage);
   if(ticks<100||wait-->0)return;
   if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
   switch(stage++){
    case 0->{checks.addAll(ModToggleChecks.run(mc));if(Boolean.getBoolean("unbox.toggleOnly")){finish(mc,null);return;}for(String id:ClientIntegrations.IDS){check(ClientIntegrations.available(id),id+" native dependency loaded");UnboxClient.set(id,true);}UnboxClient.set("ghast",true);UnboxClient.set("ghast.speed",200);wait=15;}
    case 1->{
     spatial(mc);
     for(String id:ClientIntegrations.IDS)check(ClientIntegrations.available(id),id+" settings adapter succeeds");
     check((boolean)field(value("yalter.mousetweaks.Main","config"),"wheelTweak"),"Mouse Tweaks enables wheel input");
     check((boolean)field(field(value("io.github.a5b84.statuseffectbars.StatusEffectBars","config"),"hudLayout"),"enabled"),"Status Effect Bars HUD enabled");
     check(ClientIntegrations.key("building")!=null&&ClientIntegrations.key("variants")!=null,"Building and shape cycling expose native key mappings");
     var parent=mc.screen;check(ClientIntegrations.openResources(parent).isEmpty(),"Resource browser opens");check(mc.screen instanceof net.minecraft.client.gui.screens.packs.PackSelectionScreen,"Resource browser uses native pack screen");mc.setScreen(null);
     server(mc,()->{player(mc).setGameMode(GameType.CREATIVE);player(mc).addEffect(new MobEffectInstance(MobEffects.SPEED,400,0));player(mc).addEffect(new MobEffectInstance(MobEffects.HASTE,800,0));});wait=20;
    }
    case 2->{
     check(WorldFeatures.buildingClient(),"Creative host may use building tools");
     mc.player.getInventory().setItem(mc.player.getInventory().getSelectedSlot(),new ItemStack(Items.OAK_PLANKS,64));
     CreativeVariants.key.setDown(true);check(CreativeVariants.scroll(mc.getWindow().handle(),-1),"Shape shortcut handles creative scroll");check(mc.player.getMainHandItem().is(Items.OAK_PLANKS),"Scrolling previews shapes without changing the held item");wait=15;
    }
    case 3->{check(CreativeVariants.selecting(),"Shape selector stays visible while shortcut is held");Screenshot.grab(mc.gameDirectory,"block-shapes-selector.png",mc.getMainRenderTarget(),1,c->{});CreativeVariants.key.setDown(false);CreativeVariants.tick();wait=15;}
    case 4->{server(mc,()->{
     check(player(mc).getMainHandItem().is(Items.OAK_STAIRS),"Shape change reaches server as oak stairs");
     build(mc,true);shulker(mc,true);
     ghast=(HappyGhast)BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("happy_ghast")).create(player(mc).level(),EntitySpawnReason.COMMAND);
     ghast.setPos(player(mc).getX(),player(mc).getY()+8,player(mc).getZ());ghast.setItemSlot(EquipmentSlot.BODY,new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_harness"))));ghast.setNoAi(true);
     player(mc).level().addFreshEntity(ghast);player(mc).startRiding(ghast);
     originalSpeed=ghast.getAttribute(Attributes.FLYING_SPEED).getValue();
    });wait=80;}
    case 5->{server(mc,()->{check(ghast.getControllingPassenger()==player(mc),"Ghast rider controls the mount");check(Math.abs(ghast.getAttribute(Attributes.FLYING_SPEED).getValue()-originalSpeed*2)<.0001,"Host speed setting doubles ridden flying speed");});UnboxClient.set("ghast",false);wait=30;}
    case 6->{server(mc,()->{check(Math.abs(ghast.getAttribute(Attributes.FLYING_SPEED).getValue()-originalSpeed)<.0001,"Disabling ghast speed restores original attribute");player(mc).stopRiding();ghast.discard();player(mc).setGameMode(GameType.SURVIVAL);build(mc,false);});wait=15;}
    case 7->{check(!WorldFeatures.buildingClient(),"Survival blocks client building actions");CreativeVariants.key.setDown(true);check(!CreativeVariants.scroll(mc.getWindow().handle(),-1),"Survival blocks shape shortcut");CreativeVariants.key.setDown(false);mc.setScreen(new InventoryScreen(mc.player));wait=30;}
    case 8->{Screenshot.grab(mc.gameDirectory,"new-features-inventory.png",mc.getMainRenderTarget(),1,c->{});mc.setScreen(null);UnboxClient.set("spatial.firstInventory",true);SpatialSettings.sync();mc.setScreen(new InventoryScreen(mc.player));wait=30;}
    case 9->{check((boolean)Class.forName("org.tastytrash.spatialGUI.client.SpatialGUIClient").getMethod("getEffectiveFirstPersonMode").invoke(null),"Spatial GUI actually renders the inventory in first-person mode");Screenshot.grab(mc.gameDirectory,"spatial-first-person.png",mc.getMainRenderTarget(),1,c->{});mc.setScreen(null);UnboxClient.set("mousetweaks",false);UnboxClient.set("effectbars",false);UnboxClient.set("spatial",false);UnboxClient.set("shulker",false);wait=10;}
    case 10->{check(!(boolean)field(value("yalter.mousetweaks.Main","config"),"wheelTweak"),"Mouse Tweaks disables wheel input");check(!(boolean)field(field(value("io.github.a5b84.statuseffectbars.StatusEffectBars","config"),"hudLayout"),"enabled"),"Status Effect Bars disables HUD");server(mc,()->shulker(mc,false));}
    case 11->{check(Files.readString(mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("unbox-features.properties")).contains("ghast=false"),"World feature changes persist in the world directory");joinCancellation(mc);manifestScope();check(EnvironmentManifest.scan(mc.gameDirectory.toPath()).manifest()!=null,"Complete installed mod set can be scanned for sharing");mc.setScreen(new UnboxScreen());wait=30;}
    case 12->{Screenshot.grab(mc.gameDirectory,"new-feature-cards.png",mc.getMainRenderTarget(),1,c->{});ClientIntegrations.openResources(null);var browse=mc.screen.children().stream().filter(c->c.getClass().getName().equals("dev.dediamondpro.resourcify.gui.injections.ImageButton")).map(c->(net.minecraft.client.gui.components.Button)c).findFirst().orElseThrow();browse.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0));check(mc.screen.getClass().getName().equals("dev.dediamondpro.resourcify.gui.browsepage.BrowseScreen"),"Native Resourcify browse button opens its full interface");wait=40;}
    case 13->{var hits=mc.screen.getClass().getDeclaredField("totalHits");hits.setAccessible(true);if(hits.getInt(mc.screen)==0&&browseWait++<20){stage--;wait=20;break;}check(hits.getInt(mc.screen)>0,"Resourcify retrieves actual online resource search results");var threads=Class.forName("dev.dediamondpro.resourcify.util.MultiThreading");var factory=threads.getDeclaredMethod("createThread",Runnable.class);factory.setAccessible(true);check(((Thread)factory.invoke(threads.getField("INSTANCE").get(null),(Runnable)()->{})).isDaemon(),"Resource workers do not delay game exit");Screenshot.grab(mc.gameDirectory,"new-resourcify-browser.png",mc.getMainRenderTarget(),1,c->{});mc.screen.getClass().getMethod("goBack").invoke(mc.screen);check(mc.screen instanceof net.minecraft.client.gui.screens.packs.PackSelectionScreen,"Resource browsing returns to the pack screen");mc.setScreen(null);UnboxClient.set("ghast",true);UnboxClient.set("ghast.wait",1);wait=10;}
    case 14->{check(HappyGhastRecall.key.isUnbound(),"Ghast call shortcut defaults to unbound");server(mc,()->{
     var p=player(mc);p.setGameMode(GameType.CREATIVE);p.teleportTo(p.getX(),160,p.getZ());p.getAbilities().flying=true;p.onUpdateAbilities();
     calledGhast=(HappyGhast)BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.withDefaultNamespace("happy_ghast")).create(p.level(),EntitySpawnReason.COMMAND);
     calledGhast.setPos(p.getX()+8,160,p.getZ());p.level().addFreshEntity(calledGhast);
     UnboxClient.set("ghast.range",99);HappyGhastRecall.receive(p);check(!HappyGhastRecall.called(calledGhast),"Server clamps call cube to 15 blocks even if configuration exceeds limit");
     calledGhast.setPos(p.getX()+6,160,p.getZ());callStartDistance=calledGhast.distanceToSqr(p);
    });wait=10;}
    case 15->{check(HappyGhastRecall.request().isEmpty(),"Ghast recall uses the registered client-to-server request");wait=25;}
    case 16->{server(mc,()->{check(calledGhast.distanceToSqr(player(mc))<callStartDistance,"Called ghast flies toward the player using native movement");});wait=230;}
    case 17->{server(mc,()->{
     check(!HappyGhastRecall.called(calledGhast)&&!calledGhast.isNoAi(),"Idle ghast call expires and normal AI remains enabled");
     UnboxClient.set("ghast.wait",4);calledGhast.setPos(player(mc).getX()+3,player(mc).getY(),player(mc).getZ());HappyGhastRecall.receive(player(mc));check(HappyGhastRecall.called(calledGhast),"Ghast can be called again after expiry");
    });wait=40;}
    case 18->{server(mc,()->check(HappyGhastRecall.called(calledGhast),"Arrived ghast waits for the configured four seconds"));wait=60;}
    case 19->{server(mc,()->{check(!HappyGhastRecall.called(calledGhast),"Arrived ghast resumes normal behavior after four seconds without interaction");calledGhast.setPos(player(mc).getX()+3,player(mc).getY(),player(mc).getZ());HappyGhastRecall.receive(player(mc));HappyGhastRecall.interacted(calledGhast,player(mc));check(!HappyGhastRecall.called(calledGhast),"Player interaction ends the temporary call");calledGhast.discard();});wait=10;}
    case 20->finish(mc,null);
   }
  }catch(Throwable e){finish(mc,e);}
 }
 private static void spatial(Minecraft mc)throws Exception{
  var type=Class.forName("org.tastytrash.spatialGUI.client.SpatialGUIConfig");Object c=type.getMethod("get").invoke(null);SpatialSettings.sync();
  UnboxClient.set("spatial.autoCamera",false);UnboxClient.set("spatial.firstInventory",true);SpatialSettings.sync();
  check(!(boolean)field(c,"autoDetectCameraMode")&&(boolean)field(c,"firstPersonModeInventory"),"Right Shift camera options update native Spatial GUI settings");
  var client=Class.forName("org.tastytrash.spatialGUI.client.SpatialGUIClient");
  check((boolean)client.getMethod("shouldUseFirstPersonMode",net.minecraft.client.gui.screens.Screen.class).invoke(null,new InventoryScreen(mc.player)),"Native inventory uses the selected first-person perspective");
  type.getField("firstPersonModeInventory").set(c,false);SpatialSettings.sync();check(!ClientHud.flag("spatial.firstInventory",true),"Native perspective changes update Right Shift settings");
  type.getField("screenAlpha").set(c,127);SpatialSettings.sync();SpatialSettings.sync();check((int)field(c,"screenAlpha")==127&&UnboxClient.number("spatial.opacity",0,0,100)==50,"Native opacity retains its precise value during two-way synchronization");
  type.getField("hideHud").set(c,true);SpatialSettings.sync();check(ClientHud.flag("spatial.hideHud",false),"Native HUD changes sync back without being overwritten");
  type.getField("allScreens").set(c,true);SpatialSettings.sync();check((boolean)field(c,"allScreens"),"Native advanced options are preserved");
  check(!(boolean)client.getMethod("shouldHookScreen",net.minecraft.client.gui.screens.Screen.class).invoke(null,new UnboxScreen()),"Spatial GUI leaves Unbox menus in their original layout");
  var panel=new UnboxScreen();mc.setScreen(panel);var detail=UnboxScreen.class.getDeclaredMethod("openDetail",String.class);detail.setAccessible(true);detail.invoke(panel,"spatial");
  check(SpatialSettings.open(panel).isEmpty()&&mc.screen.getClass().getName().contains("YACL"),"Native advanced Spatial GUI settings open from the client");
  type.getField("autoDetectCameraMode").set(c,true);mc.setScreen(panel);check(ClientHud.flag("spatial.autoCamera",false),"Returning from native settings refreshes Right Shift perspective controls immediately");mc.setScreen(null);
  type.getField("allScreens").set(c,false);UnboxClient.set("spatial.hideHud",false);UnboxClient.set("spatial.autoCamera",false);SpatialSettings.sync();
 }
 private static void joinCancellation(Minecraft mc)throws Exception{
  var saved=SocialBridge.view;var fixture=new com.google.gson.JsonObject();fixture.addProperty("connected",true);
  var self=new com.google.gson.JsonObject();self.addProperty("id","local-fixture");self.addProperty("kind","local");fixture.add("self",self);
  var invite=new com.google.gson.JsonObject();invite.addProperty("id","cancel-fixture");invite.addProperty("receiver","local-fixture");invite.addProperty("expires",System.currentTimeMillis()+60000);
  var room=new com.google.gson.JsonObject();room.addProperty("loader",Platform.loader().equals("fabric")?"neoforge":"fabric");room.addProperty("minecraft",net.minecraft.SharedConstants.getCurrentVersion().id());invite.add("room",room);
  var invites=new com.google.gson.JsonArray();invites.add(invite);fixture.add("invites",invites);SocialBridge.view=fixture;
  try{SocialBridge.join("cancel-fixture");check(SocialBridge.joining()&&mc.screen instanceof JoinProgressScreen,"Version switch first shows cancellable preparation");var screen=mc.screen;SocialBridge.join("cancel-fixture");check(mc.screen==screen,"Repeated Join reuses the active attempt");screen.onClose();check(!SocialBridge.joining()&&!Files.exists(mc.gameDirectory.toPath().resolve("unbox-switch-request.json")),"Cancel prevents restart request and keeps game running");
   var joiningField=SocialBridge.class.getDeclaredField("joining");joiningField.setAccessible(true);joiningField.setBoolean(null,true);
   mc.setScreen(new UnboxHomeScreen(false));SocialBridge.tick(mc);check(!SocialBridge.joining(),"Returning from connection Cancel clears the Join guard");mc.setScreen(null);
  }
  finally{SocialBridge.view=saved;}
 }
 private static void manifestScope()throws Exception{
  var root=Files.createTempDirectory("unbox-manifest-features-");
  try{Files.createDirectories(root.resolve("resourcepacks"));Files.createDirectories(root.resolve("shaderpacks"));Files.createDirectories(root.resolve("config"));
   Files.writeString(root.resolve("resourcepacks/active.zip"),"active fixture");Files.writeString(root.resolve("options.txt"),"resourcePacks:[\"vanilla\",\"file/active.zip\"]\n");
   var first=EnvironmentManifest.scan(root);Files.writeString(root.resolve("resourcepacks/unused.zip"),"unused fixture");Files.writeString(root.resolve("shaderpacks/disabled.zip"),"disabled fixture");Files.writeString(root.resolve("config/iris.properties"),"enableShaders=false\nshaderPack=disabled.zip\n");
   var second=EnvironmentManifest.scan(root);check(second.files().size()==1&&EnvironmentManifest.hashes(first.manifest()).equals(EnvironmentManifest.hashes(second.manifest())),"Unused packs and disabled shaders do not trigger synchronization");
  }finally{try(var paths=Files.walk(root)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
 }
 private static void shulker(Minecraft mc,boolean expected)throws Exception{
  Class<?> type=Class.forName("fuzs.iteminteractions.common.api.v2.world.item.storage.ContainerStorage");
  Object storage=type.getConstructor().newInstance();
  boolean allowed=(boolean)type.getMethod("allowModification",ItemStack.class,net.minecraft.world.entity.player.Player.class).invoke(storage,new ItemStack(Items.SHULKER_BOX),player(mc));
  check(allowed==expected,"Shulker server permission follows host: "+expected);
 }
 private static void build(Minecraft mc,boolean expected)throws Exception{
  var p=player(mc);BlockPos first=p.blockPosition().offset(1,2,0),last=first.offset(2,0,0);for(int i=0;i<3;i++)p.level().setBlock(first.offset(i,0,0),Blocks.AIR.defaultBlockState(),3);
  p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.STONE,64));
  String base="nl.requios.effortlessbuilding.";
  Class<?> packetType=Class.forName(base+"network.PlaceBuildModePacket");
  Object packet=packetType.getConstructors()[0].newInstance(enumeration(base+"buildmode.BuildModeEnum","LINE"),first,last,null,Direction.UP,Vec3.atCenterOf(first),enumeration(base+"buildmode.ModeOptions$ActionEnum","FULL"),enumeration(base+"buildmode.ModeOptions$ActionEnum","CUBE_FULL"),enumeration(base+"buildmode.ModeOptions$ActionEnum","SHORT_EDGE"),enumeration(base+"buildmode.ModeOptions$ActionEnum","CIRCLE_START_CORNER"),enumeration(base+"buildmode.BuildSettings$ReplaceMode","ONLY_AIR"),true);
  Class.forName(base+"network.PacketHandler").getMethod("handlePlaceBuildMode",packetType,ServerPlayer.class).invoke(null,packet,p);
  int placed=0;for(int i=0;i<3;i++)if(p.level().getBlockState(first.offset(i,0,0)).is(Blocks.STONE))placed++;
  check(placed==(expected?3:0),expected?"Native building packet places three blocks in Creative":"Server rejects building packet in Survival");
  if(expected){Class.forName(base+"network.PacketHandler").getMethod("handleUndo",ServerPlayer.class).invoke(null,p);check(p.level().getBlockState(first).isAir(),"Native building Undo restores world");}
 }
 private static void finish(Minecraft mc,Throwable error){finished=true;if(error!=null)error.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("logs/feature-smoke.json"),new Gson().toJson(Map.of("ok",error==null,"loader",Platform.loader(),"checks",checks,"error",error==null?"":error.toString())));}catch(Exception e){e.printStackTrace();}mc.stop();}
}
