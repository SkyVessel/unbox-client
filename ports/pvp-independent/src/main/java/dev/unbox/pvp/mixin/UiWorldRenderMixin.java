package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.renderer.EntityRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Skip only a fully covered world, after a matching opaque vector backing is ready. */
@Mixin(EntityRenderer.class)
public class UiWorldRenderMixin {
 @Inject(method="renderWorld",at=@At("HEAD"),cancellable=true) private void covered(float partial,long deadline,CallbackInfo ci){if(PvpSettings.coversWorld())ci.cancel();}
}
