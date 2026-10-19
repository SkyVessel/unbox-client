package fuzs.iteminteractions.common.mixin.client;

import fuzs.iteminteractions.common.impl.client.helper.ItemDecorationsHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphicsExtractor.class)
abstract class GuiGraphicsExtractorMixin {

    @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V",
            at = @At("TAIL"))
    public void itemDecorations(Font font, ItemStack itemStack, int x, int y, @Nullable String countText, CallbackInfo callback) {
        if (!itemStack.isEmpty()) {
            ItemDecorationsHelper.extractItemDecorations(GuiGraphicsExtractor.class.cast(this), font, itemStack, x, y);
        }
    }
}
