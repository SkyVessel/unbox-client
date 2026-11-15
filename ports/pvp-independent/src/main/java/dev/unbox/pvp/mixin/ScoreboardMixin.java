package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.gui.*;
import net.minecraft.scoreboard.ScoreObjective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** VanillaHUD replaces this renderer with its independently positioned HUD. */
@Mixin(GuiIngame.class)
public class ScoreboardMixin {
 @Inject(method="renderScoreboard",at=@At("HEAD"),cancellable=true) private void replace(ScoreObjective objective,ScaledResolution resolution,CallbackInfo ci){if(PvpSettings.on("scoreboard"))ci.cancel();}
}
