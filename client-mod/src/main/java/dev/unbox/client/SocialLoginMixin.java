package dev.unbox.client.mixin;
import dev.unbox.client.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import net.minecraft.util.Crypt;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class SocialLoginMixin {
 @Shadow @Final private Connection connection;
 @Shadow @Final private MinecraftServer server;
 @Shadow @Final private byte[] challenge;
 @Shadow private String requestedUsername;
 @Shadow public abstract void disconnect(Component reason);
 @Shadow protected abstract void startClientVerification(GameProfile profile);
 @Unique private boolean unboxAwaitingKey;
 @Shadow private int tick;
 @Inject(method="tick",at=@At("HEAD"))
 private void unboxTransferTime(CallbackInfo ci){if(SocialLogin.verified((ServerLoginPacketListenerImpl)(Object)this)&&EnvironmentSync.active((ServerLoginPacketListenerImpl)(Object)this))tick=0;}
 @Inject(method="handleHello",at=@At("HEAD"),cancellable=true)
 private void unboxOwner(ServerboundHelloPacket p,CallbackInfo ci){if(!SocialBridge.guarded()||connection.isMemoryConnection())return;var owner=server.getSingleplayerProfile();if(owner!=null&&(SocialBridge.privateAuthentication()?p.profileId().equals(owner.id()):p.name().equalsIgnoreCase(owner.name()))){disconnect(Component.literal("This identity belongs to the world owner."));ci.cancel();return;}if(SocialBridge.privateAuthentication())SocialLogin.claim((ServerLoginPacketListenerImpl)(Object)this,p.profileId());}
 @Redirect(method="handleHello",at=@At(value="INVOKE",target="Lnet/minecraft/server/MinecraftServer;getSingleplayerProfile()Lcom/mojang/authlib/GameProfile;"))
 private GameProfile unboxRemoteOwner(MinecraftServer s){return SocialBridge.privateAuthentication()&&!connection.isMemoryConnection()?null:s.getSingleplayerProfile();}
 @ModifyArg(method="handleHello",at=@At(value="INVOKE",target="Lnet/minecraft/network/protocol/login/ClientboundHelloPacket;<init>(Ljava/lang/String;[B[BZ)V"),index=3)
 private boolean unboxEncrypted(boolean original){if(SocialBridge.privateAuthentication()){unboxAwaitingKey=true;return false;}return original;}
 @Inject(method="handleKey",at=@At("HEAD"),cancellable=true)
 private void unboxKey(ServerboundKeyPacket p,CallbackInfo ci){if(!SocialBridge.privateAuthentication()||connection.isMemoryConnection())return;ci.cancel();try{if(!unboxAwaitingKey||!p.isChallengeValid(challenge,server.getKeyPair().getPrivate()))throw new IllegalArgumentException();unboxAwaitingKey=false;var key=p.getSecretKey(server.getKeyPair().getPrivate());connection.setEncryptionKey(Crypt.getCipher(2,key),Crypt.getCipher(1,key));startClientVerification(net.minecraft.core.UUIDUtil.createOfflineProfile(requestedUsername));}catch(Exception e){disconnect(Component.literal("Private connection verification failed."));}}
 @Inject(method="verifyLoginAndFinishConnectionSetup",at=@At("HEAD"),cancellable=true)
 private void unboxProof(GameProfile p,CallbackInfo ci){if(SocialBridge.privateAuthentication()&&!connection.isMemoryConnection()&&!SocialLogin.verified((ServerLoginPacketListenerImpl)(Object)this)){disconnect(Component.literal("Use your Unbox world invitation to join."));ci.cancel();}}
}
