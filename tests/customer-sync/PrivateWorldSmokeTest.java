package dev.unbox.client;
import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import java.nio.file.*;
import java.util.*;
/** Test-only replacement in an isolated JAR. Never changes camera or player movement. */
final class PrivateWorldSmokeTest {
 private static boolean creating; private static int step,ticks; private static long begun; private static final List<String> checks=new ArrayList<>();
 static boolean interruptStreamForTest(Minecraft mc){return false;}
 private static void finish(Minecraft mc)throws Exception {
  Files.writeString(mc.gameDirectory.toPath().resolve("logs/private-world-smoke.json"),new Gson().toJson(Map.of("passed",checks.size(),"checks",checks)));
  step=99;mc.stop();
 }
 static void tick(Minecraft mc){
  String role=System.getProperty("unbox.privateSmoke","");if(role.isEmpty()||step==99)return;
  if(begun==0)begun=System.currentTimeMillis();if(System.currentTimeMillis()-begun>900000)throw new IllegalStateException("Customer sync timeout: "+SocialBridge.message);
  if(++ticks%20!=0)return;
  try {
   Files.writeString(mc.gameDirectory.toPath().resolve("logs/customer-stage.json"),new Gson().toJson(Map.of("step",step,"ticks",ticks,"screen",mc.screen==null?"none":mc.screen.getClass().getName(),"title",mc.screen==null?"":mc.screen.getTitle().getString(),"message",SocialBridge.message)));
   if(mc.screen!=null&&mc.screen.getClass().getName().equals("de.ambertation.wover.ui.impl.client.WelcomeScreen")&&mc.getOverlay()==null){mc.screen.onClose();checks.add("Dismissed WorldWeaver first-run welcome through its normal close action");return;}
   if(mc.screen instanceof DisconnectedScreen)throw new IllegalStateException("Disconnected: "+mc.screen.getTitle().getString()+" / "+SocialBridge.message);
   if(role.equals("guest")&&mc.screen instanceof JoinDecisionScreen){
    var button=mc.screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().equals("Restart and join")).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow();
    button.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0));
   }
   if(role.equals("host")){
    if(!creating&&mc.screen instanceof UnboxHomeScreen&&mc.getOverlay()==null){
     creating=true;
     var data=net.minecraft.world.level.WorldDataConfiguration.DEFAULT;
     var settings=new net.minecraft.world.level.LevelSettings("Customer sync isolated world",net.minecraft.world.level.GameType.CREATIVE,false,net.minecraft.world.Difficulty.PEACEFUL,true,new net.minecraft.world.level.gamerules.GameRules(data.enabledFeatures()),data);
     mc.createWorldOpenFlows().createFreshLevel("CustomerSync",settings,new net.minecraft.world.level.levelgen.WorldOptions(1411263L,false,false),net.minecraft.world.level.levelgen.presets.WorldPresets::createFlatWorldDimensions,new UnboxHomeScreen(false));
    }
    if(mc.getSingleplayerServer()==null||mc.player==null||!SocialBridge.flag(SocialBridge.view,"connected"))return;
    if(step==0&&!SocialBridge.rows("friends").isEmpty()){
     var snapshot=EnvironmentManifest.scan(mc.gameDirectory.toPath());
     Files.writeString(mc.gameDirectory.toPath().resolve("logs/customer-manifest.json"),snapshot.manifest().toString());
     SocialBridge.invite(SocialBridge.text(SocialBridge.rows("friends").getFirst(),"id"));
     if(!SocialBridge.hosting())throw new IllegalStateException(SocialBridge.message);
     checks.add("Host loaded customer mods and invited through normal sharing");step=1;
    }
    if(step==1&&mc.getSingleplayerServer().getPlayerList().getPlayerCount()==2){checks.add("Host server accepted the distinct guest identity and both players coexist");step=2;}
    if(step==2&&Files.exists(mc.gameDirectory.toPath().resolve("logs/guest-complete.json"))){checks.add("Guest completed restarted join and world ticks");SocialBridge.stopSharing();finish(mc);}
   }else{
    if(Boolean.getBoolean("unbox.syncResume")&&step==0)step=1;
    if(step==0&&!SocialBridge.rows("invites").isEmpty()&&SocialBridge.flag(SocialBridge.view,"connected")){
     SocialBridge.join(SocialBridge.text(SocialBridge.rows("invites").getFirst(),"id"));step=1;
    }
    if(step==1&&mc.player!=null&&mc.level!=null&&mc.getSingleplayerServer()==null){
     if(!Boolean.getBoolean("unbox.syncResume"))throw new IllegalStateException("Guest joined without required restart");
     if(!mc.player.getUUID().equals(mc.getUser().getProfileId()))throw new IllegalStateException("Guest identity mismatch");
     var snapshot=EnvironmentManifest.scan(mc.gameDirectory.toPath());
     if(snapshot.manifest().getAsJsonArray("files").size()<10)throw new IllegalStateException("Customer gameplay mods missing");
     for(var e:EnvironmentManifest.validate(snapshot.manifest()))if(e.kind().equals("mods")&&!Platform.loaded(e.id()))throw new IllegalStateException("Mod not loaded: "+e.id());
     Files.writeString(mc.gameDirectory.toPath().resolve("logs/customer-manifest.json"),snapshot.manifest().toString());
     checks.add("Transferred gameplay mods loaded after restart; normal login entered host world");step=2;ticks=0;
    }
    if(step==2&&ticks>=200){
     if(mc.level.getChunkSource().getLoadedChunksCount()==0)throw new IllegalStateException("No world chunks received");
     checks.add("Guest received world chunks and remained connected for 200 gameplay ticks with fixed view");finish(mc);
    }
   }
  }catch(Exception e){throw new RuntimeException("Customer sync "+role+" step "+step,e);}
 }
}
