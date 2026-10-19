package dev.unbox.client.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mouse Tweaks owns drag gestures; sorting must not install a second handler. */
@Pseudo @Mixin(targets="org.anti_ad.mc.ipnext.event.MiscHandler",remap=false)
public abstract class InventoryGestureGateMixin {
    @Inject(method="swipeMoving",at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$mouseOwner(CallbackInfo ci){ci.cancel();}
}
