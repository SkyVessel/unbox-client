package fuzs.iteminteractions.common.api.v2.world.item.storage;

import fuzs.iteminteractions.common.impl.config.ClickActionScheme;
import fuzs.iteminteractions.common.impl.init.ModRegistry;
import fuzs.iteminteractions.common.impl.world.inventory.ItemSlot;
import fuzs.iteminteractions.common.impl.world.item.component.ControlScheme;
import fuzs.iteminteractions.common.impl.world.item.component.SelectedItem;
import fuzs.iteminteractions.common.impl.world.item.container.ItemStackingContext;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2ic;
import org.jspecify.annotations.Nullable;

/**
 * An {@link ItemStorage} implementation backed by a {@link Container} that arranges its contents in a grid for the
 * tooltip preview.
 */
public interface ContainerItemStorage extends ItemStorage {

    /**
     * Get the number of columns of the container grid.
     *
     * @param itemCount the number of items in the container
     * @return the grid width
     */
    int getGridWidth(int itemCount);

    /**
     * Get the number of rows of the container grid.
     *
     * @param itemCount the number of items in the container
     * @return the grid height
     */
    int getGridHeight(int itemCount);

    /**
     * Is only a single item moved at a time, instead of the full stack.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return is a single item moved at a time
     */
    default boolean extractSingleItemOnly(ItemStack itemStack, Player player) {
        return ModRegistry.CONTROL_SCHEME_ATTACHMENT_TYPE.getOrDefault(player, ControlScheme.DEFAULT).moveSingleItem();
    }

    private ClickActionScheme controlScheme(ItemStack itemStack, Player player) {
        if (this.extractSingleItemOnly(itemStack, player)) {
            return ClickActionScheme.SPLIT_INPUT;
        } else {
            return ModRegistry.CONTROL_SCHEME_ATTACHMENT_TYPE.getOrDefault(player, ControlScheme.DEFAULT)
                    .controlScheme();
        }
    }

    @Override
    default int getSelectedItem(ItemStack itemStack, Player player) {
        return ModRegistry.SELECTED_ITEM_ATTACHMENT_TYPE.getOrDefault(player, SelectedItem.DEFAULT).selectedItem();
    }

    /**
     * Set the selected item index inside the container.
     *
     * @param itemStack    the item stack providing the storage
     * @param player       the player performing the interaction
     * @param selectedItem the new selected item index
     */
    default void setSelectedItem(ItemStack itemStack, Player player, int selectedItem) {
        ModRegistry.SELECTED_ITEM_ATTACHMENT_TYPE.set(player, SelectedItem.of(selectedItem));
    }

    @Override
    default void toggleSelectedItem(ItemStack itemStack, Player player, int selectedItem, boolean slotClicked) {
        if (!slotClicked) {
            this.setSelectedItem(itemStack, player, selectedItem);
        }
    }

    /**
     * Get the slot index where inserted items are placed first, based on the selected item index and the removal
     * direction.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the prioritized slot index
     */
    default int getPrioritizedSlot(ItemStack itemStack, Player player) {
        int prioritizedSlot = this.getSelectedItem(itemStack, player);
        if (this.offsetPrioritizedSlot(prioritizedSlot)) {
            return prioritizedSlot + 1;
        } else {
            return prioritizedSlot;
        }
    }

    /**
     * Set the slot index where inserted items are placed first.
     *
     * @param itemStack       the item stack providing the storage
     * @param player          the player performing the interaction
     * @param prioritizedSlot the new prioritized slot index
     */
    default void setPrioritizedSlot(ItemStack itemStack, Player player, int prioritizedSlot) {
        this.setSelectedItem(itemStack,
                player,
                this.offsetPrioritizedSlot(prioritizedSlot) ? prioritizedSlot - 1 : prioritizedSlot);
    }

    /**
     * Is the prioritized slot index offset by one to account for the removal direction.
     *
     * @param prioritizedSlot the prioritized slot index
     * @return is the prioritized slot index offset
     */
    default boolean offsetPrioritizedSlot(int prioritizedSlot) {
        return prioritizedSlot != SelectedItem.DEFAULT_SELECTED_ITEM
                && this.getRemovalDirection() == Direction.AxisDirection.POSITIVE;
    }

