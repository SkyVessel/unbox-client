package dev.unbox.unpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.network.OldServerPinger;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import java.util.concurrent.*;

/** Server-list status ping, matching Lunar's measurement family. No gameplay packets. */
public final class Latency {
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"unbox-status-ping");t.setDaemon(true);return t;});
    private static volatile Integer sample;
    private static volatile long updated,generation;
    private static long next;
    private static volatile boolean busy;
    private static NetworkManager network;
    private static String address="";
    static void tick(){
        Minecraft mc=Minecraft.getMinecraft();
        NetworkManager current=mc.getNetHandler()==null?null:mc.getNetHandler().getNetworkManager();
        ServerData server=mc.getCurrentServerData();
        String target=mc.theWorld!=null&&!mc.isSingleplayer()&&UiState.on("ping")&&server!=null?server.serverIP:"";
        if(current!=network||!target.equals(address)){network=current;address=target;generation++;sample=null;updated=next=0;}
        long now=System.nanoTime();
        if(target.isEmpty()||current==null||busy||now<next)return;
        next=now+10_000_000_000L;busy=true;final long epoch=generation;
        IO.execute(()->{OldServerPinger pinger=new OldServerPinger();try{
            ServerData data=new ServerData("Ping",target,false);data.pingToServer=-1;
            pinger.ping(data);long deadline=System.nanoTime()+5_000_000_000L;
            while(epoch==generation&&data.pingToServer<0&&System.nanoTime()<deadline){pinger.pingPendingNetworks();Thread.sleep(2);}
            if(epoch==generation){sample=data.pingToServer>=0?(int)Math.min(Integer.MAX_VALUE,data.pingToServer):null;updated=System.nanoTime();}
        }catch(Exception e){if(epoch==generation)sample=null;if(e instanceof InterruptedException)Thread.currentThread().interrupt();}
        finally{pinger.clearPendingNetworks();busy=false;}});
    }
    static Integer value(){return System.nanoTime()-updated<30_000_000_000L?sample:null;}
    // Retain binary compatibility with the diagnostic packet hooks; never send REQUEST_STATS.
    public static void sent(NetworkManager source,Packet packet){}
    public static void received(NetworkManager source,Packet packet){}
}
