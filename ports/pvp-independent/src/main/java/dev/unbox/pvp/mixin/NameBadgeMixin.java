package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.NameBadges;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(RendererLivingEntity.class)
public class NameBadgeMixin {
 @Inject(method="renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V",at=@At("HEAD"))
 private void unboxBegin(EntityLivingBase e,double x,double y,double z,CallbackInfo ci){NameBadges.begin(e);}
 @Inject(method="renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V",at=@At("RETURN"))
 private void unboxEnd(EntityLivingBase e,double x,double y,double z,CallbackInfo ci){NameBadges.end();}
 @Redirect(method="renderName(Lnet/minecraft/entity/EntityLivingBase;DDD)V",at=@At(value="INVOKE",target="Lnet/minecraft/entity/EntityLivingBase;getDisplayName()Lnet/minecraft/util/IChatComponent;"))
 private IChatComponent unboxLabel(EntityLivingBase e){return NameBadges.prefix(e,e.getDisplayName());}
 @Inject(method="canRenderName(Lnet/minecraft/entity/EntityLivingBase;)Z",at=@At("HEAD"),cancellable=true)
 private void unboxOwn(EntityLivingBase e,CallbackInfoReturnable<Boolean> ci){if(e==Minecraft.getMinecraft().thePlayer)ci.setReturnValue(NameBadges.ownVisible(e));}
}
