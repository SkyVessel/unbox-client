package dev.unbox.client.mixin;
import dev.unbox.client.UnboxClient;
import dev.unbox.client.OptifineCape;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Gui.class)
public class GuiMixin {
 @Inject(method="extractCrosshair",at=@At("HEAD"),cancellable=true)
 private void unboxCrosshair(GuiGraphicsExtractor g,DeltaTracker t,CallbackInfo ci){if(UnboxClient.enabled("crosshair")){if(UnboxClient.showCrosshair())UnboxClient.crosshair(g);ci.cancel();}}
}
