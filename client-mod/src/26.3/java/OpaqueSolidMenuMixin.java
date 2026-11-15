package dev.unbox.client.mixin;
import dev.unbox.client.SolidScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Skip the hidden world only after a matching opaque backing is available. */
@Mixin(GameRenderer.class)
public class OpaqueSolidMenuMixin {
 @Inject(method="renderLevel",at=@At("HEAD"),cancellable=true)
 private void unboxOpaqueMenu(CallbackInfo ci){if(Minecraft.getInstance().gui.screen() instanceof SolidScreen s&&s.coversWorld())ci.cancel();}
}
