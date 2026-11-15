package dev.unbox.client;
import net.minecraft.network.FriendlyByteBuf;
public record LegacyCodec<B extends FriendlyByteBuf,T>(java.util.function.BiConsumer<T,B> writer,java.util.function.Function<B,T> reader){public void encode(B b,T p){writer.accept(p,b);}public T decode(B b){return reader.apply(b);}}
