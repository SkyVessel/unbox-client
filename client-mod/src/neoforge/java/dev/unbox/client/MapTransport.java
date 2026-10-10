package dev.unbox.client;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;
final class MapTransport {
 static void init(){
  Platform.bus.addListener((RegisterPayloadHandlersEvent e)->e.registrar("1").optional().playToClient(MapSharing.DeathPacket.TYPE,MapSharing.DeathPacket.CODEC));
  Platform.bus.addListener((RegisterClientPayloadHandlersEvent e)->e.register(MapSharing.DeathPacket.TYPE,(p,c)->WorldMap.death(p.death())));
  NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->MapSharing.started(e.getServer()));
  NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->MapSharing.stopped(e.getServer()));
  NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer p)MapSharing.joined(p,p.level().getServer());});
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,(LivingDeathEvent e)->{if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)MapSharing.died(p);});
 }
 static boolean canSend(ServerPlayer p){return p.connection.hasChannel(MapSharing.DeathPacket.TYPE);}
 static void send(ServerPlayer p,MapSharing.DeathPacket d){PacketDistributor.sendToPlayer(p,d);}
}
