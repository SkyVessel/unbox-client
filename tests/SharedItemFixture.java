package dev.unbox.fixture;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.*;
import net.minecraft.resources.*;
import net.minecraft.world.item.Item;
/** Test-only real registry entry: never bundled in the Unbox application. */
public class SharedItemFixture implements ModInitializer {
 public void onInitialize(){var key=ResourceKey.create(Registries.ITEM,Identifier.fromNamespaceAndPath("unbox_sync_fixture","shared_item"));Registry.register(BuiltInRegistries.ITEM,key,new Item(new Item.Properties().setId(key)));}
}
