package dev.unbox.client;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
public interface LegacyPayload {record Type<T>(ResourceLocation id){} Type<?> type();static <T> LegacyCodec<FriendlyByteBuf,T> codec(java.util.function.BiConsumer<T,FriendlyByteBuf> w,java.util.function.Function<FriendlyByteBuf,T> r){return new LegacyCodec<>(w,r);}}
