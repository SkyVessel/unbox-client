package dev.unbox.client.mixin;

import dev.unbox.client.WorldMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class MapBlockUpdateMixin {
    @Inject(method="setBlocksDirty",at=@At("TAIL"))
    private void unbox$mapChanged(BlockPos pos,BlockState before,BlockState after,CallbackInfo ci){WorldMap.blockChanged(pos);}
}
