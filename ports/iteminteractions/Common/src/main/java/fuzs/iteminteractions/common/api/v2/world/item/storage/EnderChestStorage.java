package fuzs.iteminteractions.common.api.v2.world.item.storage;

import com.mojang.serialization.MapCodec;
import fuzs.iteminteractions.common.api.v2.world.inventory.tooltip.ItemContentsTooltip;
import fuzs.iteminteractions.common.impl.handler.EnderChestSyncHandler;
import fuzs.iteminteractions.common.impl.init.ModRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ARGB;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A {@link VisualItemStorage} implementation backed by the player's ender chest inventory.
 */
public class EnderChestStorage implements VisualItemStorage {
    /**
     * The ender chest instance.
     */
    public static final ItemStorage INSTANCE = new EnderChestStorage();
    /**
     * Codec for {@link #INSTANCE}.
     */
    public static final MapCodec<ItemStorage> CODEC = MapCodec.unit(INSTANCE);
    /**
     * Ender color from the <a href="https://www.curseforge.com/minecraft/mc-mods/tinted">Tinted</a> mod.
     */
    private static final int BACKGROUND_COLOR = ARGB.opaque(0X2A6255);

    protected EnderChestStorage() {
        // NO-OP
    }

    @Override
    public boolean allowModification(ItemStack itemStack, Player player) {
        return true;
    }

    @Override
    public boolean hasContents(ItemStack itemStack, Player player) {
        return !this.getReadOnlyContainer(itemStack, player).isEmpty();
    }

    @Override
    public SimpleContainer getWritableContainer(ItemStack itemStack, Player player, @Nullable SlotAccess slotAccess) {
        return player.getEnderChestInventory();
    }

    @Override
    public int getGridWidth(int itemCount) {
        return 9;
    }

    @Override
    public int getGridHeight(int itemCount) {
        int gridWidth = this.getGridWidth(itemCount);
        // Attempt to support mods that add more ender chest rows, like the Carpet mod.
        if (itemCount % gridWidth == 0) {
            return itemCount / gridWidth;
        } else {
            return 3;
        }
    }

    @Override
    public TooltipComponent createTooltipImageComponent(ItemStack itemStack, Player player, NonNullList<ItemStack> itemList) {
        return new ItemContentsTooltip(itemList,
                this.getSelectedItem(itemStack, player),
                this.getGridWidth(itemList.size()),
                this.getGridHeight(itemList.size()),
                BACKGROUND_COLOR);
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
    public void broadcastChangesOnContainerMenu(ItemStack itemStack, Player player) {
        if (player.level().isClientSide()) {
            // Will only actually broadcast when in the creative menu as that menu needs manual syncing.
            EnderChestSyncHandler.broadcastCreativeState(player);
        } else {
            // Sync the full state, the client ender chest will otherwise likely be messed up.
            // Useful for nested ender chests when paired with packet spam and latency.
            EnderChestSyncHandler.broadcastFullState((ServerPlayer) player);
        }
    }

    @Override
    public ItemStorageType<?> getType() {
        return ModRegistry.ENDER_CHEST_ITEM_STORAGE_TYPE.value();
    }
}
