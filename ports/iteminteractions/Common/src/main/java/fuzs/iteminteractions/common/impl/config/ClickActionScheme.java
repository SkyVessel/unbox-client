package fuzs.iteminteractions.common.impl.config;

import com.mojang.serialization.Codec;
import fuzs.puzzleslib.common.api.network.v4.codec.ExtraStreamCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.inventory.ClickAction;

import java.util.Locale;

public enum ClickActionScheme implements StringRepresentable {
    SPLIT_INPUT(ClickAction.PRIMARY, ClickAction.SECONDARY),
    SINGLE_INPUT(ClickAction.SECONDARY, ClickAction.SECONDARY);

    public static final Codec<ClickActionScheme> CODEC = StringRepresentable.fromEnum(ClickActionScheme::values);
    public static final StreamCodec<ByteBuf, ClickActionScheme> STREAM_CODEC = ExtraStreamCodecs.fromEnum(
            ClickActionScheme.class);

    private final ClickAction insert;
    private final ClickAction remove;

    ClickActionScheme(ClickAction insert, ClickAction remove) {
        this.insert = insert;
        this.remove = remove;
    }

    public boolean insertStackedOnOther(ClickAction clickAction) {
        return clickAction == this.insert;
    }

    public boolean removeStackedOnOther(ClickAction clickAction) {
        return clickAction == this.remove;
    }

    public boolean insertOtherStackedOnMe(ClickAction clickAction) {
        return clickAction == this.insert;
    }

    public boolean removeOtherStackedOnMe(ClickAction clickAction) {
        return clickAction == this.remove;
    }

    @Override
    public String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }
}
