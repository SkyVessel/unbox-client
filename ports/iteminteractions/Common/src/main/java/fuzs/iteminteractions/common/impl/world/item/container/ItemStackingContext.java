package fuzs.iteminteractions.common.impl.world.item.container;

import fuzs.iteminteractions.common.api.v2.world.item.storage.ContainerItemStorage;
import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorageHolder;
import fuzs.iteminteractions.common.impl.world.inventory.ItemSlot;
import fuzs.iteminteractions.common.impl.world.item.component.SelectedItem;
import it.unimi.dsi.fastutil.ints.IntLinkedOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.joml.Vector2i;

public final class ItemStackingContext {
    private final ItemStorageHolder holder;
    private final ContainerItemStorage storage;
    private final Player player;

    public ItemStackingContext(ItemStorageHolder holder, ContainerItemStorage storage, Player player) {
        this.holder = holder;
        this.storage = storage;
        this.player = player;
    }

    /**
     * @see BundleContents.Mutable#tryInsert(ItemStack)
     */
    public int tryInsert(ItemStack itemStack, ItemStack otherItem, SlotAccess slotAccess) {
        return this.tryInsert(itemStack,
                otherItem,
                this.storage.getPrioritizedSlot(itemStack, this.player),
                slotAccess);
    }

    public int tryInsert(ItemStack itemStack, ItemStack otherItem, int prioritizedSlot, SlotAccess slotAccess) {
        int countLimit = this.getInsertCountLimit(itemStack, otherItem);
        if (countLimit > 0 && otherItem.getCount() > 0) {
            Container container = this.storage.getWritableContainer(itemStack, this.player, slotAccess);
            ItemStack item = otherItem.copyWithCount(countLimit);
            ItemSlot itemSlot = this.addItem(container, item, prioritizedSlot);
            this.storage.setPrioritizedSlot(itemStack, this.player, itemSlot.slotNum());
            return item.getCount() - itemSlot.item().getCount();
        } else {
            return 0;
        }
    }

    private int getInsertCountLimit(ItemStack itemStack, ItemStack otherItem) {
        int countLimit = Math.min(otherItem.getCount(),
                this.holder.getAcceptableItemCount(itemStack, otherItem, this.player));
        return this.storage.extractSingleItemOnly(itemStack, this.player) ? Math.min(1, countLimit) : countLimit;
    }

    /**
     * @see BundleContents.Mutable#removeOne()
     */
    public ItemSlot removeOne(ItemStack itemStack, ItemStack otherItem, SlotAccess slotAccess) {
        Container container = this.storage.getWritableContainer(itemStack, this.player, slotAccess);
        int slotNum = this.updatePrioritizedSlot(container, itemStack, otherItem);
        if (slotNum != SelectedItem.DEFAULT_SELECTED_ITEM) {
            ItemStack slotItem = container.getItem(slotNum);
            int removalCount = this.getRemoveCountLimit(itemStack, slotItem);
            return new ItemSlot(container.removeItem(slotNum, removalCount), slotNum);
        } else {
            return ItemSlot.EMPTY;
        }
    }

    private int getRemoveCountLimit(ItemStack itemStack, ItemStack otherItem) {
        int countLimit = otherItem.getCount();
        return this.storage.extractSingleItemOnly(itemStack, this.player) ? Math.min(1, countLimit) : countLimit;
    }

    private int updatePrioritizedSlot(Container container, ItemStack itemStack, ItemStack otherItem) {
        int prioritizedSlot = this.updatePreviousPrioritizedSlot(container, itemStack, otherItem);
        if (prioritizedSlot != SelectedItem.DEFAULT_SELECTED_ITEM) {
            return prioritizedSlot;
        }

        int offsetDirection = this.storage.getRemovalDirection().getStep();
        int startNum = (-1 + offsetDirection) / 2;
        for (int i = 0; i < container.getContainerSize(); i++) {
            int slotNum = Mth.positiveModulo(startNum + i * offsetDirection, container.getContainerSize());
            ItemStack slotItem = container.getItem(slotNum);
            if (!slotItem.isEmpty() && this.canCombineItemsInSlot(otherItem, container, slotNum, slotItem)) {
                // When we empty the slot, cycle to a different one.
                if (slotItem.getCount() <= this.getRemoveCountLimit(itemStack, slotItem)) {
                    this.storage.setPrioritizedSlot(itemStack, this.player, SelectedItem.DEFAULT_SELECTED_ITEM);
                } else {
                    // Otherwise, when not empty, set this as the newly selected item.
                    this.storage.setPrioritizedSlot(itemStack, this.player, slotNum);
                }

                return slotNum;
            }
        }

        return SelectedItem.DEFAULT_SELECTED_ITEM;
    }

