// Adapted from OverflowAnimations v2.2.5 (LGPL-3.0).
package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemRenderer.class)
public abstract class ClassicAnimationsMixin {

    @Shadow @Final private Minecraft mc;
    @Shadow private int equippedItemSlot;
    @Shadow private ItemStack itemToRender;
    @Shadow @Final private RenderItem itemRenderer;
    @Shadow protected abstract void rotateWithPlayerRotations(EntityPlayerSP entityplayerspIn, float partialTicks);
    @Unique private static float overflowAnimations$f1 = 0.0F;

    @ModifyVariable(
            method = "renderItemInFirstPerson",
            at = @At(
                    value = "STORE"
            ),
            index = 4
    )
    private float overflowAnimations$captureF1(float f1) {
        overflowAnimations$f1 = f1;
        return f1;
    }

    @ModifyArg(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;transformFirstPersonItem(FF)V"),
            slice = @Slice(
                    from = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;performDrinking(Lnet/minecraft/client/entity/AbstractClientPlayer;F)V"),
                    to = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;doBowTransformations(FLnet/minecraft/client/entity/AbstractClientPlayer;)V")
            ), index = 1
    )
    private float overflowAnimations$useF1(float swingProgress) {
        if (PvpSettings.flag("classic.oldBlockhitting", true) && PvpSettings.on("classic")) {
            return overflowAnimations$f1;
        }
        return swingProgress;
    }

    @Inject(method = "doBowTransformations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GlStateManager;scale(FFF)V"))
    private void overflowAnimations$preBowTransform(float partialTicks, AbstractClientPlayer clientPlayer, CallbackInfo ci) {
        if (PvpSettings.flag("classic.firstTransformations", true) && !false && PvpSettings.on("classic")) {
            GlStateManager.rotate(-335.0F, 0.0F, 0.0F, 1.0F);
            GlStateManager.rotate(-50.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.translate(0.0F, 0.5F, 0.0F);
        }
    }

    @Inject(method = "doBowTransformations", at = @At(value = "TAIL"))
    private void overflowAnimations$postBowTransform(float partialTicks, AbstractClientPlayer clientPlayer, CallbackInfo ci) {
        if (PvpSettings.flag("classic.firstTransformations", true) && !false && PvpSettings.on("classic")) {
            GlStateManager.translate(0.0F, -0.5F, 0.0F);
            GlStateManager.rotate(50.0F, 0.0F, 1.0F, 0.0F);
            GlStateManager.rotate(335.0F, 0.0F, 0.0F, 1.0F);
        }
    }

    @Inject(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;renderItem(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/renderer/block/model/ItemCameraTransforms$TransformType;)V"))
    private void overflowAnimations$firstPersonItemPositions(float partialTicks, CallbackInfo ci) {
        if (PvpSettings.on("classic") && !false && !itemRenderer.shouldRenderItemIn3D(itemToRender)) {
            if ((PvpSettings.flag("classic.fishingRodPosition", true) && itemToRender.getItem().shouldRotateAroundWhenRendering())) {
                GlStateManager.rotate(180.0F, 0.0F, 1.0F, 0.0F);
                overflowAnimations$itemTransforms();
            } else if (PvpSettings.flag("classic.firstTransformations", true) && !(itemToRender.getItem() instanceof ItemSword && false)) {
                overflowAnimations$itemTransforms();
            }
        }
    }

    @Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemRenderer;rotateWithPlayerRotations(Lnet/minecraft/client/entity/EntityPlayerSP;F)V"))
    private void overflowAnimations$removeRotations(ItemRenderer instance, EntityPlayerSP entityPlayerSP, float v) {
        if (!PvpSettings.flag("classic.oldItemRotations", true) || !PvpSettings.on("classic")) {
            rotateWithPlayerRotations(entityPlayerSP, v);
        }
    }

    @Unique
    private static void overflowAnimations$itemTransforms() {
        float scale = 1.5F / 1.7F;
        GlStateManager.scale(scale, scale, scale);
        GlStateManager.rotate(5.0F, 0.0F, 1.0F, 0.0F);
        GlStateManager.translate(-0.29F, 0.149F, -0.0328F);
    }

}
