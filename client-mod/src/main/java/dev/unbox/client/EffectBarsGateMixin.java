package dev.unbox.client.mixin;
import dev.unbox.client.ClientIntegrations;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Pseudo @Mixin(targets="io.github.a5b84.statuseffectbars.StatusEffectBarRenderer",remap=false)
public abstract class EffectBarsGateMixin {
 @Inject(method="render",at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$enabled(CallbackInfo ci){if(!ClientIntegrations.active("effectbars"))ci.cancel();}
}
