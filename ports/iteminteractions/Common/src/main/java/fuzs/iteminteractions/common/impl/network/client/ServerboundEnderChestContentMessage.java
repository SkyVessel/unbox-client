package fuzs.iteminteractions.common.impl.network.client;

import fuzs.iteminteractions.common.impl.handler.EnderChestSyncHandler;
import fuzs.puzzleslib.common.api.network.v4.message.MessageListener;
import fuzs.puzzleslib.common.api.network.v4.message.play.ServerboundPlayMessage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record ServerboundEnderChestContentMessage(List<ItemStack> items) implements ServerboundPlayMessage {
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundEnderChestContentMessage> STREAM_CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_LIST_STREAM_CODEC,
            ServerboundEnderChestContentMessage::items,
            ServerboundEnderChestContentMessage::new);

    @Override
    public MessageListener<Context> getListener() {
        return new MessageListener<Context>() {
            @Override
            public void accept(Context context) {
                if (context.player().gameMode.isCreative()) {
                    EnderChestSyncHandler.setEnderChestContent(context.player(),
                            ServerboundEnderChestContentMessage.this.items);
                }
            }
        };
    }
}
