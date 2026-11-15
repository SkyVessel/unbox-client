package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.ModelOcclusion;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(RendererLivingEntity.class)
public interface ModelNamesAccess extends ModelOcclusion.Names {
 @Invoker("renderName") void unbox$renderName(EntityLivingBase entity,double x,double y,double z);
}
