package dev.unbox.fixture;
@net.neoforged.fml.common.Mod("unbox_sync_fixture")
public final class SharedItemFixture {
 public SharedItemFixture(net.neoforged.bus.api.IEventBus bus){
  var items=net.neoforged.neoforge.registries.DeferredRegister.createItems("unbox_sync_fixture");
  items.registerItem("shared_item",SharedItem::new);items.register(bus);
 }
}
