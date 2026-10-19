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
    private static int ticks;
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
            server=next;saved="";ticks=0;
        }
        Set<String> wanted=new HashSet<>();for(String id:IDS)if(UnboxClient.configured(id)&&ClientIntegrations.available(id))wanted.add(id);
        enabled=Set.copyOf(wanted);
        int speed=UnboxClient.number("ghast.speed",200,100,500);
        String state=IDS.stream().sorted().map(id->id+"="+enabled.contains(id)).reduce("",(a,b)->a+b+"\n")+"ghast.speed="+speed+"\nghast.wait="+UnboxClient.number("ghast.wait",4,1,30)+"\nghast.range="+UnboxClient.number("ghast.range",15,1,15)+"\n";
        if(!saved.equals(state))try{Path tmp=path.resolveSibling("unbox-features.properties.tmp");Files.writeString(tmp,state);Files.move(tmp,path,StandardCopyOption.REPLACE_EXISTING);saved=state;}catch(java.io.IOException e){System.err.println("[Unbox] Could not save world features: "+e);}
        if(ticks++%20!=0)return;
        boolean active=enabled.contains("ghast");
        next.execute(()->{
            if(server!=next)return;
            for(var level:next.getAllLevels())for(var entity:level.getAllEntities())if(entity instanceof HappyGhast ghast){
                var attribute=ghast.getAttribute(Attributes.FLYING_SPEED);if(attribute==null)continue;
                double amount=active&&ghast.getControllingPassenger() instanceof Player?speed/100.0-1:0;
                var old=attribute.getModifier(SPEED);
                if(old!=null&&old.amount()==amount)continue;
                attribute.removeModifier(SPEED);
                if(amount!=0)attribute.addTransientModifier(new AttributeModifier(SPEED,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        });
    }
}
