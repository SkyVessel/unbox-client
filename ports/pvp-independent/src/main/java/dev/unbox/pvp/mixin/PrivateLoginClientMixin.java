package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PrivateLogin;
import net.minecraft.client.network.NetHandlerLoginClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.server.S01PacketEncryptionRequest;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(NetHandlerLoginClient.class)
public class PrivateLoginClientMixin {
 @Shadow @Final private NetworkManager networkManager;
 @Inject(method="<init>",at=@At("RETURN"))
 private void unboxTrack(NetworkManager connection,net.minecraft.client.Minecraft game,net.minecraft.client.gui.GuiScreen parent,CallbackInfo ci){PrivateLogin.track(connection);}
 @Inject(method="handleLoginSuccess",at=@At("HEAD"),cancellable=true)
 private void unboxAdmit(net.minecraft.network.login.server.S02PacketLoginSuccess packet,CallbackInfo ci){if(!PrivateLogin.acceptClient(networkManager))ci.cancel();}
 @Inject(method="handleEncryptionRequest",at=@At("HEAD"),cancellable=true)
 private void unboxVerify(S01PacketEncryptionRequest packet,CallbackInfo ci){if(PrivateLogin.client(networkManager,packet))ci.cancel();}
}
