package dev.unbox.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla hides the camera player's label even in third person. Show the actual body label. */
@Mixin(LivingEntityRenderer.class)
public abstract class LocalNicknameNameTagMixin {
    @Inject(method="shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at=@At("HEAD"), cancellable=true)
    private void unbox$ownName(LivingEntity entity, double distanceSquared, CallbackInfoReturnable<Boolean> ci) {
        Minecraft mc = Minecraft.getInstance();
        if (entity != mc.player) return;
        boolean outsideBody = mc.getCameraEntity() != entity || !mc.options.getCameraType().isFirstPerson();
        ci.setReturnValue(outsideBody && mc.screen == null && Minecraft.renderNames()
            && entity.isAlive() && !entity.isInvisible() && !entity.isSpectator()
            && distanceSquared < (entity.isDiscrete() ? 1024.0 : 4096.0));
    }
}
