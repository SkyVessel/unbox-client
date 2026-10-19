package fuzs.iteminteractions.common.impl;

import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorage;
import fuzs.iteminteractions.common.impl.config.ClientConfig;
import fuzs.iteminteractions.common.impl.config.ServerConfig;
import fuzs.iteminteractions.common.impl.data.DynamicItemStorageDefinitionsProvider;
import fuzs.iteminteractions.common.impl.handler.EnderChestSyncHandler;
import fuzs.iteminteractions.common.impl.init.ModRegistry;
import fuzs.iteminteractions.common.impl.network.ClientboundEnderChestContentMessage;
import fuzs.iteminteractions.common.impl.network.ClientboundEnderChestSlotMessage;
import fuzs.iteminteractions.common.impl.network.ClientboundSyncItemStorage;
import fuzs.iteminteractions.common.impl.network.client.ServerboundContainerClientInputMessage;
import fuzs.iteminteractions.common.impl.network.client.ServerboundEnderChestContentMessage;
import fuzs.iteminteractions.common.impl.network.client.ServerboundSelectedItemMessage;
import fuzs.iteminteractions.common.impl.world.item.container.ItemStorageManager;
import fuzs.puzzleslib.common.api.config.v3.ConfigHolder;
import fuzs.puzzleslib.common.api.core.v1.ModConstructor;
import fuzs.puzzleslib.common.api.core.v1.ModLoaderEnvironment;
import fuzs.puzzleslib.common.api.core.v1.context.DataPackReloadListenersContext;
import fuzs.puzzleslib.common.api.core.v1.context.GameRegistriesContext;
import fuzs.puzzleslib.common.api.core.v1.context.PackRepositorySourcesContext;
import fuzs.puzzleslib.common.api.core.v1.context.PayloadTypesContext;
import fuzs.puzzleslib.common.api.event.v1.entity.player.AfterChangeDimensionCallback;
import fuzs.puzzleslib.common.api.event.v1.entity.player.ContainerEvents;
import fuzs.puzzleslib.common.api.event.v1.entity.player.PlayerCopyEvents;
import fuzs.puzzleslib.common.api.event.v1.entity.player.PlayerNetworkEvents;
import fuzs.puzzleslib.common.api.event.v1.server.ServerResourcesLoadCallback;
import fuzs.puzzleslib.common.api.event.v1.server.SyncDataPackContentsCallback;
import fuzs.puzzleslib.common.api.resources.v1.DynamicPackResources;
import fuzs.puzzleslib.common.api.resources.v1.PackResourcesHelper;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.ReloadableServerResources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemInteractions implements ModConstructor {
    public static final String MOD_ID = "iteminteractions";
    public static final String MOD_NAME = "Item Interactions";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);

    public static final ConfigHolder CONFIG = ConfigHolder.builder(MOD_ID)
            .client(ClientConfig.class)
            .server(ServerConfig.class);

    @Override
    public void onConstructMod() {
        ModRegistry.bootstrap();
        registerEventHandlers();
    }

    private static void registerEventHandlers() {
        SyncDataPackContentsCallback.EVENT.register(ItemStorageManager::onSyncDataPackContents);
        ServerResourcesLoadCallback.EVENT.register(ItemStorageManager::onServerResourcesLoad);
        ContainerEvents.OPEN.register(EnderChestSyncHandler::onContainerOpen);
        PlayerNetworkEvents.JOIN.register(EnderChestSyncHandler::onPlayerJoin);
        AfterChangeDimensionCallback.EVENT.register(EnderChestSyncHandler::onAfterChangeDimension);
        PlayerCopyEvents.RESPAWN.register(EnderChestSyncHandler::onRespawn);
    }

    @Override
    public void onRegisterPayloadTypes(PayloadTypesContext context) {
        context.playToServer(ServerboundContainerClientInputMessage.class,
                ServerboundContainerClientInputMessage.STREAM_CODEC);
        context.playToServer(ServerboundSelectedItemMessage.class, ServerboundSelectedItemMessage.STREAM_CODEC);
        context.playToClient(ClientboundEnderChestContentMessage.class,
                ClientboundEnderChestContentMessage.STREAM_CODEC);
        context.playToClient(ClientboundEnderChestSlotMessage.class, ClientboundEnderChestSlotMessage.STREAM_CODEC);
        context.playToServer(ServerboundEnderChestContentMessage.class,
                ServerboundEnderChestContentMessage.STREAM_CODEC);
        context.playToClient(ClientboundSyncItemStorage.class, ClientboundSyncItemStorage.STREAM_CODEC);
    }

    @Override
    public void onRegisterGameRegistries(GameRegistriesContext context) {
        context.registerRegistry(ItemStorage.REGISTRY);
    }

    @Override
    public void onAddDataPackFinders(PackRepositorySourcesContext context) {
        if (!ModLoaderEnvironment.INSTANCE.isDevelopmentEnvironment(MOD_ID)) {
            return;
        }

        context.registerRepositorySource(PackResourcesHelper.buildServerPack(id("item_storage_definitions"),
                DynamicPackResources.create(DynamicItemStorageDefinitionsProvider::new),
                true));
    }

    @Override
    public void onAddDataPackReloadListeners(DataPackReloadListenersContext context) {
        context.registerReloadListener(ItemStorageManager.REGISTRY_KEY.identifier(),
                (DataPackReloadListenersContext.PreparableReloadListenerFactory) (ReloadableServerResources serverResources, HolderLookup.Provider lookupWithUpdatedTags) -> {
                    return new ItemStorageManager(lookupWithUpdatedTags);
                });
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
