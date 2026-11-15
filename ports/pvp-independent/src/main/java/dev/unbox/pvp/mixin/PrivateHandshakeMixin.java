package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PrivateLogin;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.handshake.client.C00Handshake;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(C00Handshake.class)
public abstract class PrivateHandshakeMixin {
 @Shadow private int port;
 @Inject(method="<init>(ILjava/lang/String;ILnet/minecraft/network/EnumConnectionState;)V",at=@At("RETURN"))
 private void unboxClaim(int version,String address,int actualPort,EnumConnectionState state,CallbackInfo ci){if(state==EnumConnectionState.LOGIN)port=PrivateLogin.portClaim(port);}
}
