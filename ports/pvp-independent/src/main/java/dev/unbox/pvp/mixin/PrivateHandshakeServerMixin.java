package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PrivateLogin;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.server.network.NetHandlerHandshakeTCP;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(NetHandlerHandshakeTCP.class)
public class PrivateHandshakeServerMixin {
 @Shadow @Final private NetworkManager networkManager;
 @Inject(method="processHandshake",at=@At("HEAD"))
 private void unboxClaim(C00Handshake packet,CallbackInfo ci){PrivateLogin.claim(networkManager,((PrivateHandshakeAccess)packet).unboxPort());}
}
