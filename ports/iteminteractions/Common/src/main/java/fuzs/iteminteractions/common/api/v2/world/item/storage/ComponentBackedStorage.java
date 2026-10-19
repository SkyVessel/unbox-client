package fuzs.iteminteractions.common.api.v2.world.item.storage;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

/**
 * A {@link VisualItemStorage} implementation backed by a data component that stores common options in a
 * {@link StorageOptions} instance.
 */
public abstract class ComponentBackedStorage implements VisualItemStorage {
    final StorageOptions storageOptions;

    /**
     * @param storageOptions the common options for this storage
     */
    public ComponentBackedStorage(StorageOptions storageOptions) {
        this.storageOptions = storageOptions;
    }

    /**
     * Codec for a {@link StorageOptions} instance stored under the {@code storage_options} key.
     *
     * @param <T> the storage type
     * @return the record codec builder
     */
    protected static <T extends ComponentBackedStorage> RecordCodecBuilder<T, StorageOptions> itemContentsCodec() {
        return StorageOptions.CODEC.lenientOptionalFieldOf("storage_options", StorageOptions.DEFAULT)
                .forGetter((T provider) -> provider.storageOptions);
    }

    @Override
    public boolean mayPlace(ItemStack otherItem) {
        return this.storageOptions.mayPlace(otherItem);
    }
}
