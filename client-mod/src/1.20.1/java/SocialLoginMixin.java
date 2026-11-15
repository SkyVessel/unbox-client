package dev.unbox.client.mixin;
import dev.unbox.client.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerLoginPacketListenerImpl.class) public abstract class SocialLoginMixin {
 @Shadow @Final private Connection connection;@Shadow @Final private MinecraftServer server;@Shadow private int tick;
 @Shadow GameProfile gameProfile;@Shadow ServerLoginPacketListenerImpl.State state;
 @Shadow public abstract void disconnect(Component reason);@Shadow protected abstract GameProfile createFakeProfile(GameProfile profile);
 @Inject(method="tick",at=@At("HEAD"))private void transfer(CallbackInfo ci){if(SocialLogin.verified((ServerLoginPacketListenerImpl)(Object)this)&&EnvironmentSync.active((ServerLoginPacketListenerImpl)(Object)this))tick=0;}
 @Inject(method="handleHello",at=@At("HEAD"),cancellable=true)private void owner(ServerboundHelloPacket p,CallbackInfo ci){if(!SocialBridge.guarded()||connection.isMemoryConnection())return;var owner=server.getSingleplayerProfile();if(owner!=null&&(SocialBridge.privateAuthentication()?p.profileId().filter(owner.getId()::equals).isPresent():p.name().equalsIgnoreCase(owner.getName()))){disconnect(Component.literal("This identity belongs to the world owner."));ci.cancel();return;}if(SocialBridge.privateAuthentication()){if(p.profileId().isEmpty()){disconnect(Component.literal("Use your Unbox world invitation to join."));ci.cancel();return;}SocialLogin.claim((ServerLoginPacketListenerImpl)(Object)this,p.profileId().get());}}
 @Redirect(method="handleHello",at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;getSingleplayerProfile()Lcom/mojang/authlib/GameProfile;"))private GameProfile remoteOwner(MinecraftServer s){return SocialBridge.privateAuthentication()&&!connection.isMemoryConnection()?null:s.getSingleplayerProfile();}
 // Vanilla validates the challenge and enables encryption before starting this thread.
 @Redirect(method="handleKey",at=@At(value="INVOKE",target="Ljava/lang/Thread;start()V"))private void authenticate(Thread thread){if(!SocialBridge.privateAuthentication()||connection.isMemoryConnection()){thread.start();return;}gameProfile=createFakeProfile(gameProfile);state=ServerLoginPacketListenerImpl.State.READY_TO_ACCEPT;}
 @Inject(method="handleAcceptedLogin",at=@At("HEAD"),cancellable=true)private void proof(CallbackInfo ci){if(SocialBridge.privateAuthentication()&&!connection.isMemoryConnection()&&!SocialLogin.verified((ServerLoginPacketListenerImpl)(Object)this)){disconnect(Component.literal("Use your Unbox world invitation to join."));ci.cancel();}}
}
