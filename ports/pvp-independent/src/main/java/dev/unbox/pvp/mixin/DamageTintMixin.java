// Adapted from Polyfrost DamageTint (LGPL-3.0); see bundled source references.
package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;

import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RendererLivingEntity.class)
public class DamageTintMixin {
    @Unique
    private EntityLivingBase damageTint$entitylivingbaseIn;

    @Inject(method = "setBrightness", at = @At("HEAD"))
    private void set(EntityLivingBase entitylivingbaseIn, float partialTicks, boolean combineTextures, CallbackInfoReturnable<Boolean> cir) {
        damageTint$entitylivingbaseIn = entitylivingbaseIn;
    }

    // OptiFine's shader path bypasses the fixed-function buffer with separate RGBA arguments.
    @ModifyArgs(method = "setBrightness", at = @At(value = "INVOKE", target = "Lnet/optifine/shaders/Shaders;setEntityColor(FFFF)V", ordinal = 0, remap = false), require = 0)
    private void unbox$shaderHurtColor(Args args) {
        if (!PvpSettings.on("hitcolor")) return;
        args.set(0, getRedTint(args.get(0)));
        args.set(1, getGreenTint(args.get(1)));
        args.set(2, getBlueTint(args.get(2)));
        args.set(3, getAlphaTint(args.get(3)));
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 0))
    private float getRedTint(float f) {
        if (PvpSettings.on("hitcolor")) {
            return ((float) ((PvpSettings.color("hitcolor.color", "#FF0000") >> 16) & 255)) / 255f;
        }
        return f;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 1))
    private float getGreenTint(float f) {
        if (PvpSettings.on("hitcolor")) {
            return ((float) ((PvpSettings.color("hitcolor.color", "#FF0000") >> 8) & 255)) / 255f;
        }
        return f;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 2))
    private float getBlueTint(float f) {
        if (PvpSettings.on("hitcolor")) {
            return ((float) ((PvpSettings.color("hitcolor.color", "#FF0000") >> 0) & 255)) / 255f;
        }
        return f;
    }

    @ModifyArg(method = "setBrightness", at = @At(value = "INVOKE", target = "Ljava/nio/FloatBuffer;put(F)Ljava/nio/FloatBuffer;", ordinal = 3))
    private float getAlphaTint(float f) {
        if (PvpSettings.on("hitcolor")) {
            if (PvpSettings.flag("hitcolor.fade", false) && damageTint$entitylivingbaseIn.maxHurtTime > 0) {
                float percent = 1.0F - (float) damageTint$entitylivingbaseIn.hurtTime / (float) damageTint$entitylivingbaseIn.maxHurtTime;
                percent = percent < 0.5F ? percent / 0.5F : (1.0F - percent) / 0.5F;
                return (float) (PvpSettings.number("hitcolor.opacity", 30, 0, 100) * 2.55f) * percent / 255.0F;
            } else {
                return (float) (PvpSettings.number("hitcolor.opacity", 30, 0, 100) * 2.55f) / 255.0F;
            }
        }
        return f;
    }
}
