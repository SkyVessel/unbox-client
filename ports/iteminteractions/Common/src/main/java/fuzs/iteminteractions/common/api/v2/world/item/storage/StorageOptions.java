package fuzs.iteminteractions.common.api.v2.world.item.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Common options for item storages controlling which items may be inserted.
 *
 * @param items                the items that are filtered, empty for no filter
 * @param disallowed           are the items in {@code items} disallowed instead of allowed
 * @param filterContainerItems are items that cannot fit inside container items rejected
 */
public record StorageOptions(Optional<HolderSet<Item>> items, boolean disallowed, boolean filterContainerItems) {
    /**
     * The default options that allow all items to be inserted.
     */
    public static final StorageOptions DEFAULT = new StorageOptions(Optional.empty(), true, true);
    /**
     * Codec for a {@link StorageOptions} instance.
     */
    public static final Codec<StorageOptions> CODEC = RecordCodecBuilder.create((RecordCodecBuilder.Instance<StorageOptions> instance) -> instance.group(
            RegistryCodecs.homogeneousList(Registries.ITEM)
                    .lenientOptionalFieldOf("items")
                    .forGetter(StorageOptions::items),
            Codec.BOOL.fieldOf("disallowed").orElse(Boolean.TRUE).forGetter(StorageOptions::disallowed),
            Codec.BOOL.fieldOf("filter_container_items")
                    .orElse(Boolean.TRUE)
                    .forGetter(StorageOptions::filterContainerItems)).apply(instance, StorageOptions::new));

    /**
     * Create a copy of these options with container items filtered.
     *
     * @return the updated options
     *
     * @deprecated use {@link #setFilterContainerItems(boolean)} instead
     */
    @Deprecated
    public StorageOptions setFilterContainerItems() {
        return this.setFilterContainerItems(true);
    }

    /**
     * Create a copy of these options with a new value for filtering container items.
     *
     * @param filterContainerItems are items that cannot fit inside container items rejected
     * @return the updated options
     */
    public StorageOptions setFilterContainerItems(boolean filterContainerItems) {
        return this.filterContainerItems() == filterContainerItems ? this :
                new StorageOptions(this.items, this.disallowed, filterContainerItems);
    }

    /**
     * Is the provided item stack allowed to be inserted.
     *
     * @param itemStack the item stack
     * @return is the item stack allowed to be inserted
     *
     * @see net.minecraft.world.inventory.Slot#mayPlace(ItemStack)
     */
    public boolean mayPlace(ItemStack itemStack) {
        if (!this.canFitInsideContainerItems(itemStack)) {
            return false;
        } else if (!this.disallowed) {
            return this.items.isEmpty() || this.items.filter(itemStack::is).isPresent();
        } else {
            return this.items.filter(itemStack::is).isEmpty();
        }
    }

    private boolean canFitInsideContainerItems(ItemStack itemStack) {
        return !this.filterContainerItems() || itemStack.getItem().canFitInsideContainerItems();
    }
}
