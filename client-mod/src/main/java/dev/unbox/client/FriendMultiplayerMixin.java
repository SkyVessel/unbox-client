package dev.unbox.client.mixin;
import dev.unbox.client.MultiplayerHubScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(JoinMultiplayerScreen.class)
public abstract class FriendMultiplayerMixin {
 @Inject(method="refreshServerList",at=@At("HEAD"),cancellable=true)
 private void unboxKeepInvitations(CallbackInfo ci){if((Object)this instanceof MultiplayerHubScreen hub){hub.refreshInvitedWorlds();ci.cancel();}}
}
