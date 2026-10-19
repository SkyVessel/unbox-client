package dev.unbox.client.mixin;
import dev.unbox.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="fuzs.iteminteractions.common.impl.client.gui.ItemStorageMouseActions",remap=false)
public abstract class ShulkerMouseGateMixin {
 @Inject(method="matches(Lnet/minecraft/world/item/ItemStack;)Z",at=@At("HEAD"),cancellable=true,remap=false)
 private void unbox$matches(CallbackInfoReturnable<Boolean> cir){if(!ClientIntegrations.active("shulker"))cir.setReturnValue(false);}
}
