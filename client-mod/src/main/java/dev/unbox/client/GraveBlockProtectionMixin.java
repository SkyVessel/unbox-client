package dev.unbox.client.mixin;

import dev.unbox.client.GraveKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class GraveBlockProtectionMixin {
    @Inject(method = "destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z",
        at = @At("HEAD"), cancellable = true)
    private void unbox$preventGraveDrops(BlockPos pos, boolean drop, Entity cause, int recursion,
                                        CallbackInfoReturnable<Boolean> cir) {
        if (GraveKeeper.blocksReplacement((Level)(Object)this, pos)) cir.setReturnValue(false);
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
        at = @At("HEAD"), cancellable = true)
    private void unbox$keepGrave(BlockPos pos, BlockState state, int flags, int recursion,
                                CallbackInfoReturnable<Boolean> cir) {
        if (GraveKeeper.blocksReplacement((Level)(Object)this, pos)) cir.setReturnValue(false);
    }
}
