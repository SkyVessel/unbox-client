package fuzs.iteminteractions.fabric.impl;

import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.puzzleslib.common.api.core.v1.ModConstructor;
import net.fabricmc.api.ModInitializer;

public class ItemInteractionsFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        ModConstructor.construct(ItemInteractions.MOD_ID, ItemInteractions::new);
    }
}
