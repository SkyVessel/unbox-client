package fuzs.iteminteractions.common.impl.data;

import fuzs.iteminteractions.common.api.v2.data.AbstractItemStorageDefinitionsProvider;
import fuzs.iteminteractions.common.api.v2.world.item.storage.BundleContentsStorage;
import fuzs.iteminteractions.common.api.v2.world.item.storage.ContainerStorage;
import fuzs.iteminteractions.common.api.v2.world.item.storage.EnderChestStorage;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.puzzleslib.common.api.data.v2.core.DataProviderContext;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Items;

public class DynamicItemStorageDefinitionsProvider extends AbstractItemStorageDefinitionsProvider {

    public DynamicItemStorageDefinitionsProvider(DataProviderContext context) {
        super(context);
    }

    @Override
    public void addItemStorageDefinitions(HolderLookup.Provider registries) {
        this.add(EnderChestStorage.INSTANCE, Items.ENDER_CHEST);
        this.add(registries.lookupOrThrow(Registries.ITEM), new ContainerStorage(), ItemTags.SHULKER_BOXES);
        this.add(ItemInteractions.id("bundle"), new BundleContentsStorage(), Items.BUNDLE);
    }
}
