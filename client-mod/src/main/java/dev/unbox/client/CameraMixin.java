package dev.unbox.client.mixin;
import dev.unbox.client.UnboxClient;
import dev.unbox.client.OptifineCape;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(Camera.class)
public class CameraMixin {
 @Inject(method="calculateFov",at=@At("RETURN"),cancellable=true)
 private void unboxZoom(float delta,CallbackInfoReturnable<Float> ci){ci.setReturnValue(ci.getReturnValue()/UnboxClient.zoomFactor());}
}
