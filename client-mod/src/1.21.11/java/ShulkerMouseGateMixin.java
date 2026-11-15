package dev.unbox.client.mixin;

import dev.unbox.client.ClientIntegrations;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo @Mixin(targets="fuzs.iteminteractions.impl.client.handler.ClientInputActionHandler",remap=false)
public abstract class ShulkerMouseGateMixin {
    @Inject(method="getContainerItemStack",at=@At("HEAD"),cancellable=true,remap=false)
    private static void unbox$container(CallbackInfoReturnable<ItemStack> result){
        if(!ClientIntegrations.active("shulker"))result.setReturnValue(ItemStack.EMPTY);
    }
}
