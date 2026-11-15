// Adapted from Yedelo Antimations (LGPL-3.0).
package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;




import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;



@Mixin(ItemRenderer.class)
public abstract class HandAnimationsMixin {
	@Shadow private ItemStack itemToRender;
	@Shadow @Final private RenderItem itemRenderer;
	@Shadow private int equippedItemSlot;
	@Shadow @Final private Minecraft mc;

	@Redirect(method = "updateEquippedItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getIsItemStackEqual(Lnet/minecraft/item/ItemStack;)Z"))
	private boolean antimations$simplifyEqual(ItemStack instance, ItemStack p_179549_1_) {
		if (PvpSettings.on("hand")) {
			if (PvpSettings.flag("hand.cancelAllHandResets", false)) {
				EntityPlayer player = Minecraft.getMinecraft().thePlayer;
				this.itemToRender = player.inventory.getCurrentItem();
				this.equippedItemSlot = player.inventory.currentItem;
				return true;
			}
			if (PvpSettings.flag("hand.cancelItemUpdateHandResets", false)) {
				if (equippedItemSlot != mc.thePlayer.inventory.currentItem) return false;
				return itemRenderer.getItemModelMesher().getItemModel(instance) == itemRenderer.getItemModelMesher().getItemModel(p_179549_1_);
			}
		}
		return instance.getIsItemStackEqual(p_179549_1_);
	}

	@Redirect(method = "renderItemInFirstPerson", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;getItemUseAction()Lnet/minecraft/item/EnumAction;"))
	private EnumAction antimations$cancelFirstPersonAnimations(ItemStack instance) {
		EnumAction itemUseAction = itemToRender.getItemUseAction();
		if (
			PvpSettings.on("hand") && (
				(PvpSettings.flag("hand.cancelOwnBlockAnimations", false) && itemUseAction == EnumAction.BLOCK)
					||
					(PvpSettings.flag("hand.cancelOwnBowAnimations", false) && itemUseAction == EnumAction.BOW)
					||
					(PvpSettings.flag("hand.cancelEatingAnimations", false) && itemUseAction == EnumAction.EAT)
					||
					(PvpSettings.flag("hand.cancelDrinkingAnimations", false) && itemUseAction == EnumAction.DRINK)
			)
		) {
			return EnumAction.NONE;
		}
		else return instance.getItemUseAction();
	}

	@Inject(method = "resetEquippedProgress", at = @At("HEAD"), cancellable = true)
	private void antimations$onUseItem(CallbackInfo ci) {
		if (PvpSettings.on("hand") && PvpSettings.flag("hand.cancelItemUseHandResets", false)) {
			ci.cancel();
		}
	}

	@Inject(method = "resetEquippedProgress2", at = @At("HEAD"), cancellable = true)
	private void antimations$onConsumeItem(CallbackInfo ci) {
		if (PvpSettings.on("hand") && PvpSettings.flag("hand.cancelItemUseHandResets", false)) {
			ci.cancel();
		}
	}
}
