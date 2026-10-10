package dev.unbox.client;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;
import java.nio.file.*;
import java.util.concurrent.*;
import com.google.gson.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Only the actual server emits deaths. Clients cannot manufacture teammate markers. */
final class MapSharing {
 record DeathPacket(WorldMap.Death death) implements CustomPacketPayload {
  static final Type<DeathPacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("unbox","death_marker"));
  static final StreamCodec<RegistryFriendlyByteBuf,DeathPacket> CODEC=CustomPacketPayload.codec((p,b)->{var d=p.death;b.writeUUID(d.player());b.writeUtf(d.name(),64);b.writeUtf(d.dimension(),256);b.writeInt(d.x());b.writeInt(d.y());b.writeInt(d.z());b.writeLong(d.time());},b->new DeathPacket(new WorldMap.Death(b.readUUID(),b.readUtf(64),b.readUtf(256),b.readInt(),b.readInt(),b.readInt(),b.readLong())));
  public Type<DeathPacket> type(){return TYPE;}
 }
 private static final Map<MinecraftServer,List<WorldMap.Death>> HISTORY=new IdentityHashMap<>();
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("unbox-map-store",0).factory());
 private static Path file(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("unbox-map-deaths.json");}
 private static void remember(MinecraftServer s,WorldMap.Death d){var history=HISTORY.computeIfAbsent(s,k->new ArrayList<>());history.add(d);while(history.size()>64)history.removeFirst();String json=new Gson().toJson(history);Path target=file(s);IO.execute(()->{try{Path tmp=target.resolveSibling("unbox-map-deaths.tmp");Files.writeString(tmp,json);Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){System.err.println("[Unbox] Could not save map death markers");}});}
 static void init(){MapTransport.init();}
 static void started(MinecraftServer s){if(!s.isSingleplayer())return;Path p=file(s);IO.execute(()->{try{if(!Files.exists(p)||Files.size(p)>65536)return;var loaded=new Gson().fromJson(Files.readString(p),WorldMap.Death[].class);if(loaded==null||loaded.length>64)return;s.execute(()->{var current=HISTORY.computeIfAbsent(s,k->new ArrayList<>());for(var d:loaded)if(d!=null&&d.player()!=null&&d.name()!=null&&d.name().length()<=64&&d.dimension()!=null&&Identifier.tryParse(d.dimension())!=null&&current.size()<64)current.add(d);for(var peer:s.getPlayerList().getPlayers())joined(peer,s);});}catch(Exception e){System.err.println("[Unbox] Map history unavailable");}});}
 static void stopped(MinecraftServer s){HISTORY.remove(s);}
 static void joined(ServerPlayer peer,MinecraftServer s){if(s.isSingleplayer()&&MapTransport.canSend(peer))for(var d:HISTORY.getOrDefault(s,List.of()))MapTransport.send(peer,new DeathPacket(d));}
 static void died(ServerPlayer p){var server=p.level().getServer();if(server==null||!server.isSingleplayer())return;
  var d=new WorldMap.Death(p.getUUID(),p.getName().getString(),p.level().dimension().identifier().toString(),p.getBlockX(),p.getBlockY(),p.getBlockZ(),System.currentTimeMillis());remember(server,d);
  for(var peer:server.getPlayerList().getPlayers())if(MapTransport.canSend(peer))MapTransport.send(peer,new DeathPacket(d));
 }
}
