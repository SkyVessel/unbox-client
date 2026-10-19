package dev.unbox.client.mixin;
import dev.unbox.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="fuzs.iteminteractions.common.api.v2.world.item.storage.ContainerStorage",remap=false)
public abstract class ShulkerHostGateMixin {
 @Inject(method="allowModification",at=@At("HEAD"),cancellable=true,remap=false)
 private void unbox$permission(net.minecraft.world.item.ItemStack stack,net.minecraft.world.entity.player.Player player,CallbackInfoReturnable<Boolean> cir){
  if(!(player.level().isClientSide()?SurvivalHostMods.hostEnabled("shulker"):WorldFeatures.serverEnabled("shulker")))cir.setReturnValue(false);
 }
}
