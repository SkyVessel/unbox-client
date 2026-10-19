package fuzs.iteminteractions.common.api.v2.world.item.storage;

import fuzs.iteminteractions.common.impl.world.item.container.ItemStorageManager;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A holder class for individual {@link ItemStorage} instances, mainly to include additional checks when calling various
 * methods.
 *
 * @param storage the wrapped {@link ItemStorage}
 */
public record ItemStorageHolder(ItemStorage storage) {
    /**
     * The empty instance.
     */
    public static final ItemStorageHolder EMPTY = new ItemStorageHolder(VoidStorage.INSTANCE);

    /**
     * Get the holder for an item stack.
     *
     * @param itemStack the item stack
     * @return the holder that may be empty
     */
    public static ItemStorageHolder ofItem(ItemStack itemStack) {
        return itemStack.isEmpty() ? EMPTY : ItemStorageManager.getHolder(itemStack);
    }

    /**
     * Get the holder for an item storage, or the empty holder when it is {@code null}.
     *
     * @param storage the item storage
     * @return the holder that may be empty
     */
    public static ItemStorageHolder ofNullable(@Nullable ItemStorage storage) {
        return storage != null && storage != VoidStorage.INSTANCE ? new ItemStorageHolder(storage) : EMPTY;
    }

    /**
     * @return is the holder empty
     */
    public boolean isEmpty() {
        return this.storage == VoidStorage.INSTANCE;
    }

    /**
     * Does this provider support extracting and inserting items for the given item stack.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return are item inventory interactions allowed
     */
    public boolean allowModification(ItemStack itemStack, Player player) {
        return itemStack.getCount() == 1 && this.storage().allowModification(itemStack, player);
    }

    /**
     * Does the item stack have data for stored items.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return is the item stack data with stored items present
     */
    public boolean hasContents(ItemStack itemStack, Player player) {
        return itemStack.getCount() == 1 && this.storage().hasContents(itemStack, player);
    }

    /**
     * Called when the item stack providing the storage is stacked onto another item.
     *
     * @param itemStack   the item stack providing the storage
     * @param slot        access to the slot holding the item stack providing the storage
     * @param otherItem   the item stack the storage is stacked onto
     * @param otherSlot   the slot holding the item stack the storage is stacked onto
     * @param clickAction the click action
     * @param player      the player performing the interaction
     * @return was the interaction handled
     */
    public boolean overrideStackedOnOther(ItemStack itemStack, SlotAccess slot, ItemStack otherItem, Slot otherSlot, ClickAction clickAction, Player player) {
        return this.storage().overrideStackedOnOther(this, itemStack, slot, otherItem, otherSlot, clickAction, player);
    }

    /**
     * Called when another item stack is stacked onto the item stack providing the storage.
     *
     * @param itemStack   the item stack providing the storage
     * @param slot        the slot holding the item stack providing the storage
     * @param otherItem   the item stack stacked onto the storage
     * @param otherSlot   access to the slot holding the item stack stacked onto the storage
     * @param clickAction the click action
     * @param player      the player performing the interaction
     * @return was the interaction handled
     */
    public boolean overrideOtherStackedOnMe(ItemStack itemStack, Slot slot, ItemStack otherItem, SlotAccess otherSlot, ClickAction clickAction, Player player) {
        return this.storage()
                .overrideOtherStackedOnMe(this, itemStack, slot, otherItem, otherSlot, clickAction, player);
    }

    /**
     * Can the container item accept another item, checks for the player being allowed to interact as well as the
     * container item being able to hold the other item.
     *
     * @param itemStack the item stack providing the container to add {@code otherItem} to
     * @param otherItem the stack to be added to the container
     * @param player    the player interacting with both item stacks
     * @return can the item be added
     */
    public boolean canAcceptItem(ItemStack itemStack, ItemStack otherItem, Player player) {
        return !otherItem.isEmpty() && this.allowModification(itemStack, player) && this.storage().mayPlace(otherItem);
    }

