package dev.unbox.client.mixin;

import dev.unbox.client.FreecamController;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Detached camera is observation-only; no body interaction packets are produced. */
@Mixin(Minecraft.class)
public abstract class FreecamInteractionMixin {
    @Inject(method="startAttack",at=@At("HEAD"),cancellable=true)
    private void unbox$attack(CallbackInfoReturnable<Boolean> ci) { if(FreecamController.active())ci.setReturnValue(false); }
    @Inject(method="continueAttack",at=@At("HEAD"),cancellable=true)
    private void unbox$continueAttack(boolean held,CallbackInfo ci) { if(FreecamController.active())ci.cancel(); }
    @Inject(method="startUseItem",at=@At("HEAD"),cancellable=true)
    private void unbox$use(CallbackInfo ci) { if(FreecamController.active())ci.cancel(); }
    @Inject(method="pickBlockOrEntity",at=@At("HEAD"),cancellable=true)
    private void unbox$pick(CallbackInfo ci) { if(FreecamController.active())ci.cancel(); }
}
