package dev.unbox.unpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.achievement.GuiStats;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C16PacketClientStatus;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S37PacketStatistics;

/** Approximate game-connection response time, not the server's TAB latency field.
 * Statistics replies have no request ID: other observed requests invalidate our sample.
 * Includes server processing and outbound queue time; unsolicited replies remain ambiguous.
 */
public final class Latency {
    private static final Measurement measurement=new Measurement();
    private static NetworkManager network;
    private static Object world;
    private Latency(){}

    static void tick(){
        Minecraft mc=Minecraft.getMinecraft();
        NetworkManager current=mc.getNetHandler()==null?null:mc.getNetHandler().getNetworkManager();
        C16PacketClientStatus request;
        synchronized(measurement){
            if(current!=network||world!=mc.theWorld){network=current;world=mc.theWorld;measurement.reset();}
            if(world==null||current==null||mc.isSingleplayer()||!UiState.on("ping")){measurement.reset();return;}
            long now=System.nanoTime();
            if(mc.currentScreen instanceof GuiStats){measurement.defer(now);return;}
            request=measurement.request(now);
        }
        if(request!=null)current.sendPacket(request);
    }

    static Integer value(){synchronized(measurement){return measurement.value(System.nanoTime());}}

    public static void sent(NetworkManager source,Packet packet){
        if(!(packet instanceof C16PacketClientStatus)||((C16PacketClientStatus)packet).getStatus()!=C16PacketClientStatus.EnumState.REQUEST_STATS)return;
        synchronized(measurement){if(source==network)measurement.sent(packet,System.nanoTime());}
    }

    public static void received(NetworkManager source,Packet packet){
        if(!(packet instanceof S37PacketStatistics)&&!(packet instanceof S01PacketJoinGame)&&!(packet instanceof S07PacketRespawn))return;
        synchronized(measurement){
            if(source!=network)return;
            if(packet instanceof S37PacketStatistics)measurement.reply(System.nanoTime());
            else {measurement.reset();world=null;}
        }
    }

    /** All calls are guarded by measurement; explicit times also permit deterministic tests. */
    static final class Measurement {
        private static final long INTERVAL=10_000_000_000L,TIMEOUT=5_000_000_000L;
        private C16PacketClientStatus pending;
        private long started,next,updated;
        private boolean sent,blocked;
        private Integer sample;
        void reset(){pending=null;sample=null;started=next=updated=0;sent=blocked=false;}
        void defer(long now){pending=null;sent=false;next=now+INTERVAL;}
        C16PacketClientStatus request(long now){
            expire(now);
            if(blocked||pending!=null||now<next)return null;
            pending=new C16PacketClientStatus(C16PacketClientStatus.EnumState.REQUEST_STATS);
            started=now;sent=false;next=now+INTERVAL;return pending;
        }
        void sent(Packet packet,long now){
            if(packet==pending){started=now;sent=true;}
            else defer(now);
        }
        void reply(long now){
            expire(now);
            if(pending==null||!sent)return;
            sample=(int)Math.max(0,Math.round((now-started)/1_000_000.0));updated=now;pending=null;sent=false;
        }
        Integer value(long now){expire(now);return sample!=null&&now-updated<3*INTERVAL?sample:null;}
        private void expire(long now){
            // No request IDs: after timeout do not risk assigning a late reply to a new request.
            // A new world/connection (or toggling the HUD) resets this state.
            if(pending!=null&&now-started>=TIMEOUT){pending=null;sample=null;sent=false;blocked=true;}
        }
    }
}
