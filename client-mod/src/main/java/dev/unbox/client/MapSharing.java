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

/** Only the actual server emits deaths; history holds one latest death per fixed player UUID. */
final class MapSharing {
 record DeathPacket(WorldMap.Death death) implements CustomPacketPayload {
  static final Type<DeathPacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("unbox","death_marker"));
  static final StreamCodec<RegistryFriendlyByteBuf,DeathPacket> CODEC=CustomPacketPayload.codec((p,b)->{var d=p.death;b.writeUUID(d.player());b.writeUtf(d.name(),64);b.writeUtf(d.dimension(),256);b.writeInt(d.x());b.writeInt(d.y());b.writeInt(d.z());b.writeLong(d.time());},b->new DeathPacket(new WorldMap.Death(b.readUUID(),b.readUtf(64),b.readUtf(256),b.readInt(),b.readInt(),b.readInt(),b.readLong())));
  public Type<DeathPacket> type(){return TYPE;}
 }
 record WorldPacket(String id) implements CustomPacketPayload {
  static final Type<WorldPacket> TYPE=new Type<>(Identifier.fromNamespaceAndPath("unbox","map_world"));
  static final StreamCodec<RegistryFriendlyByteBuf,WorldPacket> CODEC=CustomPacketPayload.codec((p,b)->b.writeUtf(p.id,36),b->new WorldPacket(b.readUtf(36)));
  public Type<WorldPacket> type(){return TYPE;}
 }
 private static final Map<MinecraftServer,LinkedHashMap<UUID,WorldMap.Death>> HISTORY=new IdentityHashMap<>();
 private static final Set<MinecraftServer> READY=Collections.newSetFromMap(new IdentityHashMap<>());
 private static final Map<MinecraftServer,String> WORLD_IDS=new ConcurrentHashMap<>();
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("unbox-map-store",0).factory());
 private static Path file(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("unbox-map-deaths.json");}
 static String worldId(MinecraftServer server){return WORLD_IDS.getOrDefault(server,"");}
 private static void merge(Map<UUID,WorldMap.Death> history,WorldMap.Death death){
  var old=history.get(death.player());if(old==null||death.time()>=old.time()){history.remove(death.player());history.put(death.player(),death);}
  while(history.size()>64)history.remove(history.keySet().iterator().next());
 }
 private static void remember(MinecraftServer s,WorldMap.Death d){
  var history=HISTORY.computeIfAbsent(s,k->new LinkedHashMap<>());merge(history,d);if(READY.contains(s))save(s,history);
 }
 private static void save(MinecraftServer s,Map<UUID,WorldMap.Death> history){
  String json=new Gson().toJson(history.values());Path target=file(s);
  IO.execute(()->{try{Path tmp=target.resolveSibling("unbox-map-deaths.tmp");Files.writeString(tmp,json);try{Files.move(tmp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}}catch(Exception e){System.err.println("[Unbox] Could not save map death markers");}});
 }
 static void init(){MapTransport.init();}
 static void started(MinecraftServer s){
  if(!s.isSingleplayer())return;var history=new LinkedHashMap<UUID,WorldMap.Death>();HISTORY.put(s,history);Path root=s.getWorldPath(LevelResource.ROOT),p=file(s);
  IO.execute(()->{
   String id="";WorldMap.Death[] loaded=null;
   try{Path identity=root.resolve("unbox-map-world-id");if(Files.exists(identity)){if(Files.size(identity)<=64)id=UUID.fromString(Files.readString(identity).trim()).toString();}
    else{id=UUID.randomUUID().toString();Files.writeString(identity,id,StandardOpenOption.CREATE_NEW);}
   }catch(Exception e){System.err.println("[Unbox] Stable map world identity unavailable");}
   try{if(Files.exists(p)&&Files.size(p)<=65536)loaded=new Gson().fromJson(Files.readString(p),WorldMap.Death[].class);}catch(Exception e){System.err.println("[Unbox] Map history unavailable");}
   String stable=id;WorldMap.Death[] records=loaded;
   s.execute(()->{if(HISTORY.get(s)!=history)return;if(!stable.isEmpty())WORLD_IDS.put(s,stable);
    boolean changed=!history.isEmpty();
    if(records!=null&&records.length<=64)for(var death:records)if(WorldMap.validDeath(death))merge(history,death);
    READY.add(s);if(changed)save(s,history);
    for(var peer:s.getPlayerList().getPlayers())joined(peer,s);
   });
  });
 }
 static void stopped(MinecraftServer s){HISTORY.remove(s);WORLD_IDS.remove(s);READY.remove(s);}
 private static boolean visible(ServerPlayer peer,WorldMap.Death death,MinecraftServer server){
  if(peer.getUUID().equals(death.player()))return true;
  if(SocialBridge.guarded())return SocialBridge.allowed(peer.getUUID())&&SocialBridge.allowed(death.player());
  var source=server.getPlayerList().getPlayer(death.player());
  return source!=null; // All players admitted to an ordinary integrated LAN world share the room.
 }
 static void joined(ServerPlayer peer,MinecraftServer s){
  if(!s.isSingleplayer())return;GraveKeeper.joined(peer);SurvivalHostMods.joined(peer);
  String id=worldId(s);if(!id.isEmpty()&&MapTransport.canSendWorld(peer))MapTransport.sendWorld(peer,new WorldPacket(id));
  if(MapTransport.canSend(peer))for(var death:HISTORY.getOrDefault(s,new LinkedHashMap<>()).values())if(visible(peer,death,s))MapTransport.send(peer,new DeathPacket(death));
 }
 static void died(ServerPlayer p){var server=p.level().getServer();if(server==null||!server.isSingleplayer())return;
  var death=new WorldMap.Death(p.getUUID(),p.getName().getString(),p.level().dimension().identifier().toString(),p.getBlockX(),p.getBlockY(),p.getBlockZ(),System.currentTimeMillis());remember(server,death);
  for(var peer:server.getPlayerList().getPlayers())if(MapTransport.canSend(peer)&&visible(peer,death,server))MapTransport.send(peer,new DeathPacket(death));
 }
}
