package dev.unbox.client.mixin;
import dev.unbox.client.SocialBridge;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.players.NameAndId;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** A shared nickname never grants the integrated world's owner privileges. */
@Mixin(IntegratedServer.class)
public class SocialOwnerMixin {
 @Inject(method="isSingleplayerOwner",at=@At("HEAD"),cancellable=true)
 private void unboxOwnerId(NameAndId profile,CallbackInfoReturnable<Boolean> ci){if(SocialBridge.privateAuthentication()){var owner=((IntegratedServer)(Object)this).getSingleplayerProfile();ci.setReturnValue(owner!=null&&owner.id().equals(profile.id()));}}
}
