package dev.unbox.client.mixin;

import dev.unbox.client.FreecamController;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class FreecamTurnMixin {
    @Inject(method="turn", at=@At("HEAD"), cancellable=true)
    private void unbox$freecamTurn(double horizontal, double vertical, CallbackInfo ci) {
        if ((Object)this == Minecraft.getInstance().player && FreecamController.active()) {
            FreecamController.turn(horizontal, vertical);
            ci.cancel();
        }
    }
}
