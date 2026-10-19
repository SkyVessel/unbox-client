package fuzs.iteminteractions.common.impl.init;

import fuzs.iteminteractions.common.api.v2.world.item.storage.*;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.world.item.component.ControlScheme;
import fuzs.iteminteractions.common.impl.world.item.component.SelectedItem;
import fuzs.puzzleslib.common.api.attachment.v4.DataAttachmentRegistry;
import fuzs.puzzleslib.common.api.attachment.v4.DataAttachmentType;
import fuzs.puzzleslib.common.api.init.v3.registry.RegistryManager;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

public class ModRegistry {
    static final RegistryManager REGISTRIES = RegistryManager.from(ItemInteractions.MOD_ID);
    public static final Holder.Reference<ItemStorageType<?>> EMPTY_ITEM_STORAGE_TYPE = REGISTRIES.register(ItemStorage.REGISTRY_KEY,
            "empty",
            () -> new ItemStorageType<>(VoidStorage.CODEC));
    public static final Holder.Reference<ItemStorageType<?>> CONTAINER_ITEM_STORAGE_TYPE = REGISTRIES.register(
            ItemStorage.REGISTRY_KEY,
            "container",
            () -> new ItemStorageType<>(ContainerStorage.CODEC));
    public static final Holder.Reference<ItemStorageType<?>> ENDER_CHEST_ITEM_STORAGE_TYPE = REGISTRIES.register(
            ItemStorage.REGISTRY_KEY,
            "ender_chest",
            () -> new ItemStorageType<>(EnderChestStorage.CODEC));
    public static final Holder.Reference<ItemStorageType<?>> BUNDLE_ITEM_STORAGE_TYPE = REGISTRIES.register(ItemStorage.REGISTRY_KEY,
            "bundle",
            () -> new ItemStorageType<>(BundleContentsStorage.CODEC));

    public static final DataAttachmentType<Entity, ControlScheme> CONTROL_SCHEME_ATTACHMENT_TYPE = DataAttachmentRegistry.<ControlScheme>entityBuilder()
            .build(ItemInteractions.id("control_scheme"));
    public static final DataAttachmentType<Entity, SelectedItem> SELECTED_ITEM_ATTACHMENT_TYPE = DataAttachmentRegistry.<SelectedItem>entityBuilder()
            .build(ItemInteractions.id("selected_item"));

    public static void bootstrap() {
        // NO-OP
    }
}
