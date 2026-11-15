package dev.unbox.client.mixin;
import dev.unbox.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Native 1.20 blur pass for public Spatial GUI 1.8, which has no blur config field. */
@Pseudo @Mixin(targets="org.tastytrash.spatialGUI.render.SpatialGUIRenderer",remap=false) public abstract class SpatialBlurMixin {
 @Shadow(remap=false) public abstract boolean shouldCapture();
 @Inject(method="renderInWorldPost",at=@At("HEAD"),remap=false)private void blur(CallbackInfo ci){if(shouldCapture()&&ClientIntegrations.active("spatial")&&ClientHud.flag("spatial.blur",false))LegacyGui.blur();}
}
