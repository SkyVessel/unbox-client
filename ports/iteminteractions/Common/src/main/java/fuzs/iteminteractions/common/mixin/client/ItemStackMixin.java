package fuzs.iteminteractions.common.mixin.client;

import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorageHolder;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.config.ClientConfig;
import fuzs.puzzleslib.common.api.util.v1.CommonHelper;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(ItemStack.class)
abstract class ItemStackMixin {

    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    public void getTooltipImage(CallbackInfoReturnable<Optional<TooltipComponent>> callback) {
        ItemStack itemStack = ItemStack.class.cast(this);
        ItemStorageHolder.ofItem(itemStack)
                .getTooltipImage(itemStack, CommonHelper.getClientPlayer())
                .ifPresent(callback::setReturnValue);
    }

    @Inject(method = "isBarVisible", at = @At("HEAD"), cancellable = true)
    public void isBarVisible(CallbackInfoReturnable<Boolean> callback) {
        if (!ItemInteractions.CONFIG.get(ClientConfig.class).overrideItemStorageBar) {
            return;
        }

        ItemStack itemStack = ItemStack.class.cast(this);
        ItemStorageHolder.ofItem(itemStack)
                .isBarVisible(itemStack, CommonHelper.getClientPlayer())
                .ifPresent(callback::setReturnValue);
    }

    @Inject(method = "getBarWidth", at = @At("HEAD"), cancellable = true)
    public void getBarWidth(CallbackInfoReturnable<Integer> callback) {
        if (!ItemInteractions.CONFIG.get(ClientConfig.class).overrideItemStorageBar) {
            return;
        }

        ItemStack itemStack = ItemStack.class.cast(this);
        ItemStorageHolder.ofItem(itemStack)
                .getBarWidth(itemStack, CommonHelper.getClientPlayer())
                .ifPresent(callback::setReturnValue);
    }

    @Inject(method = "getBarColor", at = @At("HEAD"), cancellable = true)
    public void getBarColor(CallbackInfoReturnable<Integer> callback) {
        if (!ItemInteractions.CONFIG.get(ClientConfig.class).overrideItemStorageBar) {
            return;
        }

        ItemStack itemStack = ItemStack.class.cast(this);
        ItemStorageHolder.ofItem(itemStack)
                .getBarColor(itemStack, CommonHelper.getClientPlayer())
                .ifPresent(callback::setReturnValue);
    }
}
