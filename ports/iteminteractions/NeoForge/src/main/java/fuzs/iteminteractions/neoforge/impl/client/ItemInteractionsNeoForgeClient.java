package fuzs.iteminteractions.neoforge.impl.client;

import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.client.ItemInteractionsClient;
import fuzs.iteminteractions.common.impl.data.client.ModLanguageProvider;
import fuzs.puzzleslib.common.api.client.core.v1.ClientModConstructor;
import fuzs.puzzleslib.neoforge.api.data.v2.core.DataProviderHelper;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

@Mod(value = ItemInteractions.MOD_ID, dist = Dist.CLIENT)
public class ItemInteractionsNeoForgeClient {

    public ItemInteractionsNeoForgeClient() {
        ClientModConstructor.construct(ItemInteractions.MOD_ID, ItemInteractionsClient::new);
        DataProviderHelper.registerDataProviders(ItemInteractions.MOD_ID, ModLanguageProvider::new);
    }
}
