package dev.unbox.client.mixin;

import dev.unbox.client.GraveModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class GraveHardnessMixin {
    @Inject(method="getDestroySpeed", at=@At("HEAD"), cancellable=true)
    private void unbox$stoneGraveHardness(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (((BlockState)(Object)this).is(Blocks.STRUCTURE_VOID) && GraveModel.isGraveAnchor(level, pos))
            cir.setReturnValue(Blocks.STONE.defaultBlockState().getDestroySpeed(level, pos));
    }
    @Inject(method="getDestroyProgress", at=@At("HEAD"), cancellable=true)
    private void unbox$stoneToolSpeed(net.minecraft.world.entity.player.Player player, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (((BlockState)(Object)this).is(Blocks.STRUCTURE_VOID) && GraveModel.isGraveAnchor(level, pos))
            cir.setReturnValue(Blocks.STONE.defaultBlockState().getDestroyProgress(player,level,pos));
    }
    @Inject(method={
        "getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
        "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;"
    }, at=@At("HEAD"), cancellable=true)
    private void unbox$graveShape(BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (((BlockState)(Object)this).is(Blocks.STRUCTURE_VOID) && GraveModel.isGraveAnchor(level, pos))
            cir.setReturnValue(GraveModel.shape());
    }

    // This overload returns vanilla's cached invisible-anchor shape without calling the context overload.
    @Inject(method="getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;", at=@At("HEAD"), cancellable=true)
    private void unbox$graveCachedCollision(BlockGetter level, BlockPos pos, CallbackInfoReturnable<VoxelShape> cir) {
        if (((BlockState)(Object)this).is(Blocks.STRUCTURE_VOID) && GraveModel.isGraveAnchor(level, pos))
            cir.setReturnValue(GraveModel.shape());
    }
}
