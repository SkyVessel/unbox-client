package dev.unbox.client.mixin;

import dev.unbox.client.GraveKeeper;
import dev.unbox.client.SurvivalHostMods;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="de.miraculixx.veinminer.event.VeinMinerEvent",remap=false)
public abstract class VeinMinerGateMixin {
    @Inject(method="allowedToVeinmine",at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$hostOnly(Level level,Player player,BlockPos pos,BlockState state,CallbackInfoReturnable<Object> cir){
        if(!SurvivalHostMods.serverEnabled("veinminer")||GraveKeeper.isGrave(level,pos))cir.setReturnValue(null);
    }
    @Inject(method="veinmine",at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$enabled(CallbackInfoReturnable<Integer> cir){
        if(!SurvivalHostMods.serverEnabled("veinminer"))cir.setReturnValue(0);
    }
    @Inject(method="destroyBlock",at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$protectStoredGraves(BlockState state,ItemStack tool,Level level,BlockPos pos,Player player,BlockPos origin,CallbackInfo ci){
        if(!SurvivalHostMods.serverEnabled("veinminer")||GraveKeeper.isGrave(level,pos))ci.cancel();
    }
}
