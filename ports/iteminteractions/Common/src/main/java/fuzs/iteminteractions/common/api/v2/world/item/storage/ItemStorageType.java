package fuzs.iteminteractions.common.api.v2.world.item.storage;

import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A type for identifying and serializing item storage implementations.
 *
 * @param <T>         the item storage type
 * @param codec       the item storage codec
 * @param streamCodec the item storage stream codec
 */
public record ItemStorageType<T extends ItemStorage>(MapCodec<T> codec,
                                                     StreamCodec<? super RegistryFriendlyByteBuf, T> streamCodec) {

    /**
     * Creates a stream codec from the provided codec.
     *
     * @param codec the item storage codec
     */
    public ItemStorageType(MapCodec<T> codec) {
        this(codec, ByteBufCodecs.fromCodecWithRegistries(codec.codec()));
    }
}
