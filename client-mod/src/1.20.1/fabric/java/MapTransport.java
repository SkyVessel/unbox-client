package dev.unbox.client;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
final class MapTransport {
 static <T> void sendPayload(ServerPlayer p,T value,net.minecraft.resources.ResourceLocation id,LegacyCodec<net.minecraft.network.FriendlyByteBuf,T> codec){var b=PacketByteBufs.create();codec.encode(b,value);ServerPlayNetworking.send(p,id,b);}
 static void init(){
  PayloadTypeRegistry.serverboundPlay().register(HappyGhastRecall.Request.TYPE,HappyGhastRecall.Request.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(HappyGhastRecall.Request.TYPE,(p,c)->c.server().execute(()->HappyGhastRecall.receive(c.player())));
  ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{WorldMap.disconnected();SurvivalHostMods.clearClientHost();}));
  ClientLifecycleEvents.CLIENT_STOPPING.register(client->{WorldMap.shutdown();WorldFeatures.shutdown();JoinDiagnostics.shutdown();});
  ClientPlayNetworking.registerGlobalReceiver(MapSharing.WorldPacket.TYPE.id(),(client,handler,buffer,sender)->{var p=MapSharing.WorldPacket.CODEC.decode(buffer);client.execute(()->WorldMap.receiveWorld(p));});
  ClientPlayNetworking.registerGlobalReceiver(NameBadges.Packet.TYPE.id(),(client,handler,buffer,sender)->{var p=NameBadges.Packet.CODEC.decode(buffer);client.execute(()->NameBadges.receive(p));});
  ClientPlayNetworking.registerGlobalReceiver(SurvivalHostMods.HostState.TYPE.id(),(client,handler,buffer,sender)->{var p=SurvivalHostMods.HostState.CODEC.decode(buffer);client.execute(()->SurvivalHostMods.receive(p));});
  ClientPlayNetworking.registerGlobalReceiver(GraveKeeper.HostState.TYPE.id(),(client,handler,buffer,sender)->{var p=GraveKeeper.HostState.CODEC.decode(buffer);client.execute(()->GraveKeeper.receive(p));});
  ServerLifecycleEvents.SERVER_STARTED.register(MapSharing::started);ServerLifecycleEvents.SERVER_STOPPED.register(MapSharing::stopped);
  ServerPlayConnectionEvents.JOIN.register((h,sender,s)->MapSharing.joined(h.getPlayer(),s));
  ClientPlayNetworking.registerGlobalReceiver(MapSharing.DeathPacket.TYPE.id(),(client,handler,buffer,sender)->{var p=MapSharing.DeathPacket.CODEC.decode(buffer);client.execute(()->WorldMap.death(p.death()));});
  ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{if(entity instanceof ServerPlayer p)MapSharing.died(p);});
 }
 static boolean canCallGhast(){return ClientPlayNetworking.canSend(HappyGhastRecall.Request.TYPE);}
 static void callGhast(){ClientPlayNetworking.send(new HappyGhastRecall.Request());}
 static boolean canSendWorld(ServerPlayer p){return ServerPlayNetworking.canSend(p,MapSharing.WorldPacket.TYPE.id());}
 static void sendWorld(ServerPlayer p,MapSharing.WorldPacket packet){sendPayload(p,packet,packet.type().id(),MapSharing.WorldPacket.CODEC);}
 static boolean badgeCapable(ServerPlayer p){return ServerPlayNetworking.canSend(p,NameBadges.Packet.TYPE.id());}
 static void sendBadge(ServerPlayer p,NameBadges.Packet d){sendPayload(p,d,d.type().id(),NameBadges.Packet.CODEC);}
 static boolean canSend(ServerPlayer p){return ServerPlayNetworking.canSend(p,MapSharing.DeathPacket.TYPE.id());}
 static void send(ServerPlayer p,MapSharing.DeathPacket d){sendPayload(p,d,d.type().id(),MapSharing.DeathPacket.CODEC);}
 static boolean canSendSurvivalHostState(ServerPlayer p){return ServerPlayNetworking.canSend(p,SurvivalHostMods.HostState.TYPE.id());}
 static void sendSurvivalHostState(ServerPlayer p,SurvivalHostMods.HostState state){sendPayload(p,state,state.type().id(),SurvivalHostMods.HostState.CODEC);}
 static boolean canSendHostState(ServerPlayer p){return ServerPlayNetworking.canSend(p,GraveKeeper.HostState.TYPE.id());}
 static void sendHostState(ServerPlayer p,GraveKeeper.HostState state){sendPayload(p,state,state.type().id(),GraveKeeper.HostState.CODEC);}
}
