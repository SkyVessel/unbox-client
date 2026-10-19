package dev.unbox.client.mixin;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep advanced IPN settings from enabling a second wheel handler. */
@Pseudo @Mixin(targets="org.anti_ad.mc.ipnext.inventory.GeneralInventoryActions",remap=false)
public abstract class InventoryScrollGateMixin {
    @Inject(method={"scrollUp","scrollDown"},at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$mouseOwner(CallbackInfo ci){ci.cancel();}
}
