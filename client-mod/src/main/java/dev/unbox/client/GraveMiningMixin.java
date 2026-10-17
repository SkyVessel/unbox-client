package dev.unbox.client.mixin;

import dev.unbox.client.GraveKeeper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayerGameMode.class)
public abstract class GraveMiningMixin {
    @Shadow @Final protected ServerPlayer player;
    @Shadow private int gameTicks;
    @Shadow private int delayedTickStart;
    @Shadow private BlockPos delayedDestroyPos;

    // 26.3 changed this helper to accept elapsed ticks, but the delayed branch
    // still passes its start tick. Limit the correction to our stored graves.
    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayerGameMode;incrementDestroyProgress(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;I)F", ordinal = 0), index = 2, require = 1, expect = 1)
    private int unbox$elapsedGraveMiningTicks(int original) {
        return net.minecraft.SharedConstants.getCurrentVersion().id().equals("26.3")
            && GraveKeeper.isGrave(player.level(), delayedDestroyPos)
            ? gameTicks - delayedTickStart : original;
    }

    // handleBlockBreakAction/tick retain vanilla stone hardness, tool speed and reach validation.
    @Inject(method = "destroyBlock", at = @At("HEAD"), cancellable = true)
    private void unbox$mineGrave(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (GraveKeeper.isGrave(player.level(), pos)) cir.setReturnValue(GraveKeeper.mine(player, pos));
    }
}
