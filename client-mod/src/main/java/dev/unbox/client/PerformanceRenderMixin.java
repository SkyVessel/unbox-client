package dev.unbox.client.mixin;
import dev.unbox.client.PerformanceMonitor;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
public class PerformanceRenderMixin {
 @Unique private long unbox$renderStart,unbox$extractStart;
 @Inject(method="render",at=@At("HEAD")) private void unbox$renderStart(CallbackInfo ci){unbox$renderStart=PerformanceMonitor.begin();}
 @Inject(method="render",at=@At("RETURN")) private void unbox$renderEnd(CallbackInfo ci){PerformanceMonitor.end(PerformanceMonitor.Group.RENDER,unbox$renderStart);}
 @Inject(method="extract",at=@At("HEAD")) private void unbox$extractStart(CallbackInfo ci){unbox$extractStart=PerformanceMonitor.begin();}
 @Inject(method="extract",at=@At("RETURN")) private void unbox$extractEnd(CallbackInfo ci){PerformanceMonitor.end(PerformanceMonitor.Group.EXTRACT,unbox$extractStart);}
}
