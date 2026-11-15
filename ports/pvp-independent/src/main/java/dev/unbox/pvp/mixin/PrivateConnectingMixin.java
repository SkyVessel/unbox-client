package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.SocialBridge;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.network.NetworkManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiConnecting.class)
public class PrivateConnectingMixin implements SocialBridge.CancelConnection {
 @Shadow private boolean cancel;
 @Shadow private NetworkManager networkManager;
 public void unboxCancel(){cancel=true;if(networkManager!=null)networkManager.closeChannel(new net.minecraft.util.ChatComponentText("Join cancelled"));}
 @Inject(method="actionPerformed",at=@At("HEAD"))
 private void unboxCancelButton(net.minecraft.client.gui.GuiButton button,CallbackInfo ci){if(button.id==0&&SocialBridge.joining())SocialBridge.cancelJoin();}
}
