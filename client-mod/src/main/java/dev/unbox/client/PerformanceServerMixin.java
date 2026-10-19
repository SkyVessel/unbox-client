package dev.unbox.client.mixin;
import dev.unbox.client.PerformanceMonitor;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MinecraftServer.class)
public class PerformanceServerMixin {
 @Unique private long unbox$serverStart;
 @Inject(method="tickServer",at=@At("HEAD")) private void unbox$start(CallbackInfo ci){unbox$serverStart=PerformanceMonitor.begin();}
 @Inject(method="tickServer",at=@At("RETURN")) private void unbox$end(CallbackInfo ci){PerformanceMonitor.end(PerformanceMonitor.Group.SERVER,unbox$serverStart);}
}
