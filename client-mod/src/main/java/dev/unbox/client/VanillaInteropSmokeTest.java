package dev.unbox.client;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
/** Explicit local fixture only: tests Mojang's unmodified client against ordinary LAN publishing. */
final class VanillaInteropSmokeTest {
 private static int step,ticks;private static long begun;
 static void tick(Minecraft mc){if(!Boolean.getBoolean("unbox.vanillaInterop"))return;if(begun==0)begun=System.currentTimeMillis();if(System.currentTimeMillis()-begun>180000)throw new IllegalStateException("Vanilla interoperability timeout");if(++ticks%20!=0||mc.player==null||mc.getSingleplayerServer()==null)return;
  try{var s=mc.getSingleplayerServer();if(step==0){int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}if(!s.publishServer(s.getDefaultGameType(),false,port))throw new IllegalStateException("LAN publish failed");s.setUsesAuthentication(false);Files.writeString(mc.gameDirectory.toPath().resolve("logs/vanilla-port.txt"),""+s.getPort());step=1;}
   else if(step==1){var p=s.getPlayerList().getPlayers().stream().filter(v->v.getGameProfile().name().equals("VanillaGuest")).findFirst().orElse(null);if(p==null)return;if(MapTransport.badgeCapable(p)||NameBadges.known(p.getUUID()))throw new IllegalStateException("Vanilla guest incorrectly branded");ticks=0;step=2;}
   else if(step==2&&ticks>=100){var p=s.getPlayerList().getPlayers().stream().filter(v->v.getGameProfile().name().equals("VanillaGuest")).findFirst().orElseThrow();if(NameBadges.known(p.getUUID()))throw new IllegalStateException("Vanilla guest got a badge");s.execute(()->p.sendSystemMessage(net.minecraft.network.chat.Component.literal("UNBOX_VANILLA_INTEROP_OK")));Files.writeString(mc.gameDirectory.toPath().resolve("logs/vanilla-interop.json"),"{\"ordinaryLanJoin\":true,\"vanillaGuestHasBadge\":false,\"connectedTicks\":100,\"authentication\":\"isolated offline fixture\"}");step=3;ticks=0;}
   else if(step==3&&ticks>=40)mc.stop();
  }catch(Exception e){throw new RuntimeException("Vanilla interoperability fixture",e);}
 }
}
