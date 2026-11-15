package dev.unbox.unpvp;
import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.world.*;
import java.nio.file.*;
/** Opt-in two-process integration fixture. No production account or world is used. */
final class SocialProbe {
 static int stage,ticks;static boolean rosterSeen;static JsonObject fixture;static int checks;static long started=System.currentTimeMillis();static String role=System.getProperty("unpvp.test.social","");
 static void report(String value){System.out.println("[unpvp-social-test] "+value);try{Files.write(SocialBridge.root().resolve("social-test-result.txt"),value.getBytes("UTF-8"));}catch(Exception e){throw new RuntimeException(e);}}
 static void tick(){Minecraft mc=Minecraft.getMinecraft();if(role.isEmpty())return;if(role.equals("ui")){ui();return;}if(System.currentTimeMillis()-started>150000){report("FAIL timeout "+stage+" "+SocialBridge.message);mc.shutdown();return;}
  if(stage==0&&mc.currentScreen instanceof HomeScreen){stage=1;if(role.equals("host"))mc.launchIntegratedServer("SocialLab","Social test world",new WorldSettings(42,WorldSettings.GameType.CREATIVE,false,false,WorldType.FLAT));}
  if(role.equals("host")){
   if(Boolean.getBoolean("unpvp.test.expectReject")&&stage==2&&Files.exists(SocialBridge.root().resolve("social-test-finish.json"))){if(mc.getIntegratedServer().getCurrentPlayerCount()!=1)throw new IllegalStateException("Unauthorized guest entered");report("PASS unauthorized guest rejected, owner retained");stage=5;mc.shutdown();return;}
   if(stage==1&&mc.theWorld!=null&&SocialBridge.connected()&&!SocialBridge.rows("friends").isEmpty()){SocialBridge.invite(SocialBridge.text(SocialBridge.rows("friends").get(0),"id"));stage=2;}
   if(stage==2&&mc.getIntegratedServer().getCurrentPlayerCount()==2){report("PASS host admitted guest");stage=3;}
   if(stage==3&&++ticks>140){if(!NameBadges.known(new java.util.UUID(0,2)))throw new IllegalStateException("Host did not receive private badge roster");System.out.println("[unpvp-social-test] PASS host recognizes guest badge capability");SocialBridge.stopSharing();stage=4;ticks=0;}
   if(stage==4&&++ticks>60&&mc.getIntegratedServer().getCurrentPlayerCount()==1){report("PASS host revoked guest and retained owner");mc.shutdown();stage=5;}
  }else{
   if(stage==1&&SocialBridge.connected()&&!SocialBridge.worlds().isEmpty()){SocialBridge.join(SocialBridge.text(SocialBridge.worlds().get(0),"id"));stage=2;}
   if(stage==2&&mc.theWorld!=null){if(!mc.getNetHandler().getNetworkManager().getIsencrypted())throw new IllegalStateException("Unencrypted private connection");if(!mc.thePlayer.getUniqueID().equals(mc.getSession().getProfile().getId()))throw new IllegalStateException("Identity changed");report("PASS guest joined encrypted with stable UUID");stage=3;}
   if(stage==3&&mc.theWorld!=null&&NameBadges.known(new java.util.UUID(0,1)))rosterSeen=true;
   if(stage==3&&mc.currentScreen instanceof GuiDisconnected){if(!rosterSeen)throw new IllegalStateException("Guest did not receive private badge roster");System.out.println("[unpvp-social-test] PASS guest recognizes host badge capability");report("PASS guest disconnected after revocation");mc.shutdown();stage=4;}
   if(stage==2&&mc.currentScreen instanceof GuiDisconnected){String reason=((SocialBridge.DisconnectReason)mc.currentScreen).unboxReason().getUnformattedText();if(Boolean.getBoolean("unpvp.test.expectReject")&&reason.contains("verify"))report("PASS wrong invitation secret rejected");else report("FAIL login "+reason);mc.shutdown();stage=4;}
  }
 }
 static void check(boolean ok,String label){if(!ok)throw new IllegalStateException(label);checks++;System.out.println("[unpvp-social-ui] PASS "+label);}
 static void shot(String name){Minecraft mc=Minecraft.getMinecraft();java.io.File d=new java.io.File(mc.mcDataDir,"social-ui");d.mkdirs();net.minecraft.util.ScreenShotHelper.saveScreenshot(d,name+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
 static void ui(){Minecraft mc=Minecraft.getMinecraft();try{
  if(stage==0&&mc.currentScreen instanceof HomeScreen){stage=1;String me="10000000-0000-0000-0000-000000000002",other="10000000-0000-0000-0000-000000000001";fixture=new JsonParser().parse("{connected:true,self:{id:'"+me+"',uuid:'00000000000000000000000000000002',code:'0123456789AB'},friends:[{id:'"+other+"',uuid:'00000000000000000000000000000001',name:'SocialHost',accepted:1,online:true,state:'In world'},{id:'request',name:'NewFriend',accepted:0,direction:'incoming'}],invites:[{id:'invitation',sender:'"+other+"',receiver:'"+me+"',senderName:'SocialHost',room:{name:'My private world',minecraft:'1.8.9',loader:'forge'}}]}").getAsJsonObject();fixture.addProperty("updated",System.currentTimeMillis());fixture.getAsJsonArray("invites").get(0).getAsJsonObject().addProperty("expires",System.currentTimeMillis()+300000);SocialBridge.write("unbox-social-view.json",fixture);return;}
  if(stage==1&&SocialBridge.connected()){check(SocialBridge.worlds().size()==1,"Own current invitations available");JsonObject stale=SocialBridge.copy(fixture);stale.addProperty("updated",0);SocialBridge.accept(stale,new JsonObject(),new JsonObject());check(!SocialBridge.connected()&&SocialBridge.worlds().isEmpty(),"Stale launcher data cannot join");check(!SocialBridge.command("request","0123456789AB"),"Offline commands rejected");SocialBridge.accept(fixture,new JsonObject(),new JsonObject());check(SocialBridge.command("request","0123456789AB"),"Friend request queues");check(!SocialBridge.command("remove","anything"),"Pending command cannot be overwritten");stage=2;ticks=0;}
  if(stage==2&&++ticks>20){JsonObject c=SocialBridge.read("unbox-social-command.json");check(SocialBridge.text(c,"op").equals("request"),"Launcher receives request operation");JsonObject ack=new JsonObject();ack.addProperty("requestId",SocialBridge.text(c,"requestId"));ack.addProperty("ok",true);SocialBridge.write("unbox-social-ack.json",ack);stage=3;ticks=0;}
  if(stage==3&&!SocialBridge.pending()){check(SocialBridge.commandSucceeded(),"Matching acknowledgement clears pending state");HomeScreen home=new HomeScreen(false);home.page="quick";mc.displayGuiScreen(home);stage=4;ticks=0;}
  if(stage==4&&++ticks>40){shot("quick-friends");mc.displayGuiScreen(new FriendsScreen(new HomeScreen(false)));stage=5;ticks=0;}
  if(stage==5&&++ticks>40){shot("friend-management");mc.displayGuiScreen(new MultiplayerScreen(new HomeScreen(false)));stage=6;ticks=0;}
  if(stage==6&&++ticks>40){check(((MultiplayerScreen)mc.currentScreen).invitedCount()==1,"Invitation appears inside vanilla multiplayer list");shot("multiplayer-world");mc.displayGuiScreen(new ConnectionScreen(new HomeScreen(false)));stage=7;ticks=0;}
  if(stage==7&&++ticks>30){shot("connection");mc.displayGuiScreen(new SkinsScreen(new HomeScreen(false)));stage=8;ticks=0;}
  if(stage==8&&++ticks>30){shot("appearance");String secret=String.join("",java.util.Collections.nCopies(64,"a"));check(!java.security.MessageDigest.isEqual(PrivateLogin.proof(secret,"host","room","person","nonce","key"),PrivateLogin.proof(secret,"guest","room","person","nonce","key")),"Host proof cannot be reflected as guest proof");check(!java.security.MessageDigest.isEqual(PrivateLogin.proof(secret,"guest","room","person","nonce","key"),PrivateLogin.proof(secret,"guest","room","person","other","key")),"Proof bound to per-connection nonce");report("PASS UI and bridge checks="+checks);stage=9;mc.shutdown();}
 }catch(Exception e){throw new RuntimeException(e);}}

}
