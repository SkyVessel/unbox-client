package dev.unbox.client.mixin;
import dev.unbox.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="nl.requios.effortlessbuilding.network.PacketHandler",remap=false)
public abstract class CreativeBuildServerGateMixin {
 @Inject(method={"handlePlaceBuildMode","handleBreakBuildMode","handleUpdateModifiers"},at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$packet(@Coerce Object packet,net.minecraft.server.level.ServerPlayer player,CallbackInfo ci){if(!WorldFeatures.buildingServer(player))ci.cancel();}
 @Inject(method={"handleUndo","handleRedo"},at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$undo(net.minecraft.server.level.ServerPlayer player,CallbackInfo ci){if(!WorldFeatures.buildingServer(player))ci.cancel();}
 @Inject(method="sendToServer",at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$send(CallbackInfo ci){if(!WorldFeatures.buildingClient())ci.cancel();}
}
