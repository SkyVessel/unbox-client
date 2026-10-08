package dev.unbox.client.mixin;
import dev.unbox.client.SocialBridge;
import io.netty.channel.*;
import net.minecraft.server.network.ServerConnectionListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(ServerConnectionListener.class)
public class SocialNetworkMixin {
 @ModifyArg(method="startTcpServerListener",at=@At(value="INVOKE",target="Lio/netty/bootstrap/ServerBootstrap;childHandler(Lio/netty/channel/ChannelHandler;)Lio/netty/bootstrap/ServerBootstrap;",remap=false))
 private ChannelHandler unboxHandler(ChannelHandler h){SocialBridge.handler(h);return h;}
 @ModifyArg(method="startTcpServerListener",at=@At(value="INVOKE",target="Lio/netty/bootstrap/ServerBootstrap;group(Lio/netty/channel/EventLoopGroup;)Lio/netty/bootstrap/ServerBootstrap;",remap=false))
 private EventLoopGroup unboxGroup(EventLoopGroup g){SocialBridge.group(g);return g;}
}
