package dev.unbox.client.mixin;
import dev.unbox.client.FrameBenchmark;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public class FrameBenchmarkMixin {
 @Inject(method="renderFrame",at=@At("TAIL"))
 private void unboxMeasure(boolean tick,CallbackInfo ci){FrameBenchmark.frame();}
}
