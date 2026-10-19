package fuzs.iteminteractions.common.mixin.client;

import fuzs.iteminteractions.common.impl.client.handler.ClientEventHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "handleContainerInput", at = @At("HEAD"))
    public void handleContainerInput(int containerId, int slotNum, int buttonNum, ContainerInput containerInput, Player player, CallbackInfo callback) {
        if (containerId == player.containerMenu.containerId) {
            ClientEventHandler.ensureHasSentContainerClientInput(player);
        }
    }
}
