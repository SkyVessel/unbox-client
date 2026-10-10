package dev.unbox.client.mixin;
import dev.unbox.client.LoginTransport;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerLoginPacketListenerImpl.class)
public class NeoLoginServerMixin {
 @Shadow @Final private MinecraftServer server;
 @Inject(method="handleCustomQueryPacket",at=@At("HEAD"),cancellable=true)
 private void unboxAnswer(ServerboundCustomQueryAnswerPacket p,CallbackInfo ci){if(LoginTransport.ours(p.transactionId())){LoginTransport.answer((ServerLoginPacketListenerImpl)(Object)this,server,p);ci.cancel();}}
 @Inject(method="handleLoginAcknowledgement",at=@At("RETURN"))
 private void unboxFinish(net.minecraft.network.protocol.login.ServerboundLoginAcknowledgedPacket p,CallbackInfo ci){LoginTransport.finish((ServerLoginPacketListenerImpl)(Object)this);}
 @Inject(method="onDisconnect",at=@At("HEAD"))
 private void unboxClose(DisconnectionDetails d,CallbackInfo ci){LoginTransport.close((ServerLoginPacketListenerImpl)(Object)this,server);}
}
