package dev.unbox.client.mixin;

import dev.unbox.client.FreecamController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply a temporary wheel multiplier without overwriting upstream settings or implementing flight. */
@Pseudo @Mixin(targets="net.xolt.freecam.config.model.ModConfigImpl",remap=false)
public abstract class FreecamSpeedMixin {
    @Inject(method={"getHorizontalSpeed","getVerticalSpeed"},at=@At("RETURN"),cancellable=true,remap=false)
    private void unbox$wheelSpeed(CallbackInfoReturnable<Double> ci) {
        ci.setReturnValue(ci.getReturnValue()*FreecamController.speedMultiplier());
    }
}
