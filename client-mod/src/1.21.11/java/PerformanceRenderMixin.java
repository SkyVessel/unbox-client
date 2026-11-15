package dev.unbox.client.mixin;

import dev.unbox.client.PerformanceMonitor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class PerformanceRenderMixin {
    @Unique private long unbox$renderStart;
    @Inject(method="render",at=@At("HEAD"))
    private void unbox$renderStart(CallbackInfo ci){unbox$renderStart=PerformanceMonitor.begin();}
    @Inject(method="render",at=@At("RETURN"))
    private void unbox$renderEnd(CallbackInfo ci){PerformanceMonitor.end(PerformanceMonitor.Group.RENDER,unbox$renderStart);}

    /** In 1.21.11 extraction lives inside LevelRenderer's profiler section. */
    @Mixin(LevelRenderer.class)
    public static class LevelExtraction {
        @Unique private long unbox$extractStart;
        @Inject(method="renderLevel",at=@At(value="CONSTANT",args="stringValue=extract"))
        private void unbox$extractStart(CallbackInfo ci){unbox$extractStart=PerformanceMonitor.begin();}
        @Inject(method="renderLevel",at=@At(value="CONSTANT",args="stringValue=debug"))
        private void unbox$extractEnd(CallbackInfo ci){PerformanceMonitor.end(PerformanceMonitor.Group.EXTRACT,unbox$extractStart);}
    }
}
