package dev.unbox.pvp.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Minecraft.class)
public class FeedbackShutdownMixin {
 @Inject(method="shutdownMinecraftApplet",at=@At("HEAD"))
 private void unbox$feedbackExit(CallbackInfo ci){dev.unbox.unpvp.Feedback.shutdown();}
}
