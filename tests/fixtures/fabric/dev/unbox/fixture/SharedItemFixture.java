package dev.unbox.fixture;

public final class SharedItemFixture implements net.fabricmc.api.ModInitializer {
    @Override public void onInitialize(){
        var key=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM,
            net.minecraft.resources.Identifier.fromNamespaceAndPath("unbox_sync_fixture","shared_item"));
        net.minecraft.core.Registry.register(net.minecraft.core.registries.BuiltInRegistries.ITEM,key,
            new SharedItem(new net.minecraft.world.item.Item.Properties().setId(key)));
    }
}
