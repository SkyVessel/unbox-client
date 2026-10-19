package fuzs.iteminteractions.common.impl.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

/**
 * An extension for {@link ItemSlotMouseAction} that is also passed the
 * {@link AbstractContainerMenu#getCarried() carried item} along with hooking into more screen methods.
 */
public interface CustomItemSlotMouseAction extends ItemSlotMouseAction {

    default boolean matches(Slot slot) {
        return this.matches(slot.getItem());
    }

    boolean matches(ItemStack itemStack);

    /**
     * This always runs regardless of {@link #matches(ItemStack)}
     */
    void onExtractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks);

    /**
     * This always runs regardless of {@link #matches(ItemStack)}
     */
    void onExtractForeground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks);

    boolean onMouseClicked(MouseButtonEvent event, OptionalInt slotIndex, ItemStack itemStack);

    boolean onMouseReleased(MouseButtonEvent event, OptionalInt slotIndex, ItemStack itemStack);

    boolean onMouseDragged(MouseButtonEvent event, double dragX, double dragY, OptionalInt slotIndex, ItemStack itemStack);

    boolean isDragging();

    default boolean onMouseScrolled(double scrollX, double scrollY, int slotIndex, ItemStack itemStack) {
        return this.onMouseScrolled(scrollX, scrollY, OptionalInt.of(slotIndex), itemStack);
    }

    boolean onMouseScrolled(double scrollX, double scrollY, OptionalInt slotIndex, ItemStack itemStack);

    boolean onKeyPressed(KeyEvent event, OptionalInt slotIndex, ItemStack itemStack);
}
