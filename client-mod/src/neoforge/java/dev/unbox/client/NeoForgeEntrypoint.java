package dev.unbox.client;
@net.neoforged.fml.common.Mod(value="unbox",dist=net.neoforged.api.distmarker.Dist.CLIENT)
public final class NeoForgeEntrypoint {
 public NeoForgeEntrypoint(net.neoforged.bus.api.IEventBus bus){Platform.bus=bus;UnboxClient.initialize();}
}
