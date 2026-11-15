package dev.unbox.pvp.mixin;

import dev.unbox.unpvp.Latency;
import dev.unbox.unpvp.ConnectionDiagnostics;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.GenericFutureListener;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Observe timing only. Never cancel, replace or delay gameplay packets. */
@Mixin(NetworkManager.class)
public class LatencyMixin {
    @Inject(method="sendPacket(Lnet/minecraft/network/Packet;)V",at=@At("HEAD"))
    private void unbox$sent(Packet packet,CallbackInfo ci){Latency.sent((NetworkManager)(Object)this,packet);ConnectionDiagnostics.sent((NetworkManager)(Object)this,packet);}
    @Inject(method="sendPacket(Lnet/minecraft/network/Packet;Lio/netty/util/concurrent/GenericFutureListener;[Lio/netty/util/concurrent/GenericFutureListener;)V",at=@At("HEAD"))
    private void unbox$sentWithListener(Packet packet,GenericFutureListener listener,GenericFutureListener[] listeners,CallbackInfo ci){Latency.sent((NetworkManager)(Object)this,packet);ConnectionDiagnostics.sent((NetworkManager)(Object)this,packet);}
    @Inject(method="channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/Packet;)V",at=@At("HEAD"))
    private void unbox$received(ChannelHandlerContext context,Packet packet,CallbackInfo ci){Latency.received((NetworkManager)(Object)this,packet);ConnectionDiagnostics.received((NetworkManager)(Object)this,packet);}
}
