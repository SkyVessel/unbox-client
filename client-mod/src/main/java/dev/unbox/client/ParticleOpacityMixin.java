package dev.unbox.client.mixin;

import dev.unbox.client.ParticleFilter;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Multiply the current alpha at extraction so vanilla lifetime fading is preserved. */
@Mixin(SingleQuadParticle.class)
public class ParticleOpacityMixin {
    @Shadow protected float alpha;
    @Unique private float unboxOriginalAlpha;
    @Inject(method="extract",at=@At("HEAD"))
    private void unboxOpacity(QuadParticleRenderState state,Camera camera,float tick,CallbackInfo ci){unboxOriginalAlpha=alpha;alpha*=ParticleFilter.opacity((Particle)(Object)this);}
    @Inject(method="extract",at=@At("RETURN"))
    private void unboxRestore(QuadParticleRenderState state,Camera camera,float tick,CallbackInfo ci){alpha=unboxOriginalAlpha;}
}
