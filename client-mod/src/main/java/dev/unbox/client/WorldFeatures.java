package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;

/** Host-authoritative features saved in the world, independently of launcher profiles. */
public final class WorldFeatures {
    private static final Set<String> IDS=Set.of("building","shulker","ghast");
    private static final Identifier SPEED=Identifier.fromNamespaceAndPath("unbox","ridden_ghast_speed");
    private static volatile MinecraftServer server;
    private static volatile Set<String> enabled=Set.of();
    private static volatile boolean remoteBuilding,remoteShulker,remoteGhast;
    private static String saved="";
    private static long revision=-1;
    private static int readiness=-1;
    private static volatile int ghastSpeed=200;
    private static final AsyncSettingsFile WRITER=new AsyncSettingsFile();
    public static void shutdown(){WRITER.finish();}
    public static boolean manages(String id){return IDS.contains(id);}
    public static boolean serverEnabled(String id){return server!=null&&server==Minecraft.getInstance().getSingleplayerServer()&&enabled.contains(id);}
    public static boolean remote(String id){return switch(id){case "building"->remoteBuilding;case "shulker"->remoteShulker;case "ghast"->remoteGhast;default->false;};}
    static void receive(boolean building,boolean shulker,boolean ghast){remoteBuilding=building;remoteShulker=shulker;remoteGhast=ghast;}
    public static boolean buildingClient(){var mc=Minecraft.getInstance();return mc.player!=null&&mc.player.isCreative()&&SurvivalHostMods.hostEnabled("building");}
    public static boolean buildingServer(ServerPlayer player){return player.isCreative()&&player.level().getServer()==server&&serverEnabled("building");}
    public static void tick(Minecraft mc){
        MinecraftServer next=mc.getSingleplayerServer();
        if(next==null){server=null;enabled=Set.of();saved="";return;}
        Path path=next.getWorldPath(LevelResource.ROOT).resolve("unbox-features.properties");
        if(server!=next){
            Properties p=new Properties();
            try{if(Files.exists(path))try(var in=Files.newInputStream(path)){p.load(in);}}
            catch(java.io.IOException e){System.err.println("[Unbox] World feature settings: "+e);}
            for(String id:IDS)UnboxClient.set(id,Boolean.parseBoolean(p.getProperty(id,"false")));
            UnboxClient.set("ghast.speed",p.getProperty("ghast.speed","200"));
            UnboxClient.set("ghast.wait",p.getProperty("ghast.wait","4"));
            UnboxClient.set("ghast.range",p.getProperty("ghast.range","15"));
            server=next;saved="";revision=-1;
        }
        int ready=0;for(String id:IDS)ready=ready*2+(ClientIntegrations.available(id)?1:0);
        if(revision==UnboxClient.revision()&&readiness==ready)return;
        readiness=ready;
        revision=UnboxClient.revision();
        Set<String> wanted=new HashSet<>();for(String id:IDS)if(UnboxClient.configured(id)&&ClientIntegrations.available(id))wanted.add(id);
        enabled=Set.copyOf(wanted);
        int speed=UnboxClient.number("ghast.speed",200,100,500);
        String state=IDS.stream().sorted().map(id->id+"="+enabled.contains(id)).reduce("",(a,b)->a+b+"\n")+"ghast.speed="+speed+"\nghast.wait="+UnboxClient.number("ghast.wait",4,1,30)+"\nghast.range="+UnboxClient.number("ghast.range",15,1,15)+"\n";
        if(!saved.equals(state)){WRITER.save(path,state);saved=state;}
        ghastSpeed=speed;
    }
    /** Runs only for a loaded ghast, on its server tick; no scan of unrelated entities. */
    public static double updateGhast(HappyGhast ghast,double previous){
        if(!(ghast.level() instanceof net.minecraft.server.level.ServerLevel level)||level.getServer()!=server)return previous;
        double amount=enabled.contains("ghast")&&ghast.getControllingPassenger() instanceof Player?ghastSpeed/100.0-1:0;
        if(previous==amount)return amount;
        var attribute=ghast.getAttribute(Attributes.FLYING_SPEED);if(attribute==null)return previous;
        attribute.removeModifier(SPEED);
        if(amount!=0)attribute.addTransientModifier(new AttributeModifier(SPEED,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        return amount;
    }
}
