package dev.unbox.client.mixin;
import dev.unbox.client.LoginTransport;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientHandshakePacketListenerImpl.class)
public class NeoLoginClientMixin {
 @Inject(method="handleCustomQuery",at=@At("HEAD"),cancellable=true)
 private void unboxQuery(ClientboundCustomQueryPacket p,CallbackInfo ci){if(p.payload() instanceof LoginTransport.Query q){LoginTransport.query((ClientHandshakePacketListenerImpl)(Object)this,p,q);ci.cancel();}}
 @Inject(method="onDisconnect",at=@At("HEAD"))
 private void unboxClose(DisconnectionDetails d,CallbackInfo ci){LoginTransport.close((ClientHandshakePacketListenerImpl)(Object)this);}
}
