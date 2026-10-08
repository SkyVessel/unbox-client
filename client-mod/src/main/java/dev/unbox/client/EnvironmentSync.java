package dev.unbox.client;

import com.google.gson.*;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Streams only manifest-listed JARs over the already authenticated, encrypted login connection. */
public final class EnvironmentSync {
 private static final Identifier CHANNEL=Identifier.fromNamespaceAndPath("unbox","environment_v1");
 private static final int CHUNK=262144;
 private static final Path ROOT=net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir(),CACHE=Path.of(System.getProperty("unbox.sharedCache",ROOT.resolve("unbox-cache").toString()));
 private static final ExecutorService IO=Executors.newFixedThreadPool(2,Thread.ofVirtual().name("unbox-mod-sync-",0).factory());
 private static CompletableFuture<EnvironmentManifest.Snapshot> loaded;
 private static final Map<ServerLoginPacketListenerImpl,Session> HOSTS=new ConcurrentHashMap<>();
 private static final Map<ClientHandshakePacketListenerImpl,Download> GUESTS=new ConcurrentHashMap<>();
 private static volatile JsonObject invitation;
 private static volatile boolean restart;
 private record Session(EnvironmentManifest.Snapshot snapshot,long started,Map<String,Long> sizes,Map<String,Long> modified,java.util.concurrent.atomic.AtomicLong sent){}
 private static final class Download {JsonObject manifest,invite;List<EnvironmentManifest.Entry> files;int index;long offset;OutputStream out;Path temp;String hash;boolean different;long shown,received;}
 public static void prepare(JsonObject invite){invitation=invite.deepCopy();}
 public static boolean active(ServerLoginPacketListenerImpl h){var s=HOSTS.get(h);return s!=null&&System.currentTimeMillis()-s.started<600000;}
 public static void tick(Minecraft mc){if(restart){restart=false;mc.stop();}}
 private static FriendlyByteBuf buffer(){return new FriendlyByteBuf(Unpooled.buffer());}
 public static void init(){
  // Snapshot only once per game process, off the render/server threads.
  loaded=CompletableFuture.supplyAsync(()->{try{return EnvironmentManifest.scan(ROOT);}catch(Exception e){throw new CompletionException(e);}},IO);
  ServerLoginNetworking.registerGlobalReceiver(CHANNEL,(server,h,understood,b,sync,sender)->{
   try{if(!understood){HOSTS.remove(h);h.disconnect(Component.literal("Update Unbox on both computers to synchronize mods."));return;}Session s=HOSTS.get(h);if(s==null||!understood||!SocialLogin.verified(h)||!SocialBridge.allowed(((dev.unbox.client.mixin.SocialLoginAccess)h).unboxProfile().id())||!active(h))throw new IOException();int op=b.readUnsignedByte();
    if(op==0){if(b.isReadable())throw new IOException();HOSTS.remove(h);return;}
    if(op==2){HOSTS.remove(h);h.disconnect(Component.literal("Mods ready. Restarting Unbox to join…"));return;}
    String hash=b.readUtf(64);long offset=b.readLong();if(op!=1||b.isReadable()||!s.sizes.containsKey(hash)||offset<0||offset>=s.sizes.get(hash)||s.sent.addAndGet(CHUNK)>EnvironmentManifest.MAX_TOTAL+512L*CHUNK)throw new IOException();
    var future=CompletableFuture.runAsync(()->{try{Path file=s.snapshot.files().get(hash);if(!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS)||Files.size(file)!=s.sizes.get(hash)||Files.getLastModifiedTime(file).toMillis()!=s.modified.get(hash))throw new IOException("Host mods changed. Restart the host game.");byte[] data;try(var f=new RandomAccessFile(file.toFile(),"r")){f.seek(offset);data=new byte[(int)Math.min(CHUNK,f.length()-offset)];f.readFully(data);}FriendlyByteBuf out=buffer();out.writeByte(1);out.writeUtf(hash,64);out.writeLong(offset);out.writeByteArray(data);ServerLoginNetworking.getSender(h).sendPacket(CHANNEL,out);}catch(Exception e){HOSTS.remove(h);h.disconnect(Component.literal("Mod transfer interrupted. Ask your friend to restart and invite again."));}},IO);sync.waitFor(future);
   }catch(Exception e){HOSTS.remove(h);h.disconnect(Component.literal("Invalid mod synchronization request."));}
  });
  ClientLoginNetworking.registerGlobalReceiver(CHANNEL,(mc,h,b,listener)->{
   byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);
   return CompletableFuture.supplyAsync(()->{try{if(!h.isAcceptingMessages()||!SocialLogin.trustedHost(h))throw new IOException("Unauthenticated mod source");FriendlyByteBuf in=new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));try{int op=in.readUnsignedByte();Download d;
    if(op==0){if(GUESTS.containsKey(h))throw new IOException();d=new Download();d.invite=invitation;if(d.invite==null)throw new IOException();d.manifest=JsonParser.parseString(in.readUtf(200000)).getAsJsonObject();d.files=EnvironmentManifest.validate(d.manifest);if(in.isReadable())throw new IOException();Files.createDirectories(CACHE);var local=loaded.get();d.different=!EnvironmentManifest.hashes(local.manifest()).equals(EnvironmentManifest.hashes(d.manifest));if(!d.different){report(0,false,d.files.size());SocialBridge.message="Mods are up to date. Joining…";return buffer().writeByte(0);}GUESTS.put(h,d);
     // Reuse matching local files as well as earlier downloads; never move user originals.
     for(var e:d.files){Path cached=CACHE.resolve(e.hash()+".jar");if(!valid(cached,e)&&local.files().containsKey(e.hash())){Path tmp=Files.createTempFile(CACHE,"local-",".part");Files.copy(local.files().get(e.hash()),tmp,StandardCopyOption.REPLACE_EXISTING);if(valid(tmp,e))Files.move(tmp,cached,StandardCopyOption.REPLACE_EXISTING);else Files.deleteIfExists(tmp);}}
    }else if(op==1){d=GUESTS.get(h);if(d==null)throw new IOException();String hash=in.readUtf(64);long offset=in.readLong();byte[] chunk=in.readByteArray(CHUNK);var e=d.files.get(d.index);if(!hash.equals(d.hash)||offset!=d.offset||chunk.length==0||offset+chunk.length>e.size()||in.isReadable())throw new IOException();d.out.write(chunk);d.offset+=chunk.length;d.received+=chunk.length;if(d.offset==e.size()){d.out.close();d.out=null;if(!valid(d.temp,e))throw new IOException("Mod checksum mismatch");Files.move(d.temp,CACHE.resolve(e.hash()+".jar"),StandardCopyOption.REPLACE_EXISTING);d.temp=null;d.index++;}}
    else throw new IOException();
    while(d.index<d.files.size()){var e=d.files.get(d.index);if(d.out==null&&valid(CACHE.resolve(e.hash()+".jar"),e)){d.index++;continue;}if(d.out==null){d.hash=e.hash();d.offset=0;d.temp=Files.createTempFile(CACHE,"download-",".part");d.out=Files.newOutputStream(d.temp);}SocialBridge.message="Syncing mods "+(d.index+1)+" / "+d.files.size()+" · "+e.id()+" · "+(d.offset/1048576)+" / "+Math.max(1,e.size()/1048576)+" MB";if(System.currentTimeMillis()-d.shown>200){d.shown=System.currentTimeMillis();String text=SocialBridge.message;mc.execute(()->((dev.unbox.client.mixin.SocialClientLoginAccess)h).unboxStatus().accept(Component.literal(text)));}return buffer().writeByte(1).writeUtf(d.hash,64).writeLong(d.offset);}
    if(!h.isAcceptingMessages())throw new IOException("Connection cancelled");JsonObject request=new JsonObject();request.addProperty("hostId",SocialBridge.text(d.invite,"sender"));request.addProperty("inviteId",SocialBridge.text(d.invite,"id"));request.addProperty("accountUuid",mc.getUser().getProfileId().toString());request.addProperty("at",System.currentTimeMillis());request.add("manifest",d.manifest);Path tmp=ROOT.resolve("unbox-sync-request.tmp");Files.writeString(tmp,new Gson().toJson(request));Files.move(tmp,ROOT.resolve("unbox-sync-request.json"),StandardCopyOption.REPLACE_EXISTING);GUESTS.remove(h);report(d.received,true,d.files.size());SocialBridge.message="Mods synced. Restarting to join…";restart=true;return buffer().writeByte(2);
   }finally{in.release();}}catch(Exception e){cleanup(h);SocialBridge.message="Mod sync failed. Retry the invitation; completed files are cached.";mc.execute(()->((dev.unbox.client.mixin.SocialClientLoginAccess)h).unboxConnection().disconnect(Component.literal(SocialBridge.message)));return null;}},IO);
  });
  ServerLoginConnectionEvents.DISCONNECT.register((h,s)->HOSTS.remove(h));
  net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents.DISCONNECT.register((h,m)->{IO.submit(()->cleanup(h));});
 }
 private static void report(long bytes,boolean restart,int count)throws IOException{Files.createDirectories(ROOT.resolve("logs"));Files.writeString(ROOT.resolve("logs/environment-sync.json"),new Gson().toJson(Map.of("downloadedBytes",bytes,"restart",restart,"modCount",count)));}
 private static boolean valid(Path p,EnvironmentManifest.Entry e)throws Exception{return Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)&&Files.size(p)==e.size()&&EnvironmentManifest.hash(p).equals(e.hash());}
 private static void cleanup(ClientHandshakePacketListenerImpl h){Download d=GUESTS.remove(h);if(d!=null)try{if(d.out!=null)d.out.close();if(d.temp!=null)Files.deleteIfExists(d.temp);}catch(IOException ignored){}}
 public static void begin(ServerLoginPacketListenerImpl h,ServerLoginNetworking.LoginSynchronizer sync){
  var f=loaded.thenAcceptAsync(snapshot->{try{Map<String,Long>sizes=new HashMap<>(),modified=new HashMap<>();for(var e:EnvironmentManifest.validate(snapshot.manifest())){Path p=snapshot.files().get(e.hash());sizes.put(e.hash(),e.size());modified.put(e.hash(),Files.getLastModifiedTime(p).toMillis());}HOSTS.put(h,new Session(snapshot,System.currentTimeMillis(),sizes,modified,new java.util.concurrent.atomic.AtomicLong()));FriendlyByteBuf out=buffer();out.writeByte(0);out.writeUtf(new Gson().toJson(snapshot.manifest()),200000);ServerLoginNetworking.getSender(h).sendPacket(CHANNEL,out);}catch(Exception e){h.disconnect(Component.literal("Host mod list is unavailable. Restart the host game."));}},IO).exceptionally(e->{h.disconnect(Component.literal("Host mods could not be prepared for sharing."));return null;});sync.waitFor(f);
 }
}
