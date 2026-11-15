package dev.unbox.client.mixin;
import dev.unbox.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayer.class) public class CapeMixin {
 @Inject(method="getSkinTextureLocation",at=@At("RETURN"),cancellable=true)private void skin(CallbackInfoReturnable<ResourceLocation> ci){if((Object)this==Minecraft.getInstance().player){var skin=LocalSkin.current();if(skin!=null)ci.setReturnValue(skin.texture());}}
 @Inject(method="getModelName",at=@At("RETURN"),cancellable=true)private void model(CallbackInfoReturnable<String> ci){if((Object)this==Minecraft.getInstance().player){var skin=LocalSkin.current();if(skin!=null)ci.setReturnValue(skin.model());}}
 @Inject(method="getCloakTextureLocation",at=@At("RETURN"),cancellable=true)private void cape(CallbackInfoReturnable<ResourceLocation> ci){var p=(AbstractClientPlayer)(Object)this;if(!UnboxClient.enabled("cape")||ci.getReturnValue()!=null||ClientHud.flag("cape.selfOnly",false)&&p!=Minecraft.getInstance().player)return;var t=OptifineCape.texture(p.getGameProfile().getName());if(t!=null)ci.setReturnValue(t);}
}
