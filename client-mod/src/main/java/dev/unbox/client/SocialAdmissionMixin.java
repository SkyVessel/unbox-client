package dev.unbox.client.mixin;
import dev.unbox.client.SocialBridge;
import net.minecraft.server.players.*;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(PlayerList.class)
public class SocialAdmissionMixin {
 @Inject(method="canPlayerLogin",at=@At("HEAD"),cancellable=true)
 private void unboxInvited(java.net.SocketAddress address,NameAndId profile,CallbackInfoReturnable<Component> ci){if(!SocialBridge.allowed(profile.id()))ci.setReturnValue(Component.literal("This world is invite-only."));}
}
