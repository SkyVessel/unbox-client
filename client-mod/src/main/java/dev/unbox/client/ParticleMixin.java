package dev.unbox.client.mixin;
import dev.unbox.client.UnboxClient;
import dev.unbox.client.OptifineCape;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ParticleEngine.class)
public class ParticleMixin {
 @Inject(method="makeParticle",at=@At("RETURN"))
 private void unboxType(net.minecraft.core.particles.ParticleOptions options,double x,double y,double z,double dx,double dy,double dz,org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Particle> ci){if(ci.getReturnValue()!=null)((dev.unbox.client.ParticleAccess)ci.getReturnValue()).unboxFamily(dev.unbox.client.ParticleFilter.classify(net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(options.getType()).getPath()));}
 @Inject(method="add",at=@At("HEAD"),cancellable=true)
 private void unboxParticles(Particle particle,CallbackInfo ci){if(!dev.unbox.client.ParticleFilter.allow(particle))ci.cancel();else dev.unbox.client.ParticleFilter.customize(particle);}
}
