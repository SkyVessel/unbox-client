package dev.unbox.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.unbox.client.UnboxClient;
import dev.unbox.client.DroppedItemState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Rendering-only ItemPhysic-style flat presentation; entity motion and pickup stay vanilla. */
@Mixin(ItemEntityRenderer.class)
public abstract class DroppedItemMixin {
    @Unique private ItemEntityRenderState unbox$current;

    @Inject(method="extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V",at=@At("TAIL"))
    private void unbox$motion(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        ((DroppedItemState)state).unbox$motion(entity.onGround(), entity.getDeltaMovement().length());
    }

    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("HEAD"))
    private void unbox$begin(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera, CallbackInfo ci) { unbox$current=state; }

    @Inject(method="submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",at=@At("RETURN"))
    private void unbox$end(ItemEntityRenderState state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera, CallbackInfo ci) { unbox$current=null; }

    @ModifyArgs(method="submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",ordinal=0))
    private void unbox$stopGroundBob(Args args) {
        if (!UnboxClient.enabled("items") || unbox$current==null || !((DroppedItemState)unbox$current).unbox$grounded()) return;
        float bob=Mth.sin(unbox$current.ageInTicks/10.0F+unbox$current.bobOffset)*0.1F+0.1F;
        args.set(1, ((Float)args.get(1))-bob);
    }

    @Redirect(method="submit(Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at=@At(value="INVOKE", target="Lcom/mojang/math/Axis;rotation(F)Lorg/joml/Quaternionf;"))
    private Quaternionf unbox$flatItems(Axis axis, float spin) {
        if (!UnboxClient.enabled("items") || unbox$current==null) return axis.rotation(spin);
        var state=(DroppedItemState)unbox$current;
        if (state.unbox$grounded()) return Axis.XP.rotationDegrees(90.0F);
        float tumble=(float)(unbox$current.ageInTicks*(18.0+Math.min(42.0,state.unbox$speed()*80.0)));
        return Axis.XP.rotationDegrees(tumble).mul(Axis.ZP.rotationDegrees(tumble*0.63F));
    }
}
