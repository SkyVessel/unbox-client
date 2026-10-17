package dev.unbox.client.mixin;

import dev.unbox.client.SurvivalClientMods;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="team.creative.itemphysiclite.ClientPhysic",remap=false)
public abstract class ItemPhysicRotationGateMixin {
    @Inject(method="calculateRotation",at=@At("HEAD"),cancellable=true,remap=false)
    private static void unbox$enabled(CallbackInfo ci){if(!SurvivalClientMods.itemsActive())ci.cancel();}
}