    /**
     * Is there enough space in the container provided by the item stack providing the storage to add the provided
     * stack (not necessarily the full stack).
     *
     * @param itemStack the item stack providing the storage
     * @param otherItem the stack to be added to the container
     * @param player    the player interacting with both item stacks
     * @return is adding any portion of the stack to the container possible
     */
    public boolean canAddItem(ItemStack itemStack, ItemStack otherItem, Player player) {
        return this.canAcceptItem(itemStack, otherItem, player) && this.storage()
                .canAddItem(itemStack, otherItem, player);
    }

    /**
     * Is there any item of the same type as the provided stack already in the container provided by the item stack
     * providing the storage.
     *
     * @param itemStack        the item stack providing the storage
     * @param otherItem        the stack to be searched for in the container
     * @param player           the player interacting with both item stacks
     * @param isSameComponents should item data components be compared as well
     * @return is any item of the same type as the provided stack already in the container
     */
    public boolean hasAnyOf(ItemStack itemStack, ItemStack otherItem, Player player, boolean isSameComponents) {
        return this.canAcceptItem(itemStack, otherItem, player) && this.getItemContainer(itemStack, player)
                .hasAnyMatching((ItemStack item) -> {
                    if (isSameComponents) {
                        return ItemStack.isSameItemSameComponents(item, otherItem);
                    } else {
                        return ItemStack.isSameItem(item, otherItem);
                    }
                });
    }

    /**
     * How much space is available in the container provided by the item stack providing the storage to add the provided
     * stack.
     *
     * @param itemStack the item stack providing the storage
     * @param otherItem the stack to be added to the container
     * @param player    the player interacting with both item stacks
     * @return the portion of the stack that can be added to the container
     */
    public int getAcceptableItemCount(ItemStack itemStack, ItemStack otherItem, Player player) {
        if (this.canAcceptItem(itemStack, otherItem, player)) {
            return this.storage().getAcceptableItemCount(itemStack, otherItem, player);
        } else {
            return 0;
        }
    }

    /**
     * Get the container implementation provided by the item stack providing the storage.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the provided container
     */
    public Container getItemContainer(ItemStack itemStack, Player player) {
        return this.storage().getReadOnlyContainer(itemStack, player);
    }

    /**
     * Count the total number of items inside the container provided by the item stack providing the storage.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the total number of items inside the container
     */
    public int getContentsCount(ItemStack itemStack, Player player) {
        if (this.hasContents(itemStack, player)) {
            int count = 0;
            for (ItemStack item : this.getItemContainer(itemStack, player)) {
                count += item.getCount();
            }

            return count;
        } else {
            return 0;
        }
    }

    /**
     * Get the tooltip image for the item stack providing the storage.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the tooltip image if present
     *
     * @see net.minecraft.world.item.Item#getTooltipImage(ItemStack)
     */
    public Optional<Optional<TooltipComponent>> getTooltipImage(ItemStack itemStack, Player player) {
        return this.hasContents(itemStack, player) ? this.storage().getTooltipImage(itemStack, player) :
                Optional.empty();
    }

    /**
     * Is a bar visible for the item stack providing the storage, indicating how full the container is.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return is the bar visible
     *
     * @see net.minecraft.world.item.Item#isBarVisible(ItemStack)
     */
    public Optional<Boolean> isBarVisible(ItemStack itemStack, Player player) {
        return this.hasContents(itemStack, player) ? this.storage().isBarVisible(itemStack, player) : Optional.empty();
    }

    /**
     * Get the width of the bar for the item stack providing the storage.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the bar width
     *
     * @see net.minecraft.world.item.Item#getBarWidth(ItemStack)
     */
    public OptionalInt getBarWidth(ItemStack itemStack, Player player) {
        return this.hasContents(itemStack, player) ? this.storage().getBarWidth(itemStack, player) :
                OptionalInt.empty();
    }

    /**
     * Get the color of the bar for the item stack providing the storage.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the bar color
     *
     * @see net.minecraft.world.item.Item#getBarColor(ItemStack)
     */
    public OptionalInt getBarColor(ItemStack itemStack, Player player) {
        return this.hasContents(itemStack, player) ? this.storage().getBarColor(itemStack, player) :
                OptionalInt.empty();
    }
}
