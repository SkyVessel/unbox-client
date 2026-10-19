package fuzs.iteminteractions.common.impl.network;

import fuzs.puzzleslib.common.api.network.v4.message.MessageListener;
import fuzs.puzzleslib.common.api.network.v4.message.play.ClientboundPlayMessage;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

public record ClientboundEnderChestSlotMessage(int slot, ItemStack item) implements ClientboundPlayMessage {
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundEnderChestSlotMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.SHORT.map(Short::intValue, Integer::shortValue),
            ClientboundEnderChestSlotMessage::slot,
            ItemStack.OPTIONAL_STREAM_CODEC,
            ClientboundEnderChestSlotMessage::item,
            ClientboundEnderChestSlotMessage::new);

    @Override
    public MessageListener<Context> getListener() {
        return new MessageListener<Context>() {
            @Override
            public void accept(Context context) {
                Container container = context.player().getEnderChestInventory();
                if (ClientboundEnderChestSlotMessage.this.slot >= 0
                        && ClientboundEnderChestSlotMessage.this.slot < container.getContainerSize()) {
                    container.setItem(ClientboundEnderChestSlotMessage.this.slot,
                            ClientboundEnderChestSlotMessage.this.item);
                }
            }
        };
    }
}
