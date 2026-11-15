package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.util.MovementInputFromOptions;
import net.minecraft.util.MovementInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MovementInputFromOptions.class)
public abstract class SneakMixin extends MovementInput {
 @Inject(method="updatePlayerMoveState",at=@At("RETURN")) private void toggle(CallbackInfo ci){if(PvpSettings.on("sneak")&&PvpSettings.sneak()&&!sneak){sneak=true;moveStrafe*=.3f;moveForward*=.3f;}}
}
