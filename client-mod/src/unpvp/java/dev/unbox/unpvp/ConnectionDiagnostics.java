package dev.unbox.unpvp;

import com.google.gson.*;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import java.lang.management.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.*;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.server.*;
import net.minecraft.network.play.client.C02PacketUseEntity;
import javax.management.*;
import javax.management.openmbean.CompositeData;
import com.sun.management.GarbageCollectionNotificationInfo;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Opt-in bounded local timing report. Packet threads only update counters; disk I/O is asynchronous. */
public final class ConnectionDiagnostics {
    private static volatile boolean enabled;
    private static volatile NetworkManager network;
    private static final Object LOCK=new Object();
    private static final IdentityHashMap<Packet,Long> arrivals=new IdentityHashMap<>();
    private static final Map<Integer,Long> keepalives=new HashMap<>();
    private static final ArrayList<Double> replies=new ArrayList<>(),queues=new ArrayList<>(),frames=new ArrayList<>(),gcPauses=new ArrayList<>();
    private static final ArrayDeque<JsonObject> windows=new ArrayDeque<>();
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Unbox connection diagnostics");t.setDaemon(true);return t;});
    private static JsonObject connection=new JsonObject();
    private static long lastReport,lastFrame,gcCount,gcMs;
    private static int dropped,attacks,velocities,hurts;private static final List<NotificationEmitter> gcSources=new ArrayList<>();
    private static final NotificationListener GC_LISTENER=(event,handback)->{if(!enabled||!event.getType().equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION))return;try{double ms=GarbageCollectionNotificationInfo.from((CompositeData)event.getUserData()).getGcInfo().getDuration();synchronized(LOCK){if(gcPauses.size()<256)gcPauses.add(ms);}}catch(RuntimeException ignored){}};
    private static void observeGc(boolean on){for(NotificationEmitter emitter:gcSources)try{emitter.removeNotificationListener(GC_LISTENER);}catch(Exception ignored){}gcSources.clear();if(on)for(GarbageCollectorMXBean bean:ManagementFactory.getGarbageCollectorMXBeans())if(bean instanceof NotificationEmitter)try{NotificationEmitter e=(NotificationEmitter)bean;e.addNotificationListener(GC_LISTENER,null,null);gcSources.add(e);}catch(Exception ignored){}}
    static void resetWindow(){synchronized(LOCK){arrivals.clear();keepalives.clear();replies.clear();queues.clear();frames.clear();gcPauses.clear();attacks=velocities=hurts=dropped=0;lastFrame=0;}lastReport=System.nanoTime();gcCount=gc(false);gcMs=gc(true);}

    static volatile String status="Off";
    private ConnectionDiagnostics(){}

    static void toggle(){UiState.set("performance.connectionDiagnostics",!UiState.flag("performance.connectionDiagnostics",false));UiState.save();tick();}
    static void tick(){
        Minecraft mc=Minecraft.getMinecraft();boolean want=Feedback.active()||UiState.flag("performance.connectionDiagnostics",false);
        NetworkManager current=want&&mc.getNetHandler()!=null?mc.getNetHandler().getNetworkManager():null;
        if(want!=enabled||current!=network){
            if(enabled)save();enabled=want;network=current;observeGc(want);
            synchronized(LOCK){arrivals.clear();keepalives.clear();replies.clear();queues.clear();frames.clear();gcPauses.clear();attacks=velocities=hurts=dropped=0;lastFrame=0;}
            connection=describe(current);lastReport=System.nanoTime();gcCount=gc(false);gcMs=gc(true);
            status=want?"Recording locally":"Off";
        }
        if(enabled&&System.nanoTime()-lastReport>=10_000_000_000L)save();
    }
    static void frame(){if(!enabled||network==null)return;long now=System.nanoTime();synchronized(LOCK){if(lastFrame!=0&&frames.size()<20000)frames.add((now-lastFrame)/1e6);lastFrame=now;}}
    public static void received(NetworkManager source,Packet packet){
        if(!enabled||source!=network)return;long now=System.nanoTime();
        synchronized(LOCK){
            if(packet instanceof S12PacketEntityVelocity)velocities++;if(packet instanceof S19PacketEntityStatus&&((S19PacketEntityStatus)packet).getOpCode()==2)hurts++;
            if(packet instanceof S00PacketKeepAlive){if(keepalives.size()<32)keepalives.put(((S00PacketKeepAlive)packet).func_149134_c(),now);else dropped++;}
            else if(packet instanceof S12PacketEntityVelocity||packet instanceof S14PacketEntity||packet instanceof S18PacketEntityTeleport||packet instanceof S19PacketEntityStatus){if(arrivals.size()<256)arrivals.put(packet,now);else dropped++;}
        }
    }
    public static void sent(NetworkManager source,Packet packet){
        if(!enabled||source!=network)return;if(packet instanceof C02PacketUseEntity&&((C02PacketUseEntity)packet).getAction()==C02PacketUseEntity.Action.ATTACK){synchronized(LOCK){attacks++;}}if(!(packet instanceof C00PacketKeepAlive))return;long now=System.nanoTime();
        synchronized(LOCK){Long at=keepalives.remove(((C00PacketKeepAlive)packet).getKey());if(at!=null&&replies.size()<1000)replies.add((now-at)/1e6);}
    }
    public static void handled(Packet packet){
        if(!enabled)return;long now=System.nanoTime();synchronized(LOCK){Long at=arrivals.remove(packet);if(at!=null&&queues.size()<20000)queues.add((now-at)/1e6);}
    }
    private static JsonObject describe(NetworkManager n){
        JsonObject v=new JsonObject();v.addProperty("java",System.getProperty("java.version"));v.addProperty("vm",System.getProperty("java.vm.name"));v.addProperty("os",System.getProperty("os.name"));v.addProperty("arch",System.getProperty("os.arch"));v.addProperty("maxHeapMiB",Runtime.getRuntime().maxMemory()/1048576);
        Minecraft mc=Minecraft.getMinecraft();if(mc.getCurrentServerData()!=null)v.addProperty("requestedAddress",safeAddress(mc.getCurrentServerData().serverIP));
        v.addProperty("preferIPv4Stack",System.getProperty("java.net.preferIPv4Stack","default"));v.addProperty("preferIPv6Addresses",System.getProperty("java.net.preferIPv6Addresses","default"));v.addProperty("socksProxyPropertyPresent",System.getProperty("socksProxyHost")!=null);
        JsonArray collectors=new JsonArray();for(GarbageCollectorMXBean b:ManagementFactory.getGarbageCollectorMXBeans())collectors.add(new JsonPrimitive(b.getName()));v.add("collectors",collectors);
        if(n!=null)try{
            Channel ch=(Channel)ReflectionHelper.findField(NetworkManager.class,"channel","field_150746_k").get(n);
            v.addProperty("remoteEndpoint",safeAddress(String.valueOf(ch.remoteAddress())));v.addProperty("route",n.isLocalChannel()?"integrated":PrivateLogin.route(n));if(ch.remoteAddress() instanceof java.net.InetSocketAddress){java.net.InetAddress address=((java.net.InetSocketAddress)ch.remoteAddress()).getAddress();v.addProperty("addressFamily",address instanceof java.net.Inet6Address?"IPv6":"IPv4");}v.addProperty("channel",ch.getClass().getSimpleName());v.addProperty("localTransport",n.isLocalChannel());
            Boolean noDelay=ch.config().getOption(ChannelOption.TCP_NODELAY);if(noDelay!=null)v.addProperty("tcpNoDelay",noDelay);
            JsonArray pipeline=new JsonArray();for(Map.Entry<String,io.netty.channel.ChannelHandler> e:ch.pipeline())pipeline.add(new JsonPrimitive(e.getKey()+":"+e.getValue().getClass().getSimpleName()));v.add("pipeline",pipeline);
        }catch(Exception e){v.addProperty("inspectionError",e.getClass().getSimpleName());}
        return v;
    }
    private static String safeAddress(String address){return address!=null&&address.length()<=300&&!address.contains("@")&&!address.contains("?")?address:"redacted";}
    private static long gc(boolean time){long total=0;for(GarbageCollectorMXBean b:ManagementFactory.getGarbageCollectorMXBeans())total+=Math.max(0,time?b.getCollectionTime():b.getCollectionCount());return total;}
    private static JsonObject stats(List<Double> values){JsonObject out=new JsonObject();out.addProperty("samples",values.size());if(!values.isEmpty()){Collections.sort(values);out.addProperty("p50Ms",values.get(values.size()/2));out.addProperty("p95Ms",values.get(Math.min(values.size()-1,(int)(values.size()*.95))));out.addProperty("maxMs",values.get(values.size()-1));}return out;}
    static void save(){
        if(!enabled)return;long now=System.nanoTime(),count=gc(false),time=gc(true);JsonObject window=new JsonObject();
        window.addProperty("at",System.currentTimeMillis());window.addProperty("durationMs",(now-lastReport)/1e6);window.add("connection",connection);window.addProperty("gcCollections",Math.max(0,count-gcCount));window.addProperty("gcCollectionTimeMs",Math.max(0,time-gcMs));Integer latency=Latency.value();if(latency!=null)window.addProperty("responseEstimateMs",latency);
        Minecraft mc=Minecraft.getMinecraft();window.addProperty("fullbright",UiState.on("fullbright"));window.addProperty("screen",mc.currentScreen==null?"game":mc.currentScreen.getClass().getSimpleName());if(mc.theWorld!=null)window.addProperty("loadedEntities",mc.theWorld.loadedEntityList.size());if(mc.thePlayer!=null){window.addProperty("playerSprinting",mc.thePlayer.isSprinting());net.minecraft.client.network.NetworkPlayerInfo info=mc.getNetHandler()==null?null:mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());if(info!=null)window.addProperty("serverReportedPingMs",info.getResponseTime());}
        List<Double> replySnapshot,queueSnapshot,frameSnapshot,gcSnapshot;final int feedbackTicket=Feedback.ticket();
        synchronized(LOCK){gcSnapshot=new ArrayList<>(gcPauses);gcPauses.clear();window.addProperty("attackPackets",attacks);window.addProperty("velocityPackets",velocities);window.addProperty("hurtPackets",hurts);attacks=velocities=hurts=0;replySnapshot=new ArrayList<>(replies);queueSnapshot=new ArrayList<>(queues);frameSnapshot=new ArrayList<>(frames);window.addProperty("droppedSamples",dropped);window.addProperty("unhandledSamples",arrivals.size());arrivals.clear();keepalives.clear();replies.clear();queues.clear();frames.clear();dropped=0;}
        lastReport=now;gcCount=count;gcMs=time;
        IO.submit(()->{
            window.add("keepaliveHandler",stats(replySnapshot));window.add("entityMainThreadQueue",stats(queueSnapshot));window.add("frames",stats(frameSnapshot));window.add("gcEvents",stats(gcSnapshot));
            Feedback.record(window,feedbackTicket);windows.add(window);while(windows.size()>60)windows.removeFirst();JsonObject report=new JsonObject();report.addProperty("schema",1);report.addProperty("note","Keepalive ends at sendPacket entry, not wire delivery. Entity timing starts after decode. GC events are collector duration, not all JVM safepoints. Endpoint is the TCP peer, not every network hop.");JsonArray list=new JsonArray();for(JsonObject w:windows)list.add(w);report.add("windows",list);
            try{Path file=SocialBridge.root().resolve("logs/unbox-connection-diagnostics.json");Files.createDirectories(file.getParent());Path tmp=file.resolveSibling(".unbox-connection-diagnostics.tmp");Files.write(tmp,report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);status=enabled?"Recording locally":"Saved locally";}catch(Exception e){status="Could not save diagnostics";}
        });
    }
    static void finish(){save();observeGc(false);IO.shutdown();try{IO.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}
