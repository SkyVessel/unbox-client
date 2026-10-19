package dev.unbox.client.mixin;

import dev.unbox.client.WorldFeatures;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject held radial-menu input before the upstream tick can create a Screen. */
@Pseudo @Mixin(targets="nl.requios.effortlessbuilding.utilities.KeyBindings",remap=false)
public abstract class BuildingKeyGateMixin {
    @Inject(method="isKeyDown",at=@At("HEAD"),cancellable=true,remap=false)
    private static void unbox$heldKey(CallbackInfoReturnable<Boolean> result){if(!WorldFeatures.buildingClient())result.setReturnValue(false);}
}
