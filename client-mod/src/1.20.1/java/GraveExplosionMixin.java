package dev.unbox.client.mixin;
import dev.unbox.client.GraveKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ExplosionDamageCalculator.class)public abstract class GraveExplosionMixin {
 @Inject(method="shouldBlockExplode",at=@At("HEAD"),cancellable=true)private void protect(Explosion explosion,BlockGetter level,BlockPos pos,BlockState state,float power,CallbackInfoReturnable<Boolean> ci){if(level instanceof net.minecraft.server.level.ServerLevel server&&GraveKeeper.isGrave(server,pos))ci.setReturnValue(false);}
}
