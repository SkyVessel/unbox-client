package fuzs.iteminteractions.common.impl.world.item.component;

import fuzs.iteminteractions.common.impl.config.ClickActionScheme;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record ControlScheme(boolean moveSingleItem, ClickActionScheme controlScheme) {
    public static final ControlScheme DEFAULT = new ControlScheme(false, ClickActionScheme.SINGLE_INPUT);
    public static final StreamCodec<ByteBuf, ControlScheme> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.BOOL,
            ControlScheme::moveSingleItem,
            ClickActionScheme.STREAM_CODEC,
            ControlScheme::controlScheme,
            ControlScheme::new);
}
