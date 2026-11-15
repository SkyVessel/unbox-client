package net.razrcraft.lookbehind.mixin.client;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import net.razrcraft.lookbehind.client.LookBehindClient;

@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Redirect(method = "setup", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = -1))
    private void rearviewModifyRotation(Camera instance, float yaw, float pitch) {
        float yawFinal = yaw;
        float pitchFinal = pitch;
        if(LookBehindClient.isLookingBehind()) {
            yawFinal = yaw - 180.0f;
            pitchFinal = pitch * -1.0f;
        }
        ((CameraMixin) (Object) instance).setRotation(yawFinal, pitchFinal);
    }
}
