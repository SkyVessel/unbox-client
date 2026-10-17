package dev.unbox.client.mixin;

import dev.unbox.client.SurvivalHostMods;
import java.util.Optional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="fr.rakambda.fallingtree.common.tree.TreeHandler",remap=false)
public abstract class FallingTreeGateMixin {
    @Inject(method="breakTree",at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$hostOnly(CallbackInfoReturnable<Object> cir){
        if(!SurvivalHostMods.serverEnabled("fallingtree"))cir.setReturnValue(SurvivalHostMods.abortedTree());
    }
    @Inject(method="shouldCancelEvent",at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$keepVanillaEvent(CallbackInfoReturnable<Boolean> cir){
        if(!SurvivalHostMods.hostEnabled("fallingtree"))cir.setReturnValue(false);
    }
    @Inject(method="getBreakSpeed",at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$keepVanillaSpeed(CallbackInfoReturnable<Optional<Float>> cir){
        if(!SurvivalHostMods.hostEnabled("fallingtree"))cir.setReturnValue(Optional.empty());
    }
}
