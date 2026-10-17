package dev.unbox.client.mixin;

import dev.unbox.client.GraveKeeper;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class GravePlayerMixin {
    @Inject(method="tick", at=@At("TAIL"))
    private void unbox$cleanupGrave(CallbackInfo ci) {
        GraveKeeper.cleanup((ServerPlayer)(Object)this);
    }
}
