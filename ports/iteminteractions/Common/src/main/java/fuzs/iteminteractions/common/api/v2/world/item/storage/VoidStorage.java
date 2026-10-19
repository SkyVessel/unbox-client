package fuzs.iteminteractions.common.api.v2.world.item.storage;

import com.mojang.serialization.MapCodec;
import fuzs.iteminteractions.common.impl.init.ModRegistry;
import fuzs.iteminteractions.common.impl.world.item.component.SelectedItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2ic;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A bare-bones {@link ItemStorage} implementation that does not accept any items, used when no provider is available.
 */
public class VoidStorage implements ItemStorage {
    /**
     * The empty instance.
     */
    public static final ItemStorage INSTANCE = new VoidStorage();
    /**
     * Codec for {@link #INSTANCE}.
     */
    public static final MapCodec<ItemStorage> CODEC = MapCodec.unit(INSTANCE);

    protected VoidStorage() {
        // NO-OP
    }

    @Override
    public boolean allowModification(ItemStack itemStack, Player player) {
        return false;
    }

    @Override
    public boolean hasContents(ItemStack itemStack, Player player) {
        return false;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStorageHolder holder, ItemStack itemStack, SlotAccess slot, ItemStack otherItem, Slot otherSlot, ClickAction clickAction, Player player) {
        return false;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStorageHolder holder, ItemStack itemStack, Slot slot, ItemStack otherItem, SlotAccess otherSlot, ClickAction clickAction, Player player) {
        return false;
    }

    @Override
    public boolean mayPlace(ItemStack otherItem) {
        return false;
    }

    @Override
    public boolean canAddItem(ItemStack itemStack, ItemStack otherItem, Player player) {
        return false;
    }

    @Override
    public Container getReadOnlyContainer(ItemStack itemStack, Player player) {
        return new SimpleContainer() {
            @Override
            public void setChanged() {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public Container getWritableContainer(ItemStack itemStack, Player player, SlotAccess slotAccess) {
        return new SimpleContainer();
    }

    @Override
    public int getAcceptableItemCount(ItemStack itemStack, ItemStack otherItem, Player player) {
        return 0;
    }

    @Override
    public Optional<Optional<TooltipComponent>> getTooltipImage(ItemStack itemStack, Player player) {
        return Optional.empty();
    }

    @Override
    public Optional<Boolean> isBarVisible(ItemStack itemStack, Player player) {
        return Optional.empty();
    }

    @Override
    public OptionalInt getBarWidth(ItemStack itemStack, Player player) {
        return OptionalInt.empty();
    }

    @Override
    public OptionalInt getBarColor(ItemStack itemStack, Player player) {
        return OptionalInt.empty();
    }

    @Override
    public int getSelectedItem(ItemStack itemStack, Player player) {
        return SelectedItem.DEFAULT_SELECTED_ITEM;
    }

    @Override
    public int scrollSelectedItem(ItemStack itemStack, Player player, Container container, Vector2ic scrollXY) {
        return this.getSelectedItem(itemStack, player);
    }

    @Override
    public void toggleSelectedItem(ItemStack itemStack, Player player, int selectedItem, boolean slotClicked) {
        // NO-OP
    }

    @Override
    public void playRemoveOneSound(Player player) {
        // NO-OP
    }

    @Override
    public void playInsertSound(Player player) {
        // NO-OP
    }

    @Override
    public ItemStorageType<?> getType() {
        return ModRegistry.EMPTY_ITEM_STORAGE_TYPE.value();
    }
}
