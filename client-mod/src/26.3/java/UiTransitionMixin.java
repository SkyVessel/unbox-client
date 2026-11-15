package dev.unbox.client.mixin;
import dev.unbox.client.UiTransition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(net.minecraft.client.gui.Gui.class)
public class UiTransitionMixin {
 @Inject(method="setScreen",at=@At("HEAD")) private void unboxTransition(Screen next,CallbackInfo ci){UiTransition.open(next);}
 @Mixin(Screen.class) public static class Frame {
  @Inject(method="extractRenderStateWithTooltipAndSubtitles",at=@At("TAIL")) private void unboxFade(GuiGraphicsExtractor g,int x,int y,float delta,CallbackInfo ci){UiTransition.draw(g);}
 }
}
