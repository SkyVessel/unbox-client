package dev.unbox.client.mixin;
import dev.unbox.client.SocialLogin;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.protocol.login.ClientboundHelloPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientHandshakePacketListenerImpl.class)
public class SocialClientLoginMixin {
 @Inject(method="handleHello",at=@At("HEAD"))
 private void unboxKey(ClientboundHelloPacket p,CallbackInfo ci){try{SocialLogin.captureKey((ClientHandshakePacketListenerImpl)(Object)this,p.getPublicKey());}catch(Exception ignored){}}
}
