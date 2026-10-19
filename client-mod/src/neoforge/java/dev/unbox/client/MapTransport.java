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
  Platform.bus.addListener((RegisterPayloadHandlersEvent e)->e.registrar("1").optional().playToServer(HappyGhastRecall.Request.TYPE,HappyGhastRecall.Request.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)HappyGhastRecall.receive(player);})));
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e)->net.minecraft.client.Minecraft.getInstance().execute(()->{WorldMap.disconnected();SurvivalHostMods.clearClientHost();}));
  NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.GameShuttingDownEvent e)->{WorldMap.shutdown();WorldFeatures.shutdown();});
  Platform.bus.addListener((RegisterPayloadHandlersEvent e)->e.registrar("1").optional().playToClient(MapSharing.WorldPacket.TYPE,MapSharing.WorldPacket.CODEC));
  Platform.bus.addListener((RegisterClientPayloadHandlersEvent e)->e.register(MapSharing.WorldPacket.TYPE,(p,c)->WorldMap.receiveWorld(p)));
  Platform.bus.addListener((RegisterPayloadHandlersEvent e)->e.registrar("1").optional().playToClient(NameBadges.Packet.TYPE,NameBadges.Packet.CODEC));
  Platform.bus.addListener((RegisterClientPayloadHandlersEvent e)->e.register(NameBadges.Packet.TYPE,(p,c)->NameBadges.receive(p)));
  Platform.bus.addListener((RegisterPayloadHandlersEvent e)->e.registrar("1").optional().playToClient(SurvivalHostMods.HostState.TYPE,SurvivalHostMods.HostState.CODEC));
  Platform.bus.addListener((RegisterClientPayloadHandlersEvent e)->e.register(SurvivalHostMods.HostState.TYPE,(p,c)->SurvivalHostMods.receive(p)));
  Platform.bus.addListener((RegisterPayloadHandlersEvent e)->e.registrar("1").optional().playToClient(GraveKeeper.HostState.TYPE,GraveKeeper.HostState.CODEC));
  Platform.bus.addListener((RegisterClientPayloadHandlersEvent e)->e.register(GraveKeeper.HostState.TYPE,(p,c)->GraveKeeper.receive(p)));
  Platform.bus.addListener((RegisterPayloadHandlersEvent e)->e.registrar("1").optional().playToClient(MapSharing.DeathPacket.TYPE,MapSharing.DeathPacket.CODEC));
  Platform.bus.addListener((RegisterClientPayloadHandlersEvent e)->e.register(MapSharing.DeathPacket.TYPE,(p,c)->WorldMap.death(p.death())));
  NeoForge.EVENT_BUS.addListener((ServerStartedEvent e)->MapSharing.started(e.getServer()));
  NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e)->MapSharing.stopped(e.getServer()));
  NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer p)MapSharing.joined(p,p.level().getServer());});
  NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,(LivingDeathEvent e)->{if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)MapSharing.died(p);});
 }
 static boolean canCallGhast(){var c=net.minecraft.client.Minecraft.getInstance().getConnection();return c!=null&&c.hasChannel(HappyGhastRecall.Request.TYPE);}
 static void callGhast(){net.minecraft.client.Minecraft.getInstance().getConnection().send(new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(new HappyGhastRecall.Request()));}
 static boolean canSendWorld(ServerPlayer p){return p.connection.hasChannel(MapSharing.WorldPacket.TYPE);}
 static void sendWorld(ServerPlayer p,MapSharing.WorldPacket packet){PacketDistributor.sendToPlayer(p,packet);}
 static boolean badgeCapable(ServerPlayer p){return p.connection.hasChannel(NameBadges.Packet.TYPE);}
 static void sendBadge(ServerPlayer p,NameBadges.Packet d){PacketDistributor.sendToPlayer(p,d);}
 static boolean canSend(ServerPlayer p){return p.connection.hasChannel(MapSharing.DeathPacket.TYPE);}
 static void send(ServerPlayer p,MapSharing.DeathPacket d){PacketDistributor.sendToPlayer(p,d);}
 static boolean canSendSurvivalHostState(ServerPlayer p){return p.connection.hasChannel(SurvivalHostMods.HostState.TYPE);}
 static void sendSurvivalHostState(ServerPlayer p,SurvivalHostMods.HostState state){PacketDistributor.sendToPlayer(p,state);}
 static boolean canSendHostState(ServerPlayer p){return p.connection.hasChannel(GraveKeeper.HostState.TYPE);}
 static void sendHostState(ServerPlayer p,GraveKeeper.HostState state){PacketDistributor.sendToPlayer(p,state);}
}
