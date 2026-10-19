package dev.unbox.client.mixin;
import dev.unbox.client.PerformanceMonitor;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public class PerformanceGameMixin {
 @Unique private long unbox$tickStart;
 @Inject(method="tick",at=@At("HEAD")) private void unbox$tickStart(CallbackInfo ci){unbox$tickStart=PerformanceMonitor.begin();}
 @Inject(method="tick",at=@At("RETURN")) private void unbox$tickEnd(CallbackInfo ci){PerformanceMonitor.end(PerformanceMonitor.Group.CLIENT_TICK,unbox$tickStart);}
 @Inject(method="renderFrame",at=@At("TAIL")) private void unbox$frame(boolean tick,CallbackInfo ci){PerformanceMonitor.frame();}
}
