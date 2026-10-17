package dev.unbox.client.mixin;

import dev.unbox.client.GraveKeeper;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Display.class)
public abstract class GraveLabelMixin {
    @Inject(method="tick", at=@At("TAIL"))
    private void unbox$updateGraveNameRange(CallbackInfo ci) {
        Display self=(Display)(Object)this;
        if(self.tickCount%20==0){GraveKeeper.updateLabel(self);dev.unbox.client.GraveModel.cleanup(self);}
    }
}
