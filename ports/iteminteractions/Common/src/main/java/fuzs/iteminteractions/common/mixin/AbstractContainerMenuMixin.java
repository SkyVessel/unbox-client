package fuzs.iteminteractions.common.mixin;

import fuzs.iteminteractions.common.impl.handler.ContainerClickInputHandler;
import fuzs.puzzleslib.common.api.event.v1.core.EventResult;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerMenu.class)
abstract class AbstractContainerMenuMixin {

    @Inject(method = "tryItemClickBehaviourOverride", at = @At("HEAD"), cancellable = true)
    private void tryItemClickBehaviourOverride(Player player, ClickAction clickAction, Slot slot, ItemStack clicked, ItemStack carried, CallbackInfoReturnable<Boolean> callback) {
        EventResult eventResult = ContainerClickInputHandler.onContainerItemClicked(clicked,
                slot,
                carried,
                this.createCarriedSlotAccess(),
                clickAction,
                player);
        if (eventResult.isInterrupt()) {
            callback.setReturnValue(eventResult.getAsBoolean());
        }
    }

    @Shadow
    private SlotAccess createCarriedSlotAccess() {
        throw new RuntimeException();
    }
}
