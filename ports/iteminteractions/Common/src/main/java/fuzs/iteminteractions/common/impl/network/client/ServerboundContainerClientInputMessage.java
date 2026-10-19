package fuzs.iteminteractions.common.impl.network.client;

import fuzs.iteminteractions.common.impl.init.ModRegistry;
import fuzs.iteminteractions.common.impl.world.item.component.ControlScheme;
import fuzs.puzzleslib.common.api.network.v4.message.MessageListener;
import fuzs.puzzleslib.common.api.network.v4.message.play.ServerboundPlayMessage;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record ServerboundContainerClientInputMessage(ControlScheme controlScheme) implements ServerboundPlayMessage {
    public static final StreamCodec<ByteBuf, ServerboundContainerClientInputMessage> STREAM_CODEC = StreamCodec.composite(
            ControlScheme.STREAM_CODEC,
            ServerboundContainerClientInputMessage::controlScheme,
            ServerboundContainerClientInputMessage::new);

    @Override
    public MessageListener<Context> getListener() {
        return new MessageListener<Context>() {
            @Override
            public void accept(Context context) {
                ModRegistry.CONTROL_SCHEME_ATTACHMENT_TYPE.set(context.player(),
                        ServerboundContainerClientInputMessage.this.controlScheme());
            }
        };
    }
}
