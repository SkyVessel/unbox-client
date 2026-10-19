package dev.unbox.client.mixin;
import dev.unbox.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient",remap=false)
public abstract class CreativeBuildClientGateMixin {
 @Inject(method={"shouldInterceptPlacing","shouldInterceptBreaking"},at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$creative(CallbackInfoReturnable<Boolean> cir){if(!WorldFeatures.buildingClient())cir.setReturnValue(false);}
 @Inject(method={"handleRightClick","handleLeftClick"},at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$input(CallbackInfo ci){if(!WorldFeatures.buildingClient())ci.cancel();}
}
