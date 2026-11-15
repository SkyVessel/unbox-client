package dev.unbox.pvp.mixin;
import net.minecraft.network.handshake.client.C00Handshake;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(C00Handshake.class)
public interface PrivateHandshakeAccess {@Accessor("port") int unboxPort();}
