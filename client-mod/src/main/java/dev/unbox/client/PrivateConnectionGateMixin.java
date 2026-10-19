package dev.unbox.client.mixin;
import dev.unbox.client.SocialLogin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Local identities connect only after a private invitation has prepared room authentication. */
@Mixin(ConnectScreen.class)
public class PrivateConnectionGateMixin {
 @Inject(method="startConnecting",at=@At("HEAD"),cancellable=true)
 private static void unboxPrivateOnly(CallbackInfo ci){
  if(System.getProperty("unbox.accountKind","microsoft").equals("local")&&!SocialLogin.hasPendingInvitation()){
   var mc=Minecraft.getInstance();var previous=mc.screen;mc.setScreen(new AlertScreen(()->mc.setScreen(previous),Component.literal("Private worlds only"),Component.literal("Use Friends to join a private Unbox world. Public servers require Microsoft sign-in.")));ci.cancel();
  }
 }
}
