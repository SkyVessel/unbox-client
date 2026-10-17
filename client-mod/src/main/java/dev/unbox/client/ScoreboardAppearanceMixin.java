package dev.unbox.client.mixin;

import dev.unbox.client.ScoreboardAppearance;
import dev.unbox.client.UnboxClient;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(Gui.class)
public abstract class ScoreboardAppearanceMixin {
    @ModifyArg(method="displayScoreboardSidebar", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal=0), index=4)
    private int unbox$titleBackground(int original) { return UnboxClient.enabled("scoreboard") ? ScoreboardAppearance.background(true) : original; }

    @ModifyArg(method="displayScoreboardSidebar", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal=1), index=4)
    private int unbox$bodyBackground(int original) { return UnboxClient.enabled("scoreboard") ? ScoreboardAppearance.background(false) : original; }

    @ModifyArg(method="displayScoreboardSidebar", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", ordinal=0), index=1)
    private Component unbox$title(Component original) { return !UnboxClient.enabled("scoreboard") ? original : !ScoreboardAppearance.showTitle() ? Component.empty() : ScoreboardAppearance.tint(original,true,false); }

    @ModifyArg(method="displayScoreboardSidebar", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", ordinal=1), index=1)
    private Component unbox$name(Component original) { return UnboxClient.enabled("scoreboard") ? ScoreboardAppearance.tint(original,false,false) : original; }

    @ModifyArg(method="displayScoreboardSidebar", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", ordinal=2), index=1)
    private Component unbox$score(Component original) { return !UnboxClient.enabled("scoreboard") ? original : !ScoreboardAppearance.showScores() ? Component.empty() : ScoreboardAppearance.tint(original,false,true); }

    @ModifyArg(method="displayScoreboardSidebar", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"), index=5)
    private boolean unbox$shadow(boolean original) { return UnboxClient.enabled("scoreboard") ? ScoreboardAppearance.shadow() : original; }

    @ModifyArg(method="displayScoreboardSidebar", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", ordinal=0), index=4)
    private int unbox$titleColor(int original) { return UnboxClient.enabled("scoreboard") ? ScoreboardAppearance.text(true, false) : original; }

    @ModifyArg(method="displayScoreboardSidebar", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", ordinal=1), index=4)
    private int unbox$nameColor(int original) { return UnboxClient.enabled("scoreboard") ? ScoreboardAppearance.text(false, false) : original; }

    @ModifyArg(method="displayScoreboardSidebar", at=@At(value="INVOKE", target="Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V", ordinal=2), index=4)
    private int unbox$scoreColor(int original) { return UnboxClient.enabled("scoreboard") ? ScoreboardAppearance.text(false, true) : original; }
}
