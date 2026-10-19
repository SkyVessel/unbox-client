package dev.unbox.client.mixin;
import dev.unbox.client.ClientIntegrations;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="dev.dediamondpro.resourcify.config.Config",remap=false)
public abstract class ResourceConfigGateMixin {
 @Inject(method={"getResourcePacksEnabled","getDataPacksEnabled","getShaderPacksEnabled","getWorldsEnabled","getOpenLinkInResourcify"},at=@At("HEAD"),cancellable=true,remap=false)
 private void unbox$enabled(CallbackInfoReturnable<Boolean> result){if(!ClientIntegrations.active("resources"))result.setReturnValue(false);}
}
