package fuzs.iteminteractions.common.impl.client.helper;

import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorageHolder;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

public class ItemDecorationsHelper {
    @Nullable
    private static Slot slotBeingRendered;

    /**
     * @see GuiGraphicsExtractor#itemDecorations(Font, ItemStack, int, int, String)
     */
    public static void extractItemDecorations(GuiGraphicsExtractor guiGraphics, Font font, ItemStack itemStack, int itemPosX, int itemPosY) {
        if (!ItemInteractions.CONFIG.get(ClientConfig.class).itemStorageIndicator) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.screen instanceof AbstractContainerScreen<?> screen)) {
            return;
        }

        ItemStorageHolder holder = ItemStorageHolder.ofItem(itemStack);
        if (holder.allowModification(itemStack, minecraft.player) && allowSlotModification(slotBeingRendered,
                itemStack,
                minecraft.player)) {
            ItemStack itemHeldByCursor = screen.getMenu().getCarried();
            if (itemStack != itemHeldByCursor) {
                ItemDecorationsType type = ItemDecorationsType.pickType(holder,
                        itemStack,
                        itemHeldByCursor,
                        minecraft.player);
                if (type != null) {
                    type.extractRenderState(guiGraphics, font, itemPosX, itemPosY);
                }
            }
        }
    }

    /**
     * Prevent rendering on items used as icons for creative mode tabs and for backpacks in locked slots (like the Inmis
     * mod).
     */
    public static boolean allowSlotModification(@Nullable Slot slot, ItemStack itemStack, Player player) {
        if (slot == null || slot.getItem() != itemStack) {
            return false;
        } else if (!slot.allowModification(player)) {
            return false;
        } else if (slot instanceof CreativeModeInventoryScreen.CustomCreativeSlot) {
            // The creative mode item list should not be interactable in any way.
            return false;
        } else {
            return true;
        }
    }

    public static void setSlotBeingRendered(@Nullable Slot slotBeingRendered) {
        ItemDecorationsHelper.slotBeingRendered = slotBeingRendered;
    }
}
