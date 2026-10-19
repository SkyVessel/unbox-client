package fuzs.iteminteractions.common.impl.client.helper;

import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorageHolder;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public enum ItemDecorationsType {
    NOT_FULL(DyeColor.YELLOW),
    HAS_ITEM(DyeColor.GREEN),
    HAS_ITEM_BUT_FULL(DyeColor.RED);

    private final String string;
    private final int color;

    ItemDecorationsType(DyeColor dyeColor) {
        this("+", dyeColor);
    }

    ItemDecorationsType(String string, DyeColor dyeColor) {
        this(string, ARGB.opaque(dyeColor.getTextColor()));
    }

    ItemDecorationsType(String string, int color) {
        this.string = string;
        this.color = color;
    }

    public static @Nullable ItemDecorationsType pickType(ItemStorageHolder holder, ItemStack itemStack, ItemStack itemHeldByCursor, Player player) {
        if (holder.canAddItem(itemStack, itemHeldByCursor, player)) {
            if (holder.hasAnyOf(itemStack, itemHeldByCursor, player, false)) {
                return ItemDecorationsType.HAS_ITEM;
            } else {
                return ItemDecorationsType.NOT_FULL;
            }
        } else if (holder.hasAnyOf(itemStack, itemHeldByCursor, player, false)) {
            return ItemDecorationsType.HAS_ITEM_BUT_FULL;
        } else {
            return null;
        }
    }

    public void extractRenderState(GuiGraphicsExtractor guiGraphics, Font font, int itemPosX, int itemPosY) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.text(font,
                this.string,
                itemPosX + 19 - 2 - font.width(this.string),
                itemPosY + 6 + 3,
                this.color,
                true);
        guiGraphics.pose().popMatrix();
    }
}
