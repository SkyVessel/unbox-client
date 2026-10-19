package fuzs.iteminteractions.common.impl.client.helper;

import com.google.common.collect.ImmutableSortedMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.NavigableMap;

public final class ItemStorageTooltipHelper {
    private static final NavigableMap<Integer, Character> NUMBER_SUFFIXES = ImmutableSortedMap.<Integer, Character>naturalOrder()
            .put(1_000, 'K')
            .put(1_000_000, 'M')
            .put(1_000_000_000, 'B')
            .build();

    private ItemStorageTooltipHelper() {
        // NO-OP
    }

    /**
     * @see GuiGraphicsExtractor#itemDecorations(Font, ItemStack, int, int, String)
     */
    public static void itemDecorations(GuiGraphicsExtractor guiGraphics, Font font, ItemStack itemStack, int x, int y) {
        if (!itemStack.isEmpty()) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.itemBar(itemStack, x, y);
            guiGraphics.itemCooldown(itemStack, x, y);
            itemCount(guiGraphics, font, itemStack, x, y);
            guiGraphics.pose().popMatrix();
            // This should probably also invoke the respective event on each mod loader, but meh.
        }
    }

    /**
     * @see GuiGraphicsExtractor#itemCount(Font, ItemStack, int, int, String)
     */
    private static void itemCount(GuiGraphicsExtractor guiGraphics, Font font, ItemStack itemStack, int x, int y) {
        int count = itemStack.getCount();
        if (count <= 1) {
            return;
        }

        String countValue = getIntAsString(count);
        float scale = Math.min(1.0F, 16.0F / font.width(countValue));
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scale, scale);
        int posX = (int) ((x + 17) / scale - font.width(countValue));
        int posY = (int) ((y + font.lineHeight * 2) / scale - font.lineHeight);
        guiGraphics.text(font, countValue, posX, posY, -1, true);
        guiGraphics.pose().popMatrix();
    }

    private static String getIntAsString(int value) {
        Map.Entry<Integer, Character> entry = NUMBER_SUFFIXES.floorEntry(value);
        return entry == null ? String.valueOf(value) : value / entry.getKey() + String.valueOf(entry.getValue());
    }
}
