package dev.unbox.client.mixin;

import dev.unbox.client.GraveKeeper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class GraveDeathInventoryMixin {
    // Reached only once death proceeds to loot, after NeoForge's cancellable death hook.
    @Inject(method="dropEquipment", at=@At("HEAD"))
    private void unbox$storeAcceptedDeath(ServerLevel level, CallbackInfo ci) {
        if ((Object)this instanceof ServerPlayer player) GraveKeeper.onDeath(player);
    }
}
