package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.LocalSkin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractClientPlayer.class)
public class LocalSkinMixin {
 @Inject(method="getLocationSkin",at=@At("HEAD"),cancellable=true)
 private void unboxSkin(CallbackInfoReturnable<ResourceLocation> ci){if((Object)this==Minecraft.getMinecraft().thePlayer&&LocalSkin.texture()!=null)ci.setReturnValue(LocalSkin.texture());}
 @Inject(method="getSkinType",at=@At("HEAD"),cancellable=true)
 private void unboxModel(CallbackInfoReturnable<String> ci){if((Object)this==Minecraft.getMinecraft().thePlayer&&LocalSkin.texture()!=null)ci.setReturnValue(LocalSkin.model());}
}
