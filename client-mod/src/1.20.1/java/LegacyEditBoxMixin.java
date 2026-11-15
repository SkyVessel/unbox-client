package dev.unbox.client.mixin;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(EditBox.class)public class LegacyEditBoxMixin {
 @Redirect(method="renderWidget",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;III)I"))private int shadow(GuiGraphics g,Font f,net.minecraft.util.FormattedCharSequence text,int x,int y,int color){return g.drawString(f,text,x,y,color,!((Object)this instanceof dev.unbox.client.LegacyEditBox box)||box.textShadow());}
}
