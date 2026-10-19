package fuzs.iteminteractions.common.mixin.client;

import fuzs.iteminteractions.common.impl.client.handler.ClientEventHandler;
import fuzs.iteminteractions.common.impl.client.helper.ItemDecorationsHelper;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerScreen.class)
abstract class AbstractContainerScreenMixin extends Screen {

    protected AbstractContainerScreenMixin(Component component) {
        super(component);
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void extractSlot$0(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo callback) {
        ItemDecorationsHelper.setSlotBeingRendered(slot);
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void extractSlot$1(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo callback) {
        ItemDecorationsHelper.setSlotBeingRendered(null);
    }

    @Inject(method = "onMouseClickAction", at = @At("HEAD"))
    void onMouseClickAction(Slot slot, ContainerInput containerInput, CallbackInfo callback) {
        ClientEventHandler.ensureHasSentContainerClientInput(this.minecraft.player);
    }
}
