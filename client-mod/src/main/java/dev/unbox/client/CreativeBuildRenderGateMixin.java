package dev.unbox.client.mixin;
import dev.unbox.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="nl.requios.effortlessbuilding.render.RenderHandler",remap=false)
public abstract class CreativeBuildRenderGateMixin {
 @Inject(method={"onRenderLevel","onRenderGui"},at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$creative(CallbackInfo ci){if(!WorldFeatures.buildingClient())ci.cancel();}
}
