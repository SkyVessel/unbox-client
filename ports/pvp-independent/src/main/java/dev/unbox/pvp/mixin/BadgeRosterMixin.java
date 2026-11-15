package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.NameBadges;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(NetHandlerPlayClient.class)
public class BadgeRosterMixin {
 @Inject(method="handleCustomPayload",at=@At("HEAD"),cancellable=true)
 private void unboxRoster(S3FPacketCustomPayload packet,CallbackInfo ci){if(!NameBadges.CHANNEL.equals(packet.getChannelName()))return;PacketBuffer data=packet.getBufferData();if(data.readableBytes()<=32771){byte[] bytes=new byte[data.readableBytes()];data.getBytes(data.readerIndex(),bytes);Minecraft.getMinecraft().addScheduledTask(()->{if(Minecraft.getMinecraft().getNetHandler()!=(Object)this)return;PacketBuffer b=new PacketBuffer(io.netty.buffer.Unpooled.wrappedBuffer(bytes));try{NameBadges.receive(b);}finally{b.release();}});}data.release();ci.cancel();}
}