    @Override
    default int scrollSelectedItem(ItemStack itemStack, Player player, Container container, Vector2ic scrollXY) {
        int selectedItem = this.getSelectedItem(itemStack, player);
        int gridWidth = this.getGridWidth(container.getContainerSize());
        int gridHeight = this.getGridHeight(container.getContainerSize());
        int gridSize = gridWidth * gridHeight;
        for (int slotNum = 0; slotNum < gridSize; slotNum++) {
            int x = selectedItem % gridWidth;
            int y = selectedItem / gridWidth;
            if (scrollXY.x() == 0) {
                y += Mth.sign(scrollXY.y());
                if (y < 0) {
                    y = gridHeight - 1;
                    x--;
                } else if (y >= gridHeight) {
                    y = 0;
                    x++;
                }

                x = Mth.positiveModulo(x, gridWidth);
            } else {
                x += Mth.sign(scrollXY.x());
                if (x < 0) {
                    x = gridWidth - 1;
                    y--;
                } else if (x >= gridWidth) {
                    x = 0;
                    y++;
                }

                y = Mth.positiveModulo(y, gridHeight);
            }

            selectedItem = y * gridWidth + x;
            if (selectedItem >= 0 && selectedItem < container.getContainerSize() && !container.getItem(selectedItem)
                    .isEmpty()) {
                return selectedItem;
            }
        }

        return SelectedItem.DEFAULT_SELECTED_ITEM;
    }

    @Override
    default boolean mayPlace(ItemStack otherItem) {
        return true;
    }

    @Override
    default SimpleContainer getReadOnlyContainer(ItemStack itemStack, Player player) {
        return this.getWritableContainer(itemStack, player, null);
    }

    @Override
    SimpleContainer getWritableContainer(ItemStack itemStack, Player player, @Nullable SlotAccess slotAccess);

    @Override
    default boolean canAddItem(ItemStack itemStack, ItemStack otherItem, Player player) {
        return this.getReadOnlyContainer(itemStack, player).canAddItem(otherItem);
    }

    @Override
    default int getAcceptableItemCount(ItemStack itemStack, ItemStack otherItem, Player player) {
        return otherItem.getCount();
    }

    /**
     * @see net.minecraft.world.item.BundleItem#overrideStackedOnOther(ItemStack, Slot, ClickAction, Player)
     */
    @Override
    default boolean overrideStackedOnOther(ItemStorageHolder holder, ItemStack itemStack, SlotAccess slot, ItemStack otherItem, Slot otherSlot, ClickAction clickAction, Player player) {
        boolean extractSingleItemOnly = this.extractSingleItemOnly(itemStack, player);
        ClickActionScheme scheme = this.controlScheme(itemStack, player);
        ItemStackingContext context = new ItemStackingContext(holder, this, player);
        if (scheme.removeStackedOnOther(clickAction) && (otherItem.isEmpty() || extractSingleItemOnly)) {
            ItemSlot itemSlot = context.removeOne(itemStack, otherItem, slot);
            if (!itemSlot.item().isEmpty()) {
                context.tryInsert(itemStack, otherSlot.safeInsert(itemSlot.item()), itemSlot.slotNum(), slot);
                if (!extractSingleItemOnly) {
                    this.playRemoveOneSound(player);
                }
            }

            this.broadcastChangesOnContainerMenu(itemStack, player);
            return true;
        } else if (scheme.insertStackedOnOther(clickAction) && (!otherItem.isEmpty() || extractSingleItemOnly)) {
            if (!otherItem.isEmpty() && !holder.canAcceptItem(itemStack, otherItem, player)) {
                return false;
            }

            otherItem = otherSlot.safeTake(otherItem.getCount(), otherItem.getCount(), player);
            int transferredCount = context.tryInsert(itemStack, otherItem, slot);
            otherItem.shrink(transferredCount);
            if (!extractSingleItemOnly) {
                if (transferredCount > 0) {
                    this.playInsertSound(player);
                } else {
                    this.playInsertFailSound(player);
                }
            }

            otherSlot.safeInsert(otherItem);
            this.broadcastChangesOnContainerMenu(itemStack, player);
            return true;
        } else {
            return false;
        }
    }

