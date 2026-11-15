package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Camera-space viewmodel adjustment inside vanilla's hand-only matrix scope. */
@Mixin(ItemRenderer.class)
public class ViewmodelMixin {
 @Shadow private ItemStack itemToRender;
 @Inject(method="renderItemInFirstPerson",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/GlStateManager;pushMatrix()V",shift=At.Shift.AFTER))
 private void unboxViewmodel(float partial,CallbackInfo ci){
  if(!PvpSettings.on("viewmodel"))return;
  String prefix=itemToRender==null&&PvpSettings.flag("viewmodel.separateHand",false)?"viewmodel.empty.":"viewmodel.item.";
  GlStateManager.translate(PvpSettings.number(prefix+"x",0,-100,100)/100f,PvpSettings.number(prefix+"y",0,-100,100)/100f,-PvpSettings.number(prefix+"z",0,-100,100)/100f);
  // Rotate/scale around the resting hand rather than moving it around the camera origin.
  GlStateManager.translate(.56f,-.52f,-.72f);
  GlStateManager.rotate(PvpSettings.number(prefix+"pitch",0,-180,180),1,0,0);GlStateManager.rotate(PvpSettings.number(prefix+"yaw",0,-180,180),0,1,0);GlStateManager.rotate(PvpSettings.number(prefix+"roll",0,-180,180),0,0,1);
  float size=PvpSettings.number(prefix+"scale",100,25,150)/100f;GlStateManager.scale(size,size,size);GlStateManager.translate(-.56f,.52f,.72f);
 }
}
