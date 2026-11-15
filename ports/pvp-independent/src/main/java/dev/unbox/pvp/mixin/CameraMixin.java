package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.*;
/** World/hand separation adapted from Nickotato NoScreenShake (MIT). */
@Mixin(EntityRenderer.class)
public class CameraMixin {
 @Redirect(method="setupCameraTransform",at=@At(value="FIELD",target="Lnet/minecraft/client/settings/GameSettings;viewBobbing:Z",ordinal=0))
 private boolean worldBobbing(GameSettings settings){return settings.viewBobbing&&(!PvpSettings.on("bobbing")||PvpSettings.flag("bobbing.world",false));}
 @Unique private boolean unbox$worldCamera;
 @Inject(method="setupCameraTransform",at=@At("HEAD")) private void worldStart(float partial,int pass,CallbackInfo ci){unbox$worldCamera=true;}
 @Inject(method="setupCameraTransform",at=@At("RETURN")) private void worldEnd(float partial,int pass,CallbackInfo ci){unbox$worldCamera=false;}
 @Inject(method="setupViewBobbing",at=@At("HEAD"),cancellable=true) private void handBobbing(float partial,CallbackInfo ci){if(!unbox$worldCamera&&PvpSettings.on("bobbing")&&!PvpSettings.flag("bobbing.hand",true))ci.cancel();}
 @ModifyArg(method="hurtCameraEffect",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GlStateManager;rotate(FFFF)V",ordinal=2),index=0)
 private float hurt(float angle){return PvpSettings.on("hurtcam")?angle*PvpSettings.number("hurtcam.strength",0,0,100)/100f:angle;}
}
