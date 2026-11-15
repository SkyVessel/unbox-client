package dev.unbox.cleantest.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Unbox fullbright: only the client lightmap changes; world lighting and gamma stay intact. */
@Mixin(EntityRenderer.class)
public class FullbrightMixin {
    @Shadow private Minecraft mc;
    @Shadow @Final private DynamicTexture lightmapTexture;
    @Shadow @Final private int[] lightmapColors;
    @Shadow private boolean lightmapUpdateNeeded;
    @Unique private boolean unbox$brightMap;

    @Inject(method="updateLightmap",at=@At("HEAD"),cancellable=true)
    private void unbox$fullbright(float partialTicks,CallbackInfo ci){
        if(mc.theWorld==null){if(unbox$brightMap)lightmapUpdateNeeded=true;unbox$brightMap=false;return;}
        if(!unbox$brightMap){
            java.util.Arrays.fill(lightmapColors,0xffffffff);
            lightmapTexture.updateDynamicTexture();
            unbox$brightMap=true;
        }
        lightmapUpdateNeeded=false;
        ci.cancel();
    }
}
