package dev.unbox.client;
import java.util.concurrent.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
/** Unbox protocol operations. Each build provides its own native transport. */
public final class LoginHooks {
 @FunctionalInterface public interface Sender {void sendPacket(Identifier id,FriendlyByteBuf data);}
 @FunctionalInterface public interface Barrier {void waitFor(CompletableFuture<?> task);}
 @FunctionalInterface public interface Start {void run(ServerLoginPacketListenerImpl h,MinecraftServer server,Sender sender,Barrier barrier);}
 @FunctionalInterface public interface ServerReceiver {void receive(MinecraftServer server,ServerLoginPacketListenerImpl h,boolean understood,FriendlyByteBuf data,Barrier barrier,Sender sender);}
 @FunctionalInterface public interface ClientReceiver {CompletableFuture<FriendlyByteBuf> receive(Minecraft client,ClientHandshakePacketListenerImpl h,FriendlyByteBuf data,Sender sender);}
}
