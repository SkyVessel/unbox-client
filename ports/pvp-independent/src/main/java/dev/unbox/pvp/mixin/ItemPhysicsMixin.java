package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderEntityItem;
import net.minecraft.client.resources.model.IBakedModel;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** CreativeMD ItemPhysicLite resting/spinning model adaptation (GPL-3.0), using vanilla draw batches. */
@Mixin(RenderEntityItem.class)
public class ItemPhysicsMixin {
 @Inject(method="func_177077_a",at=@At("HEAD"),cancellable=true)
 private void pose(EntityItem entity,double x,double y,double z,float partial,IBakedModel model,CallbackInfoReturnable<Integer> ci){if(!PvpSettings.on("itemphysics"))return;ItemStack stack=entity.getEntityItem();if(stack==null)return;
  GlStateManager.translate((float)x,(float)y+(model.isGui3d()?.15f:.025f),(float)z);GlStateManager.rotate(entity.rotationYaw,0,1,0);
  float rotation=entity.onGround?90:((entity.getAge()+partial)*PvpSettings.number("itemphysics.speed",100,0,200)/100f*8)%360;GlStateManager.rotate(rotation,1,0,0);GlStateManager.color(1,1,1,1);
  int count=stack.stackSize>48?5:stack.stackSize>32?4:stack.stackSize>16?3:stack.stackSize>1?2:1;
  if(!model.isGui3d())GlStateManager.translate(0,0,-.046875f*(count-1));ci.setReturnValue(count);
 }
}
