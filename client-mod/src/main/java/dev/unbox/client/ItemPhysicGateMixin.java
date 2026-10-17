package dev.unbox.client.mixin;

import dev.unbox.client.SurvivalClientMods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="team.creative.itemphysiclite.ItemPhysicLite",remap=false)
public abstract class ItemPhysicGateMixin {
    @Inject(method="submit",at=@At("HEAD"),cancellable=true,remap=false)
    private static void unbox$enabled(CallbackInfoReturnable<Boolean> cir){
        if(!SurvivalClientMods.itemsActive())cir.setReturnValue(false);
    }
}
