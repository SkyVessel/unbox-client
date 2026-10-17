package dev.unbox.client.mixin;
import dev.unbox.client.LocalSkin;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayer.class)
public class LocalSkinMixin {
 @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
 private void unboxLocalSkin(CallbackInfoReturnable<PlayerSkin> ci){ci.setReturnValue(LocalSkin.replace(ci.getReturnValue(),(AbstractClientPlayer)(Object)this));}
}
