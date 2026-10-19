package fuzs.iteminteractions.common.impl.network;

import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorage;
import fuzs.iteminteractions.common.impl.world.item.container.ItemStorageManager;
import fuzs.puzzleslib.common.api.network.v4.message.MessageListener;
import fuzs.puzzleslib.common.api.network.v4.message.play.ClientboundPlayMessage;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;

public record ClientboundSyncItemStorage(Map<Item, ItemStorage> providers) implements ClientboundPlayMessage {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundSyncItemStorage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.registry(Registries.ITEM), ItemStorage.STREAM_CODEC),
            ClientboundSyncItemStorage::providers,
            ClientboundSyncItemStorage::new);

    @Override
    public MessageListener<Context> getListener() {
        return new MessageListener<Context>() {
            @Override
            public void accept(Context context) {
                ItemStorageManager.setItemStorageDefinitions(ClientboundSyncItemStorage.this.providers);
            }
        };
    }
}
