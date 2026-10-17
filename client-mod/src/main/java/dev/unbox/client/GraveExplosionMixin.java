package dev.unbox.client.mixin;

import dev.unbox.client.GraveKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.BiConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockBehaviour.class)
public abstract class GraveExplosionMixin {
    @Inject(method = "onExplosionHit", at = @At("HEAD"), cancellable = true)
    private void unbox$protectGrave(BlockState state, ServerLevel level, BlockPos pos, Explosion explosion,
                                   BiConsumer<ItemStack, BlockPos> drops, CallbackInfo ci) {
        if (GraveKeeper.isGrave(level, pos)) ci.cancel();
    }
}
