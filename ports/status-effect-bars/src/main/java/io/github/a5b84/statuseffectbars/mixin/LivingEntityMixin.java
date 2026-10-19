package io.github.a5b84.statuseffectbars.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.a5b84.statuseffectbars.MobEffectInstanceDuck;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;


/** Prevents effects' max duration from being overwritten. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

  // NeoForge may reject the effect before the map update. Capture the actual
  // replaced value, not a local that is uninitialized on the rejection branch.
  @ModifyExpressionValue(method = "forceAddEffect", at = @At(value = "INVOKE",
      target = "Ljava/util/Map;put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"))
  private Object onSetStatusEffect(Object replaced,
      @Local(argsOnly = true) MobEffectInstance newEffect) {
    MobEffectInstance previousEffect = (MobEffectInstance) replaced;
    if (previousEffect != null && previousEffect.getAmplifier() == newEffect.getAmplifier()) {
      MobEffectInstanceDuck duck = (MobEffectInstanceDuck) newEffect;
      MobEffectInstanceDuck oldDuck = (MobEffectInstanceDuck) previousEffect;
      if (duck.statusEffectBars_getMaxDuration() < oldDuck.statusEffectBars_getMaxDuration()) {
        duck.statusEffectBars_setMaxDuration(oldDuck.statusEffectBars_getMaxDuration());
      }
    }
    return replaced;
  }
}
