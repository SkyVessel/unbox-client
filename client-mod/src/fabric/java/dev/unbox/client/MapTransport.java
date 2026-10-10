package dev.unbox.client;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.level.ServerPlayer;
final class MapTransport {
 static void init(){
  ServerLifecycleEvents.SERVER_STARTED.register(MapSharing::started);ServerLifecycleEvents.SERVER_STOPPED.register(MapSharing::stopped);
  ServerPlayConnectionEvents.JOIN.register((h,sender,s)->MapSharing.joined(h.getPlayer(),s));
  PayloadTypeRegistry.clientboundPlay().register(MapSharing.DeathPacket.TYPE,MapSharing.DeathPacket.CODEC);
  ClientPlayNetworking.registerGlobalReceiver(MapSharing.DeathPacket.TYPE,(p,c)->c.client().execute(()->WorldMap.death(p.death())));
  ServerLivingEntityEvents.AFTER_DEATH.register((entity,source)->{if(entity instanceof ServerPlayer p)MapSharing.died(p);});
 }
 static boolean canSend(ServerPlayer p){return ServerPlayNetworking.canSend(p,MapSharing.DeathPacket.TYPE);}
 static void send(ServerPlayer p,MapSharing.DeathPacket d){ServerPlayNetworking.send(p,d);}
}
