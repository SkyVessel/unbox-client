package dev.unbox.client.mixin;
import dev.unbox.client.UnboxClient;
import dev.unbox.client.ClientHud;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Native counterpart of Unbox 1.8.9 Viewmodel, inside vanilla's per-hand pose scope. */
@Mixin(ItemInHandRenderer.class)
public class ViewmodelMixin {
 @Inject(method="renderArmWithItem",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",shift=At.Shift.AFTER,ordinal=0))
 private void unboxViewmodel(AbstractClientPlayer player,float partial,float pitch,InteractionHand hand,float swing,ItemStack item,float equip,PoseStack pose,SubmitNodeCollector collector,int light,CallbackInfo ci){
  if(!UnboxClient.enabled("viewmodel"))return;
  String p=item.isEmpty()&&ClientHud.flag("viewmodel.separateHand",false)?"viewmodel.empty.":hand==InteractionHand.OFF_HAND&&ClientHud.flag("viewmodel.separateOffhand",false)?"viewmodel.off.":"viewmodel.item.";
  float side=(hand==InteractionHand.MAIN_HAND)==(player.getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT)?1:-1;
  pose.translate(UnboxClient.number(p+"x",0,-100,100)/100f,UnboxClient.number(p+"y",0,-100,100)/100f,-UnboxClient.number(p+"z",0,-100,100)/100f);
  pose.translate(.56f*side,-.52f,-.72f);
  pose.mulPose(Axis.XP.rotationDegrees(UnboxClient.number(p+"pitch",0,-180,180)));pose.mulPose(Axis.YP.rotationDegrees(UnboxClient.number(p+"yaw",0,-180,180)));pose.mulPose(Axis.ZP.rotationDegrees(UnboxClient.number(p+"roll",0,-180,180)));
  float size=UnboxClient.number(p+"scale",100,25,150)/100f;pose.scale(size,size,size);pose.translate(-.56f*side,.52f,.72f);
 }
}
