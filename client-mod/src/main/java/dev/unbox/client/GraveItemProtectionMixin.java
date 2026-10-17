package dev.unbox.client.mixin;

import dev.unbox.client.GraveKeeper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents creative-mode's vanilla overflow behavior from silently deleting protected grave items. */
@Mixin(ItemEntity.class)
public abstract class GraveItemProtectionMixin {
    @Unique private net.minecraft.world.phys.Vec3 unbox$graveAnchor;
    @Inject(method="tick",at=@At("HEAD"))
    private void unbox$anchorStart(CallbackInfo ci){ItemEntity self=(ItemEntity)(Object)this;if(self.entityTags().stream().anyMatch(t->t.startsWith("unbox.grave.")))unbox$graveAnchor=self.position();else unbox$graveAnchor=null;}
    @Inject(method="tick",at=@At("RETURN"))
    private void unbox$anchorEnd(CallbackInfo ci){if(unbox$graveAnchor==null)return;ItemEntity self=(ItemEntity)(Object)this;if(self.isAlive()){self.setPos(unbox$graveAnchor);self.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);}}
    @Inject(method="playerTouch",at=@At("HEAD"),cancellable=true)
    private void unbox$keepOverflow(Player player,CallbackInfo ci) {
        ItemEntity self=(ItemEntity)(Object)this;
        if(!GraveKeeper.protectedItem(self))return;
        if(!self.entityTags().contains("unbox.owner."+player.getUUID())){ci.cancel();return;}
        var inventory=player.getInventory();
        if(inventory.getFreeSlot()<0&&inventory.getSlotWithRemainingSpace(self.getItem())<0)ci.cancel();
        // Creative Inventory.add deletes any remainder, even when only part of the stack fits.
        if(player.getAbilities().instabuild){
            ItemStack incoming=self.getItem();
            int capacity=0;
            for(int slot=0;slot<36;slot++){
                ItemStack current=inventory.getItem(slot);
                if(current.isEmpty())capacity+=incoming.getMaxStackSize();
                else if(ItemStack.isSameItemSameComponents(current,incoming))capacity+=Math.max(0,current.getMaxStackSize()-current.getCount());
            }
            ItemStack offhand=player.getOffhandItem();
            if(ItemStack.isSameItemSameComponents(offhand,incoming))capacity+=Math.max(0,offhand.getMaxStackSize()-offhand.getCount());
            if(capacity<incoming.getCount())ci.cancel();
        }
    }

    @Inject(method="isMergable",at=@At("HEAD"),cancellable=true)
    private void unbox$keepOwnership(CallbackInfoReturnable<Boolean> cir){
        if(GraveKeeper.protectedItem((ItemEntity)(Object)this))cir.setReturnValue(false);
    }

    // Vanilla mobs consult this method; playerTouch uses its own delay field and UUID check.
    @Inject(method="hasPickUpDelay",at=@At("HEAD"),cancellable=true)
    private void unbox$preventMobPickup(CallbackInfoReturnable<Boolean> cir){
        if(GraveKeeper.protectedItem((ItemEntity)(Object)this))cir.setReturnValue(true);
    }
}
