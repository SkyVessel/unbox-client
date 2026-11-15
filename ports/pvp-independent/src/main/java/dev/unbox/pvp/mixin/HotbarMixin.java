package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraftforge.client.GuiIngameForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** VanillaHUD hotbar matrix wrapper, with Unbox configuration and vanilla contents preserved. */
@Mixin(GuiIngameForge.class)
public class HotbarMixin {
 @Unique private boolean unbox$hotbar;
 @Inject(method="func_180479_a",at=@At("HEAD"),remap=false) private void begin(ScaledResolution r,float partial,CallbackInfo ci){unbox$hotbar=PvpSettings.on("hotbar");if(unbox$hotbar){GlStateManager.pushMatrix();float s=PvpSettings.number("hotbar.scale",100,50,150)/100f;GlStateManager.translate(r.getScaledWidth()*(1-s)/2f,r.getScaledHeight()*(1-s)-PvpSettings.number("hotbar.offset",0,0,150),0);GlStateManager.scale(s,s,1);}}
 @Inject(method="func_180479_a",at=@At("RETURN"),remap=false) private void end(ScaledResolution r,float partial,CallbackInfo ci){if(unbox$hotbar){GlStateManager.popMatrix();unbox$hotbar=false;}}
 @Inject(method="renderToolHightlight",at=@At("HEAD"),cancellable=true,remap=false) private void label(ScaledResolution r,CallbackInfo ci){if(PvpSettings.on("hotbar")&&!PvpSettings.flag("hotbar.label",true))ci.cancel();}
}
