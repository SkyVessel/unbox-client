package dev.unbox.client;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import java.util.function.BiConsumer;
public final class LoginTransport {
 public static void onStart(LoginHooks.Start f){ServerLoginConnectionEvents.QUERY_START.register((h,s,p,b)->f.run(h,s,p::sendPacket,b::waitFor));}
 public static void server(Identifier id,LoginHooks.ServerReceiver f){ServerLoginNetworking.registerGlobalReceiver(id,(s,h,u,d,b,p)->f.receive(s,h,u,d,b::waitFor,sender(h)));}
 public static void client(Identifier id,LoginHooks.ClientReceiver f){ClientLoginNetworking.registerGlobalReceiver(id,(m,h,d,p)->f.receive(m,h,d,(key,data)->{throw new UnsupportedOperationException("Login clients reply to queries");}));}
 public static LoginHooks.Sender sender(ServerLoginPacketListenerImpl h){return ServerLoginNetworking.getSender(h)::sendPacket;}
 public static void onServerDisconnect(BiConsumer<ServerLoginPacketListenerImpl,MinecraftServer> f){ServerLoginConnectionEvents.DISCONNECT.register(f::accept);}
 public static void onClientDisconnect(BiConsumer<ClientHandshakePacketListenerImpl,net.minecraft.client.Minecraft> f){ClientLoginConnectionEvents.DISCONNECT.register(f::accept);}
 public static boolean ready(ServerLoginPacketListenerImpl h,MinecraftServer s){return true;}
}
