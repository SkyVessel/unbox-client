package dev.unbox.client.mixin;

import dev.unbox.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Spatial GUI 1.8 predates its blur setting. Apply the vanilla pass before the world-space panel. */
@Mixin(GameRenderer.class)
public abstract class SpatialBlurMixin implements SpatialBlurAccess {
    @Shadow @Final private CrossFrameResourcePool resourcePool;
    public void unboxBlurWorld(){
        var mc=Minecraft.getInstance();
        var chain=mc.getShaderManager().getPostChain(Identifier.withDefaultNamespace("blur"),LevelTargetBundle.MAIN_TARGETS);
        if(chain!=null)chain.process(mc.getMainRenderTarget(),resourcePool);
    }
    @Pseudo @Mixin(targets="org.tastytrash.spatialGUI.render.SpatialGUIRenderer",remap=false)
    public abstract static class BeforePanel {
        @Shadow(remap=false) public abstract boolean shouldCapture();
        @Inject(method="renderInWorldPost",at=@At("HEAD"),remap=false)
        private void unbox$blur(CallbackInfo ci){
            if(shouldCapture()&&ClientIntegrations.active("spatial")&&ClientHud.flag("spatial.blur",false))
                ((SpatialBlurAccess)Minecraft.getInstance().gameRenderer).unboxBlurWorld();
        }
    }
}
