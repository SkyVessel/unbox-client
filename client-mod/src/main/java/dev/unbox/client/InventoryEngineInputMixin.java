package dev.unbox.client.mixin;

import dev.unbox.client.SurvivalClientMods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets={"org.anti_ad.mc.ipnext.input.InputHandler","org.anti_ad.mc.ipnext.input.InventoryInputHandler","org.anti_ad.mc.ipnext.input.CancellableInputHandler"},remap=false)
public abstract class InventoryEngineInputMixin {
    @Inject(method="onInput",at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$enabled(CallbackInfoReturnable<Boolean> cir){if(!SurvivalClientMods.inventoryActive())cir.setReturnValue(false);}
}
