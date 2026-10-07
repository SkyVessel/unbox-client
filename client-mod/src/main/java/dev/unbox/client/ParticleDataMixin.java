package dev.unbox.client.mixin;

import dev.unbox.client.ParticleAccess;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Particle.class)
public class ParticleDataMixin implements ParticleAccess {
    @Unique private String unboxFamily;
    public String unboxFamily(){return unboxFamily;}
    public void unboxFamily(String family){unboxFamily=family;}
}
