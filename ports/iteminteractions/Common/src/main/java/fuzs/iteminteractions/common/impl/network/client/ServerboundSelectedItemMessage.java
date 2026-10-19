package fuzs.iteminteractions.common.impl.network.client;

import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorageHolder;
import fuzs.puzzleslib.common.api.network.v4.message.MessageListener;
import fuzs.puzzleslib.common.api.network.v4.message.play.ServerboundPlayMessage;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

/**
 * @see net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket
 */
public record ServerboundSelectedItemMessage(OptionalInt slotId,
                                             int selectedItemIndex,
                                             boolean slotClicked) implements ServerboundPlayMessage {
    public static final StreamCodec<FriendlyByteBuf, ServerboundSelectedItemMessage> STREAM_CODEC = StreamCodec.ofMember(
            ServerboundSelectedItemMessage::write,
            ServerboundSelectedItemMessage::new);

    private ServerboundSelectedItemMessage(FriendlyByteBuf input) {
        this(ByteBufCodecs.OPTIONAL_VAR_INT.decode(input), input.readVarInt(), input.readBoolean());
        if (this.selectedItemIndex < 0 && this.selectedItemIndex != -1) {
            throw new IllegalArgumentException("Invalid selectedItemIndex: " + this.selectedItemIndex);
        }
    }

    private void write(FriendlyByteBuf output) {
        ByteBufCodecs.OPTIONAL_VAR_INT.encode(output, this.slotId);
        output.writeVarInt(this.selectedItemIndex);
        output.writeBoolean(this.slotClicked);
    }

    @Override
    public MessageListener<Context> getListener() {
        return new MessageListener<Context>() {
            @Override
            public void accept(Context context) {
                ItemStack itemStack = this.getContainerItem(context.player().containerMenu);
                if (!itemStack.isEmpty()) {
                    ItemStorageHolder.ofItem(itemStack)
                            .storage()
                            .toggleSelectedItem(itemStack,
                                    context.player(),
                                    ServerboundSelectedItemMessage.this.selectedItemIndex,
                                    ServerboundSelectedItemMessage.this.slotClicked);
                }
            }

            private ItemStack getContainerItem(AbstractContainerMenu menu) {
                OptionalInt slotIndex = ServerboundSelectedItemMessage.this.slotId;
                if (slotIndex.isPresent()) {
                    if (slotIndex.getAsInt() >= 0 && slotIndex.getAsInt() < menu.slots.size()) {
                        return menu.slots.get(slotIndex.getAsInt()).getItem();
                    } else {
                        return ItemStack.EMPTY;
                    }
                } else {
                    return menu.getCarried();
                }
            }
        };
    }
}
