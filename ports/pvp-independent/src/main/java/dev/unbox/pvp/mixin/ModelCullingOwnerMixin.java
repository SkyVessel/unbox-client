package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.ModelOcclusion;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Living models handled below retain original name rendering; other entities keep EntityCulling. */
@Mixin(targets="meldexun.renderlib.renderer.entity.EntityRenderer",remap=false,priority=500)
public class ModelCullingOwnerMixin {
 @Inject(method="isOcclusionCulled",at=@At("HEAD"),cancellable=true) private void owner(Entity entity,CallbackInfoReturnable<Boolean> ci){if(ModelOcclusion.active&&ModelOcclusion.handles(entity))ci.setReturnValue(false);}
}
