package dev.unbox.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.*;
import java.util.*;

/** Optional two-process loopback fixture. All observations arrive through the registered native packet handlers. */
final class SurvivalLanSmokeTest {
    private static final String ROLE=System.getProperty("unbox.survivalLan","");
    private static final Gson JSON=new Gson();
    private static final List<String> CHECKS=new ArrayList<>();
    private static long started;
    private static int ticks,stage=1,lastGuestStage,stageAt;
    private static boolean connected,ordinary,finished;
    private static volatile boolean queued;
    private static UUID barrier=UUID.randomUUID();
    private static final String GUEST="UnboxLanGuest";
    private SurvivalLanSmokeTest(){}
    private static Path root(Minecraft mc){return Path.of(System.getProperty("unbox.survivalLanRoot",mc.gameDirectory.toString()));}
    private static Path shared(Minecraft mc,String file){return root(mc).resolve("logs").resolve(file);}

    static void tick(Minecraft mc){
        if(ROLE.isEmpty()||finished)return;
        if(!ROLE.equals("host")&&!ROLE.equals("guest"))throw new IllegalStateException("Invalid survival LAN fixture role");
        if(started==0)started=System.currentTimeMillis();
        try{
            if(System.currentTimeMillis()-started>220000)throw new IllegalStateException("Survival LAN fixture timed out at stage "+stage+" / "+lastGuestStage);
            if(++ticks%10!=0)return;
            if(ROLE.equals("host"))host(mc);else guest(mc);
        }catch(Throwable error){finish(mc,error);}
    }
    private static void host(Minecraft mc)throws Exception{
        var server=mc.getSingleplayerServer();if(server==null||mc.player==null)return;
        check(SurvivalHostMods.available("veinminer"),SurvivalHostMods.reason("veinminer"));
        check(SurvivalHostMods.available("fallingtree"),SurvivalHostMods.reason("fallingtree"));
        if(!connected){
            int port;try(var socket=new java.net.ServerSocket(0)){port=socket.getLocalPort();}
            check(server.publishServer(server.getDefaultGameType(),false,port),"LAN publish failed");server.setUsesAuthentication(false);
            Files.writeString(shared(mc,"survival-lan-port.txt"),""+server.getPort());connected=true;
            CHECKS.add("Published an isolated offline loopback LAN host without account or cloud login");
        }
        JsonObject ack=read(shared(mc,"survival-lan-ack.json"));
        if(ack.has("stage")&&ack.get("stage").getAsInt()==stage){
            if(stage==4){finish(mc,null);return;}
            stage++;stageAt=ticks;barrier=UUID.randomUUID();
        }
        boolean enabled=stage!=2;
        UnboxClient.set("veinminer",enabled);UnboxClient.set("fallingtree",enabled);UnboxClient.set("gravestones",enabled);
        if(stage==4&&(!ack.has("ordinaryReady")||!ack.get("ordinaryReady").getAsBoolean()))return;
        if(ticks-stageAt<40||SurvivalHostMods.serverEnabled("veinminer")!=enabled||SurvivalHostMods.serverEnabled("fallingtree")!=enabled||MapSharing.worldId(server).isEmpty()||queued)return;
        queued=true;int current=stage;UUID sentinel=barrier;
        server.execute(()->{
            try{
                ServerPlayer peer=server.getPlayerList().getPlayers().stream().filter(p->p.getGameProfile().name().equals(GUEST)).findFirst().orElse(null);
                if(peer==null)return;
                check(MapTransport.canSendSurvivalHostState(peer)&&MapTransport.canSendHostState(peer)&&MapTransport.canSendWorld(peer)&&MapTransport.canSend(peer)&&MapTransport.badgeCapable(peer),"Guest did not negotiate every native fixture channel");
                var owner=server.getPlayerList().getPlayer(mc.getUser().getProfileId());check(owner!=null,"Host player missing");
                // This exercises the real production server emission, codec and TCP receive path.
                // No client receive() is called by the fixture and no player is killed.
                // LAN stages rely on the normal host tick broadcasts, not manually invoked state receivers/senders.
                // On ordinary reconnect, explicitly resend positive state to prove that the client rejects it.
                if(current==4)MapSharing.joined(peer,server);
                MapSharing.died(owner);
                // A separate real packet is an ordered barrier: observing this random UUID proves earlier packets arrived.
                MapTransport.sendBadge(peer,new NameBadges.Packet(List.of(owner.getUUID(),peer.getUUID(),sentinel)));
                var state=new JsonObject();state.addProperty("stage",current);state.addProperty("enabled",enabled);state.addProperty("barrier",sentinel.toString());state.addProperty("world",MapSharing.worldId(server));state.addProperty("host",owner.getUUID().toString());
                write(shared(mc,"survival-lan-state.json"),state);
            }catch(Throwable error){mc.execute(()->finish(mc,error));}finally{queued=false;}
        });
    }
    private static void guest(Minecraft mc)throws Exception{
        if(!connected){
            Path port=shared(mc,"survival-lan-port.txt");if(!Files.exists(port)||mc.getOverlay()!=null)return;
            String address="127.0.0.1:"+Integer.parseInt(Files.readString(port).trim());
            ConnectScreen.startConnecting(new TitleScreen(),mc,ServerAddress.parseString(address),new ServerData("Survival LAN fixture",address,ordinary?ServerData.Type.OTHER:ServerData.Type.LAN),false,null);
            connected=true;return;
        }
        if(mc.player==null||mc.level==null)return;
        check(mc.getSingleplayerServer()==null&&!SocialLogin.trustedClientSession(),"Guest unexpectedly entered a local world or authenticated private room");
        if(ordinary&&lastGuestStage==3){var ack=new JsonObject();ack.addProperty("stage",3);ack.addProperty("ordinaryReady",true);write(shared(mc,"survival-lan-ack.json"),ack);}
        JsonObject state=read(shared(mc,"survival-lan-state.json"));if(!state.has("stage"))return;
        int current=state.get("stage").getAsInt();if(current<=lastGuestStage||!NameBadges.known(UUID.fromString(state.get("barrier").getAsString())))return;
        boolean enabled=state.get("enabled").getAsBoolean();UUID owner=UUID.fromString(state.get("host").getAsString());
        // Local preferences must never replace the authoritative host state.
        UnboxClient.set("veinminer",!enabled);UnboxClient.set("fallingtree",!enabled);UnboxClient.set("gravestones",!enabled);
        if(current<4){
            check(mc.getCurrentServer().isLan(),"Fixture connection was not classified by native LAN metadata");
            check(SurvivalHostMods.hostKnown()&&GraveKeeper.hostKnown(),"Native host state packet missing");
            check(SurvivalHostMods.hostEnabled("veinminer")==enabled&&SurvivalHostMods.hostEnabled("fallingtree")==enabled&&GraveKeeper.hostEnabled()==enabled,"Host enable/disable state was not received authoritatively");
            check(WorldMap.remoteWorld.equals(state.get("world").getAsString()),"Stable world UUID did not cross the native packet channel");
            var death=WorldMap.DEATHS.get(owner);check(death!=null&&death.player().equals(owner),"Same-room host death marker missing on guest");
            check(!ClientFeaturePolicy.allowed("veinminer")&&!ClientFeaturePolicy.allowed("fallingtree")&&!ClientFeaturePolicy.allowed("gravestones"),"Guest gained host configuration authority");
            CHECKS.add("LAN stage "+current+": real packets report all three host features "+(enabled?"enabled":"disabled")+", world UUID and teammate marker; guest settings cannot override host");
        }else{
            check(!mc.getCurrentServer().isLan(),"Ordinary-server reconnect retained LAN metadata");
            check(!SurvivalHostMods.hostKnown()&&!SurvivalHostMods.hostEnabled("veinminer")&&!SurvivalHostMods.hostEnabled("fallingtree"),"Ordinary server state enabled survival host features");
            check(!GraveKeeper.hostKnown()&&!GraveKeeper.hostEnabled(),"Ordinary server state enabled gravestones");
            check(WorldMap.remoteWorld.isEmpty()&&!WorldMap.DEATHS.containsKey(owner),"Ordinary server injected a room UUID or non-team death marker");
            CHECKS.add("Reconnecting with ordinary-server metadata rejects the same real host-state/world/death packets after an ordered native-packet barrier");
        }
        lastGuestStage=current;
        var ack=new JsonObject();ack.addProperty("stage",current);
        if(current==4){writeResult(mc,null);write(shared(mc,"survival-lan-ack.json"),ack);finished=true;mc.stop();return;}
        write(shared(mc,"survival-lan-ack.json"),ack);
        if(current==3){ordinary=true;connected=false;mc.disconnect(new TitleScreen(),false);}
    }
    private static JsonObject read(Path path)throws Exception{return Files.exists(path)?JsonParser.parseString(Files.readString(path)).getAsJsonObject():new JsonObject();}
    private static void write(Path path,JsonObject value)throws Exception{Files.createDirectories(path.getParent());Path temp=path.resolveSibling(path.getFileName()+".tmp");Files.writeString(temp,JSON.toJson(value));Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
    private static void writeResult(Minecraft mc,Throwable error)throws Exception{
        var result=new JsonObject();result.addProperty("ok",error==null);result.addProperty("role",ROLE);result.addProperty("scope","same-machine isolated offline TCP fixture; not two-home testing or official account authentication");result.add("checks",JSON.toJsonTree(CHECKS));if(error!=null)result.addProperty("error",error.toString());
        write(mc.gameDirectory.toPath().resolve("logs/survival-lan-"+ROLE+".json"),result);
    }
    private static void finish(Minecraft mc,Throwable error){if(finished)return;finished=true;try{writeResult(mc,error);}catch(Exception e){System.err.println("[Unbox] Survival LAN report failed: "+e);}mc.stop();}
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
