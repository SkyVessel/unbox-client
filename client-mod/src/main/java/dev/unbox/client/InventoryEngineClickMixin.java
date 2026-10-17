package dev.unbox.client.mixin;

import dev.unbox.client.SurvivalClientMods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="org.anti_ad.mc.ipnext.inventory.ContainerClicker",remap=false)
public abstract class InventoryEngineClickMixin {
    // Includes both overloads and already queued clicks, without intercepting vanilla clicks.
    @Inject(method={"genericClick","executeClicks","executeQClicks","executeSwapClicks"},at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$enabled(CallbackInfo ci){if(!SurvivalClientMods.inventoryActive())ci.cancel();}
}
