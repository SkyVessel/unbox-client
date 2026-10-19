package dev.unbox.client;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
final class MapTransport {
 static void init(){
  PayloadTypeRegistry.serverboundPlay().register(HappyGhastRecall.Request.TYPE,HappyGhastRecall.Request.CODEC);
  ServerPlayNetworking.registerGlobalReceiver(HappyGhastRecall.Request.TYPE,(p,c)->c.server().execute(()->HappyGhastRecall.receive(c.player())));
  ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->client.execute(()->{WorldMap.disconnected();SurvivalHostMods.clearClientHost();}));
  ClientLifecycleEvents.CLIENT_STOPPING.register(client->{WorldMap.shutdown();WorldFeatures.shutdown();});
  PayloadTypeRegistry.clientboundPlay().register(MapSharing.WorldPacket.TYPE,MapSharing.WorldPacket.CODEC);
  ClientPlayNetworking.registerGlobalReceiver(MapSharing.WorldPacket.TYPE,(p,c)->c.client().execute(()->WorldMap.receiveWorld(p)));
  PayloadTypeRegistry.clientboundPlay().register(NameBadges.Packet.TYPE,NameBadges.Packet.CODEC);
  ClientPlayNetworking.registerGlobalReceiver(NameBadges.Packet.TYPE,(p,c)->c.client().execute(()->NameBadges.receive(p)));
  PayloadTypeRegistry.clientboundPlay().register(SurvivalHostMods.HostState.TYPE,SurvivalHostMods.HostState.CODEC);
  ClientPlayNetworking.registerGlobalReceiver(SurvivalHostMods.HostState.TYPE,(p,c)->c.client().execute(()->SurvivalHostMods.receive(p)));
  PayloadTypeRegistry.clientboundPlay().register(GraveKeeper.HostState.TYPE,GraveKeeper.HostState.CODEC);
  ClientPlayNetworking.registerGlobalReceiver(GraveKeeper.HostState.TYPE,(p,c)->c.client().execute(()->GraveKeeper.receive(p)));
  ServerLifecycleEvents.SERVER_STARTED.register(MapSharing::started);ServerLifecycleEvents.SERVER_STOPPED.register(MapSharing::stopped);
  ServerPlayConnectionEvents.JOIN.register((h,sender,s)->MapSharing.joined(h.getPlayer(),s));
  PayloadTypeRegistry.clientboundPlay().register(MapSharing.DeathPacket.TYPE,MapSharing.DeathPacket.CODEC);
  ClientPlayNetworking.registerGlobalReceiver(MapSharing.DeathPacket.TYPE,(p,c)->c.client().execute(()->WorldMap.death(p.death())));
  ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{if(entity instanceof ServerPlayer p)MapSharing.died(p);});
 }
 static boolean canCallGhast(){return ClientPlayNetworking.canSend(HappyGhastRecall.Request.TYPE);}
 static void callGhast(){ClientPlayNetworking.send(new HappyGhastRecall.Request());}
 static boolean canSendWorld(ServerPlayer p){return ServerPlayNetworking.canSend(p,MapSharing.WorldPacket.TYPE);}
 static void sendWorld(ServerPlayer p,MapSharing.WorldPacket packet){ServerPlayNetworking.send(p,packet);}
 static boolean badgeCapable(ServerPlayer p){return ServerPlayNetworking.canSend(p,NameBadges.Packet.TYPE);}
 static void sendBadge(ServerPlayer p,NameBadges.Packet d){ServerPlayNetworking.send(p,d);}
 static boolean canSend(ServerPlayer p){return ServerPlayNetworking.canSend(p,MapSharing.DeathPacket.TYPE);}
 static void send(ServerPlayer p,MapSharing.DeathPacket d){ServerPlayNetworking.send(p,d);}
 static boolean canSendSurvivalHostState(ServerPlayer p){return ServerPlayNetworking.canSend(p,SurvivalHostMods.HostState.TYPE);}
 static void sendSurvivalHostState(ServerPlayer p,SurvivalHostMods.HostState state){ServerPlayNetworking.send(p,state);}
 static boolean canSendHostState(ServerPlayer p){return ServerPlayNetworking.canSend(p,GraveKeeper.HostState.TYPE);}
 static void sendHostState(ServerPlayer p,GraveKeeper.HostState state){ServerPlayNetworking.send(p,state);}
}