    private int updatePreviousPrioritizedSlot(Container container, ItemStack itemStack, ItemStack otherItem) {
        int prioritizedSlot = this.storage.getPrioritizedSlot(itemStack, this.player);
        if (prioritizedSlot >= 0 && prioritizedSlot < container.getContainerSize()) {
            ItemStack slotItem = container.getItem(prioritizedSlot);
            if (!slotItem.isEmpty() && this.canCombineItemsInSlot(otherItem, container, prioritizedSlot, slotItem)) {
                // When we empty the slot, cycle to a different one.
                if (slotItem.getCount() <= this.getRemoveCountLimit(itemStack, slotItem)) {
                    int updatedSelectedItem = this.storage.scrollSelectedItem(itemStack,
                            this.player,
                            container,
                            new Vector2i(this.storage.getRemovalDirection().getStep(), 0));
                    this.storage.setPrioritizedSlot(itemStack, this.player, updatedSelectedItem);
                }

                return prioritizedSlot;
            }
        }

        return SelectedItem.DEFAULT_SELECTED_ITEM;
    }

    private boolean canCombineItemsInSlot(ItemStack itemStack, Container container, int slotNum, ItemStack slotItem) {
        return itemStack.isEmpty() || (ItemStack.isSameItemSameComponents(itemStack, slotItem)
                && itemStack.getCount() < this.storage.getMaxStackSize(container, slotNum, slotItem));
    }

    /**
     * @see net.minecraft.world.SimpleContainer#addItem(ItemStack)
     */
    private ItemSlot addItem(Container container, ItemStack itemStack, int prioritizedSlot) {
        ItemStack remainingItems = itemStack.copy();
        int slotNum = this.moveItemToOccupiedSlotsWithSameType(container, remainingItems, prioritizedSlot);
        if (remainingItems.isEmpty()) {
            return new ItemSlot(slotNum);
        } else {
            slotNum = this.moveItemToEmptySlots(container, remainingItems, slotNum);
            if (remainingItems.isEmpty()) {
                return new ItemSlot(slotNum);
            } else {
                return new ItemSlot(remainingItems, slotNum);
            }
        }
    }

    /**
     * @see net.minecraft.world.SimpleContainer#moveItemToEmptySlots(ItemStack)
     */
    private int moveItemToEmptySlots(Container container, ItemStack sourceStack, int prioritizedSlot) {
        IntSet slotNums = IntLinkedOpenHashSet.of(prioritizedSlot);
        slotNums.addAll(IntSets.fromTo(0, container.getContainerSize()));
        int lastUsedSlot = SelectedItem.DEFAULT_SELECTED_ITEM;
        for (int slotNum : slotNums.toIntArray()) {
            if (slotNum >= 0 && slotNum < container.getContainerSize()) {
                ItemStack targetStack = container.getItem(slotNum);
                if (targetStack.isEmpty()) {
                    // We cannot assume a picked-up item stack fits into a single slot, split if necessary.
                    int maxStackSize = this.storage.getMaxStackSize(container, slotNum, sourceStack);
                    if (maxStackSize <= 0) {
                        continue;
                    }

                    int count = Math.min(sourceStack.getCount(), maxStackSize);
                    container.setItem(slotNum, sourceStack.split(count));
                    lastUsedSlot = slotNum;
                    if (sourceStack.isEmpty()) {
                        break;
                    }
                }
            }
        }

        return lastUsedSlot;
    }

    /**
     * @see net.minecraft.world.SimpleContainer#moveItemToOccupiedSlotsWithSameType(ItemStack)
     */
    private int moveItemToOccupiedSlotsWithSameType(Container container, ItemStack sourceStack, int prioritizedSlot) {
        IntSet slotNums = IntLinkedOpenHashSet.of(prioritizedSlot);
        slotNums.addAll(IntSets.fromTo(0, container.getContainerSize()));
        for (int slotNum : slotNums.toIntArray()) {
            if (slotNum >= 0 && slotNum < container.getContainerSize()) {
                ItemStack targetStack = container.getItem(slotNum);
                if (ItemStack.isSameItemSameComponents(targetStack, sourceStack)) {
                    this.moveItemsBetweenStacks(container, sourceStack, targetStack, slotNum);
                    if (sourceStack.isEmpty()) {
                        return slotNum;
                    }
                }
            }
        }

        return SelectedItem.DEFAULT_SELECTED_ITEM;
    }

    /**
     * @see net.minecraft.world.SimpleContainer#moveItemsBetweenStacks(ItemStack, ItemStack)
     */
    private void moveItemsBetweenStacks(Container container, ItemStack sourceStack, ItemStack targetStack, int slotNum) {
        int maxCount = this.storage.getMaxStackSize(container, slotNum, targetStack);
        int diff = Math.min(sourceStack.getCount(), maxCount - targetStack.getCount());
        if (diff > 0) {
            targetStack.grow(diff);
            sourceStack.shrink(diff);
            container.setChanged();
        }
    }
}
