package dev.unbox.client;
import dev.unbox.client.mixin.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.login.*;
import net.minecraft.network.protocol.login.custom.*;
/** Native vanilla login queries, before NeoForge's configuration handshake. No Fabric classes. */
public final class LoginTransport {
 private static final int BASE=0x55420000;
 private static final Map<Identifier,LoginHooks.ServerReceiver> SERVER=new HashMap<>();
 private static final Map<Identifier,LoginHooks.ClientReceiver> CLIENT=new HashMap<>();
 private static final List<LoginHooks.Start> START=new ArrayList<>();
 private static final List<BiConsumer<ServerLoginPacketListenerImpl,MinecraftServer>> CLOSE_SERVER=new ArrayList<>();
 private static final List<BiConsumer<ClientHandshakePacketListenerImpl,Minecraft>> CLOSE_CLIENT=new ArrayList<>();
 private static final Map<ServerLoginPacketListenerImpl,Gate> GATES=new ConcurrentHashMap<>();
 private static final class Gate {final Map<Integer,Identifier> pending=new ConcurrentHashMap<>();final List<CompletableFuture<?>> work=new CopyOnWriteArrayList<>();int serial;boolean started;}
 public record Query(Identifier id,byte[] bytes) implements CustomQueryPayload {public void write(FriendlyByteBuf b){b.writeBytes(bytes);}}
 public record Answer(byte[] bytes) implements CustomQueryAnswerPayload {public void write(FriendlyByteBuf b){b.writeBytes(bytes);}}
 public static byte[] read(FriendlyByteBuf b){if(b.readableBytes()>1_048_576)throw new IllegalArgumentException("Oversized login payload");byte[] bytes=new byte[b.readableBytes()];b.readBytes(bytes);return bytes;}
 public static boolean ours(int id){return (id&0xffff0000)==BASE;}
 public static boolean ours(Identifier id){return CLIENT.containsKey(id);}
 public static void onStart(LoginHooks.Start f){START.add(f);}public static void server(Identifier id,LoginHooks.ServerReceiver f){SERVER.put(id,f);}public static void client(Identifier id,LoginHooks.ClientReceiver f){CLIENT.put(id,f);}
 public static void onServerDisconnect(BiConsumer<ServerLoginPacketListenerImpl,MinecraftServer> f){CLOSE_SERVER.add(f);}public static void onClientDisconnect(BiConsumer<ClientHandshakePacketListenerImpl,Minecraft> f){CLOSE_CLIENT.add(f);}
 public static LoginHooks.Sender sender(ServerLoginPacketListenerImpl h){return (id,data)->{try{Gate g=GATES.get(h);if(g==null||!h.isAcceptingMessages())return;int transaction; synchronized(g){if(g.serial>=65535)throw new IllegalStateException("Login query limit");transaction=BASE|++g.serial;g.pending.put(transaction,id);}((SocialLoginAccess)h).unboxConnection().send(new ClientboundCustomQueryPacket(transaction,new Query(id,read(data))));}finally{data.release();}};}
 public static boolean ready(ServerLoginPacketListenerImpl h,MinecraftServer s){if(!SocialBridge.privateAuthentication()||((SocialLoginAccess)h).unboxConnection().isMemoryConnection())return true;
  Gate g=GATES.computeIfAbsent(h,k->new Gate());if(!g.started){g.started=true;for(var f:START)f.run(h,s,sender(h),g.work::add);return false;}
  for(var f:g.work)if(f.isCompletedExceptionally()){h.disconnect(Component.literal("Private world preparation failed"));return false;}
  g.work.removeIf(CompletableFuture::isDone);return g.pending.isEmpty()&&g.work.isEmpty();
 }
 public static void answer(ServerLoginPacketListenerImpl h,MinecraftServer s,ServerboundCustomQueryAnswerPacket p){Gate g=GATES.get(h);Identifier id=g==null?null:g.pending.get(p.transactionId());if(id==null){h.disconnect(Component.literal("Unexpected Unbox login reply"));return;}
  FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.wrappedBuffer(p.payload() instanceof Answer a?a.bytes:new byte[0]));try{var receiver=SERVER.get(id);if(receiver==null)throw new IllegalStateException();receiver.receive(s,h,p.payload() instanceof Answer,b,g.work::add,sender(h));}catch(Exception e){h.disconnect(Component.literal("Invalid Unbox login reply"));}finally{b.release();g.pending.remove(p.transactionId());}
 }
 public static void query(ClientHandshakePacketListenerImpl h,ClientboundCustomQueryPacket p,Query q){var m=Minecraft.getInstance();var connection=((SocialClientLoginAccess)h).unboxConnection();FriendlyByteBuf b=new FriendlyByteBuf(Unpooled.wrappedBuffer(q.bytes));
  try{CLIENT.get(q.id).receive(m,h,b,(id,data)->{throw new UnsupportedOperationException();}).whenComplete((reply,error)->{try{if(connection.isConnected())connection.send(new ServerboundCustomQueryAnswerPacket(p.transactionId(),error!=null||reply==null?null:new Answer(read(reply))));}finally{if(reply!=null)reply.release();}});}catch(Exception e){connection.disconnect(Component.literal("Private world verification failed"));}finally{b.release();}
 }
 public static void finish(ServerLoginPacketListenerImpl h){GATES.remove(h);}
 public static void close(ServerLoginPacketListenerImpl h,MinecraftServer s){GATES.remove(h);for(var f:CLOSE_SERVER)f.accept(h,s);}
 public static void close(ClientHandshakePacketListenerImpl h){for(var f:CLOSE_CLIENT)f.accept(h,Minecraft.getInstance());}
}