    /**
     * @see net.minecraft.world.item.BundleItem#overrideOtherStackedOnMe(ItemStack, ItemStack, Slot, ClickAction,
     *         Player, SlotAccess)
     */
    @Override
    default boolean overrideOtherStackedOnMe(ItemStorageHolder holder, ItemStack itemStack, Slot slot, ItemStack otherItem, SlotAccess otherSlot, ClickAction clickAction, Player player) {
        boolean extractSingleItemOnly = this.extractSingleItemOnly(itemStack, player);
        ClickActionScheme scheme = this.controlScheme(itemStack, player);
        if (clickAction == ClickAction.PRIMARY && otherItem.isEmpty()) {
            if (!extractSingleItemOnly) {
                this.toggleSelectedItem(itemStack, player, SelectedItem.DEFAULT_SELECTED_ITEM, true);
                slot.set(itemStack);
                return false;
            } else {
                return true;
            }
        } else {
            ItemStackingContext context = new ItemStackingContext(holder, this, player);
            SlotAccess slotAccess = SlotAccess.of(slot::getItem, slot::set);
            if (scheme.removeOtherStackedOnMe(clickAction) && (otherItem.isEmpty() || extractSingleItemOnly)) {
                if (slot.allowModification(player)) {
                    ItemStack itemRemainder = context.removeOne(itemStack, otherItem, slotAccess).item();
                    if (!itemRemainder.isEmpty()) {
                        // When extracting single items only, the item held by cursor may not be empty, so we cannot just replace it straight away.
                        if (otherItem.isEmpty()) {
                            otherSlot.set(itemRemainder);
                        } else {
                            otherItem.grow(itemRemainder.getCount());
                        }

                        if (!extractSingleItemOnly) {
                            this.playRemoveOneSound(player);
                        }
                    }
                }

                slot.set(itemStack);
                this.broadcastChangesOnContainerMenu(itemStack, player);
                return true;
            } else if (scheme.insertOtherStackedOnMe(clickAction) && !otherItem.isEmpty()) {
                if (slot.allowModification(player)) {
                    int transferredCount = context.tryInsert(itemStack, otherItem, slotAccess);
                    otherItem.shrink(transferredCount);
                    if (!extractSingleItemOnly) {
                        if (transferredCount > 0) {
                            this.playInsertSound(player);
                        } else {
                            this.playInsertFailSound(player);
                        }
                    }
                }

                slot.set(itemStack);
                this.broadcastChangesOnContainerMenu(itemStack, player);
                return true;
            } else {
                this.toggleSelectedItem(itemStack, player, SelectedItem.DEFAULT_SELECTED_ITEM, true);
                slot.set(itemStack);
                return false;
            }
        }
    }

    /**
     * Used to synchronize item storage changes.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     *
     * @see net.minecraft.world.item.BundleItem#broadcastChangesOnContainerMenu(Player)
     */
    default void broadcastChangesOnContainerMenu(ItemStack itemStack, Player player) {
        player.containerMenu.slotsChanged(player.getInventory());
    }

    /**
     * Get the direction in which the container is traversed when removing items.
     *
     * @return the removal direction
     */
    default Direction.AxisDirection getRemovalDirection() {
        return Direction.AxisDirection.NEGATIVE;
    }

    /**
     * Get the maximum stack size for an item stack in the container.
     *
     * @param container the container
     * @param slotNum   the slot index
     * @param itemStack the item stack
     * @return the max stack size
     */
    default int getMaxStackSize(Container container, int slotNum, ItemStack itemStack) {
        return container.getMaxStackSize(itemStack);
    }

    /**
     * @see net.minecraft.world.item.BundleItem#playRemoveOneSound(Entity)
     */
    @Override
    default void playRemoveOneSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    /**
     * @see net.minecraft.world.item.BundleItem#playInsertSound(Entity)
     */
    @Override
    default void playInsertSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
    }

    /**
     * Plays a sound when inserting an item into the container fails.
     *
     * @param player the player performing the interaction
     *
     * @see net.minecraft.world.item.BundleItem#playInsertFailSound(Entity)
     */
    default void playInsertFailSound(Player player) {
        player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
    }
}
