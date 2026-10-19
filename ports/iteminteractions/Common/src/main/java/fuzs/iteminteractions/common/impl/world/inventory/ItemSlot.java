package fuzs.iteminteractions.common.impl.world.inventory;

import fuzs.iteminteractions.common.impl.world.item.component.SelectedItem;
import net.minecraft.world.item.ItemStack;

public record ItemSlot(ItemStack item, int slotNum) {
    public static final ItemSlot EMPTY = new ItemSlot(SelectedItem.DEFAULT_SELECTED_ITEM);

    public ItemSlot(int slotNum) {
        this(ItemStack.EMPTY, slotNum);
    }
}
