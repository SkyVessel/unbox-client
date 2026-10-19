package fuzs.iteminteractions.fabric.impl.client;

import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.client.ItemInteractionsClient;
import fuzs.puzzleslib.common.api.client.core.v1.ClientModConstructor;
import net.fabricmc.api.ClientModInitializer;

public class ItemInteractionsFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientModConstructor.construct(ItemInteractions.MOD_ID, ItemInteractionsClient::new);
    }
}
