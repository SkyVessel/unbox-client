package dev.unbox.client.mixin;
import dev.unbox.client.*;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Gui.class)public abstract class ScoreboardAppearanceMixin {
 @ModifyArg(method="displayScoreboardSidebar",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V",ordinal=0),index=4)private int body(int color){return UnboxClient.enabled("scoreboard")?ScoreboardAppearance.background(false):color;}
 @ModifyArg(method="displayScoreboardSidebar",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V",ordinal=1),index=4)private int title(int color){return UnboxClient.enabled("scoreboard")?ScoreboardAppearance.background(true):color;}
 @ModifyArg(method="displayScoreboardSidebar",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V",ordinal=2),index=4)private int divider(int color){return UnboxClient.enabled("scoreboard")?ScoreboardAppearance.background(false):color;}
 @ModifyArg(method="displayScoreboardSidebar",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",ordinal=0),index=1)private Component name(Component c){return UnboxClient.enabled("scoreboard")?ScoreboardAppearance.tint(c,false,false):c;}
 @ModifyArg(method="displayScoreboardSidebar",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I",ordinal=1),index=1)private Component titleText(Component c){return !UnboxClient.enabled("scoreboard")?c:ScoreboardAppearance.showTitle()?ScoreboardAppearance.tint(c,true,false):Component.empty();}
 @Redirect(method="displayScoreboardSidebar",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)I"))private int score(net.minecraft.client.gui.GuiGraphics g,net.minecraft.client.gui.Font f,String s,int x,int y,int color,boolean shadow){if(!UnboxClient.enabled("scoreboard"))return g.drawString(f,s,x,y,color,shadow);return g.drawString(f,ScoreboardAppearance.showScores()?Component.literal(net.minecraft.ChatFormatting.stripFormatting(s)).withStyle(style->style.withColor(ScoreboardAppearance.text(false,true))):Component.empty(),x,y,ScoreboardAppearance.text(false,true),ScoreboardAppearance.shadow());}
 @ModifyArg(method="displayScoreboardSidebar",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;drawString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)I"),index=5)private boolean shadow(boolean s){return UnboxClient.enabled("scoreboard")?ScoreboardAppearance.shadow():s;}
}
