package dev.unbox.client.mixin;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ServerLoginPacketListenerImpl.class)
public interface SocialLoginAccess {
 @Accessor("authenticatedProfile") void unboxProfile(GameProfile p);
 @Accessor("authenticatedProfile") GameProfile unboxProfile();
 @Accessor("requestedUsername") String unboxUsername();
 @Accessor("connection") Connection unboxConnection();
}
