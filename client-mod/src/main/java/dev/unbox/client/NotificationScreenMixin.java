package dev.unbox.client.mixin;
import dev.unbox.client.SocialNotifications;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Screen.class)
public class NotificationScreenMixin {
 @Inject(method="extractRenderStateWithTooltipAndSubtitles",at=@At("TAIL"))
 private void unboxNotifications(GuiGraphicsExtractor g,int x,int y,float delta,CallbackInfo ci){SocialNotifications.draw(g);}
}
