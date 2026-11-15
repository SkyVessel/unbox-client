package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.client.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetHandlerLoginServer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(NetHandlerLoginServer.class)
public class PrivateLoginServerMixin {
 @Shadow @Final private MinecraftServer server;
 @Shadow @Final public NetworkManager networkManager;
 @Shadow private GameProfile loginGameProfile;
 @Inject(method="tryAcceptPlayer",at=@At("HEAD"),cancellable=true)
 private void unboxAdmission(CallbackInfo ci){if(SocialBridge.guarded()&&!networkManager.isLocalChannel()&&(loginGameProfile==null||!SocialBridge.allowed(loginGameProfile.getId())||!networkManager.getIsencrypted())){((NetHandlerLoginServer)(Object)this).closeConnection("World sharing ended.");ci.cancel();}}
 @Inject(method="processLoginStart",at=@At("HEAD"),cancellable=true)
 private void unboxStart(C00PacketLoginStart packet,CallbackInfo ci){if(PrivateLogin.begin((NetHandlerLoginServer)(Object)this,server,packet))ci.cancel();}
 @Inject(method="processEncryptionResponse",at=@At("HEAD"),cancellable=true)
 private void unboxVerify(C01PacketEncryptionResponse packet,CallbackInfo ci){if(SocialBridge.guarded()&&!networkManager.isLocalChannel()){GameProfile p=PrivateLogin.finish((NetHandlerLoginServer)(Object)this,server,packet);if(p!=null)loginGameProfile=p;ci.cancel();}}
}
