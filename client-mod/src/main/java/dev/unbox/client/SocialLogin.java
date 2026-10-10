package dev.unbox.client;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.unbox.client.mixin.SocialLoginAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.*;
import java.util.concurrent.*;

/** Per-room challenge response inside Minecraft's encrypted login connection. */
public final class SocialLogin {
 private static final Identifier CHANNEL=Identifier.fromNamespaceAndPath("unbox","private_login_v2");
 public record Grant(String person,UUID uuid,String name,String secret,String room,long expires){}
 private record Pending(Grant grant,String nonce,String keyHash){}
 private static final Map<String,Grant> GRANTS=new ConcurrentHashMap<>();
 private static final Map<ServerLoginPacketListenerImpl,Pending> QUERIES=Collections.synchronizedMap(new WeakHashMap<>());
 private static final Set<ServerLoginPacketListenerImpl> VERIFIED=Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
 private static final Map<ServerLoginPacketListenerImpl,UUID> CLAIMS=Collections.synchronizedMap(new WeakHashMap<>());
 public static void claim(ServerLoginPacketListenerImpl h,UUID id){CLAIMS.put(h,id);}
 private static final Map<ClientHandshakePacketListenerImpl,String> KEYS=Collections.synchronizedMap(new WeakHashMap<>());
 private static volatile Grant joining;
 private static final Set<ClientHandshakePacketListenerImpl> TRUSTED=Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<>()));
 public static boolean trustedHost(ClientHandshakePacketListenerImpl h){return TRUSTED.contains(h);}
 public static String keyHash(PublicKey key)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getEncoded()));}
 public static byte[] proof(String secret,String side,String room,String person,String nonce,String key)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(HexFormat.of().parseHex(secret),"HmacSHA256"));return m.doFinal(("unbox-v2|"+side+"|"+room+"|"+person+"|"+nonce+"|"+key).getBytes(StandardCharsets.UTF_8));}
 public static void captureKey(ClientHandshakePacketListenerImpl handler,PublicKey key)throws Exception{KEYS.put(handler,keyHash(key));}
 public static boolean verified(ServerLoginPacketListenerImpl h){return VERIFIED.contains(h);}
 public static String grant(String person,UUID uuid,String name,String room){if(!person.matches("[a-f0-9-]{36}")||!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException();String secret=UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-","");GRANTS.put(person,new Grant(person,uuid,name,secret,room,System.currentTimeMillis()+300000));return secret;}
 public static JsonObject secrets(){JsonObject o=new JsonObject();for(var g:GRANTS.values())o.addProperty(g.person,g.secret);return o;}
 public static void retain(Set<UUID> ids){GRANTS.values().removeIf(g->!ids.contains(g.uuid));}
 public static void clearHost(){GRANTS.clear();QUERIES.clear();VERIFIED.clear();CLAIMS.clear();}
 public static void prepareJoin(JsonObject invite,UUID uuid,String name){JsonObject room=invite.getAsJsonObject("room");String secret=SocialBridge.text(invite,"join_secret");if(!secret.matches("[a-f0-9]{64}")||!room.has("protocol")||room.get("protocol").getAsInt()!=2)throw new IllegalArgumentException("Update Unbox on both computers");joining=new Grant(SocialBridge.text(invite,"receiver"),uuid,name,secret,SocialBridge.text(room,"id"),invite.get("expires").getAsLong());}
 public static void init(){
  LoginTransport.onStart((h,server,sender,sync)->{
   if(!SocialBridge.privateAuthentication()||((SocialLoginAccess)h).unboxConnection().isMemoryConnection())return;
   try{UUID claimed=CLAIMS.remove(h);Grant g=GRANTS.values().stream().filter(x->x.uuid.equals(claimed)&&x.expires>System.currentTimeMillis()).findFirst().orElse(null);if(g==null){h.disconnect(Component.literal("Ask your friend for a new Unbox invitation."));return;}
    String nonce=UUID.randomUUID().toString(),key=keyHash(server.getKeyPair().getPublic());QUERIES.put(h,new Pending(g,nonce,key));FriendlyByteBuf out=new FriendlyByteBuf(Unpooled.buffer());out.writeUtf(g.room,40);out.writeUtf(g.person,40);out.writeUtf(nonce,40);out.writeByteArray(proof(g.secret,"host",g.room,g.person,nonce,key));sender.sendPacket(CHANNEL,out);
   }catch(Exception e){h.disconnect(Component.literal("Private world verification failed."));}
  });
  LoginTransport.server(CHANNEL,(server,h,understood,buf,sync,sender)->{
   Pending q=QUERIES.remove(h);try{if(q==null||!understood||q.grant.expires<=System.currentTimeMillis()||GRANTS.get(q.grant.person)!=q.grant||!MessageDigest.isEqual(buf.readByteArray(32),proof(q.grant.secret,"guest",q.grant.room,q.grant.person,q.nonce,q.keyHash))||buf.isReadable())throw new IllegalArgumentException();
    ((SocialLoginAccess)h).unboxProfile(new GameProfile(q.grant.uuid,q.grant.name));VERIFIED.add(h);
    // Invitation proves identity; retrieve signed textures for that exact UUID separately.
    // This runs outside the render/server tick and is awaited before login completes.
    sync.waitFor(CompletableFuture.supplyAsync(()->{
     try{return server.services().sessionService().fetchProfile(q.grant.uuid,true);}
     catch(Exception ignored){return null;}
    }).completeOnTimeout(null,8,TimeUnit.SECONDS).thenAccept(result->{
     if(result!=null&&q.grant.uuid.equals(result.profile().id()))
      ((SocialLoginAccess)h).unboxProfile(new GameProfile(q.grant.uuid,q.grant.name,result.profile().properties()));
    }));EnvironmentSync.begin(h,sync);
   }catch(Exception e){h.disconnect(Component.literal("Invalid or expired Unbox invitation."));}
  });
  LoginTransport.client(CHANNEL,(mc,h,buf,listener)->{
   try{Grant g=joining;String room=buf.readUtf(40),person=buf.readUtf(40),nonce=buf.readUtf(40),key=KEYS.get(h);byte[] signed=buf.readByteArray(32);
    if(g==null||key==null||g.expires<=System.currentTimeMillis()||!g.room.equals(room)||!g.person.equals(person)||!g.uuid.equals(mc.getUser().getProfileId())||!g.name.equals(mc.getUser().getName())||buf.isReadable()||!MessageDigest.isEqual(signed,proof(g.secret,"host",room,person,nonce,key)))return CompletableFuture.completedFuture(null);
    FriendlyByteBuf out=new FriendlyByteBuf(Unpooled.buffer());out.writeByteArray(proof(g.secret,"guest",room,person,nonce,key));joining=null;KEYS.remove(h);TRUSTED.add(h);return CompletableFuture.completedFuture(out);
   }catch(Exception e){return CompletableFuture.completedFuture(null);}
  });
  LoginTransport.onServerDisconnect((h,server)->{QUERIES.remove(h);VERIFIED.remove(h);CLAIMS.remove(h);});
 }
}
