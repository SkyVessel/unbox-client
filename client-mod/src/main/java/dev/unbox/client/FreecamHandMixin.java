package dev.unbox.client.mixin;

import dev.unbox.client.FreecamController;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A detached spectator camera must not render the server player's first-person hands. */
@Mixin(GameRenderer.class)
public abstract class FreecamHandMixin {
    @Inject(method="renderItemInHand",at=@At("HEAD"),cancellable=true)
    private void unbox$hideHand(CameraRenderState state,float partialTick,Matrix4fc matrix,CallbackInfo ci) {
        if(FreecamController.active())ci.cancel();
    }
}
