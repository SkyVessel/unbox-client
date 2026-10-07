package dev.unbox.client.mixin;
import dev.unbox.client.UnboxClient;
import dev.unbox.client.OptifineCape;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.core.ClientAsset;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayer.class)
public class CapeMixin {
 @Inject(method="getSkin",at=@At("RETURN"),cancellable=true)
 private void unboxCape(CallbackInfoReturnable<PlayerSkin> ci){
  if(!UnboxClient.enabled("cape"))return;
  var original=ci.getReturnValue();if(original.cape()!=null)return;
  var player=(AbstractClientPlayer)(Object)this;
  if(dev.unbox.client.ClientHud.flag("cape.selfOnly",false)&&player!=net.minecraft.client.Minecraft.getInstance().player)return;
  var texture=OptifineCape.texture(player.getGameProfile().name());
  if(texture!=null)ci.setReturnValue(new PlayerSkin(original.body(),new ClientAsset.ResourceTexture(texture,texture),original.elytra(),original.model(),original.secure()));
 }
}
