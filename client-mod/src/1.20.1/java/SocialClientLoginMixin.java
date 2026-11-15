package dev.unbox.client.mixin;
import dev.unbox.client.SocialLogin;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientHandshakePacketListenerImpl.class)
public class SocialClientLoginMixin {
 @Inject(method="authenticateServer",at=@At("HEAD"),cancellable=true)private void invitedAuthentication(String hash,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.network.chat.Component> ci){if(SocialLogin.hasPendingInvitation())ci.setReturnValue(null);}

 @Inject(method="handleHello",at=@At("HEAD"))
 private void unboxKey(ClientboundHelloPacket p,CallbackInfo ci){try{SocialLogin.captureKey((ClientHandshakePacketListenerImpl)(Object)this,p.getPublicKey());}catch(Exception ignored){}}
}
