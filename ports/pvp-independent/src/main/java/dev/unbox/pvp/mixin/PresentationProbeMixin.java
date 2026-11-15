package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PresentationProbe;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public class PresentationProbeMixin {
 @Inject(method="updateDisplay",at=@At("HEAD")) private void window(CallbackInfo ci){PresentationProbe.check();}
}
