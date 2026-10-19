/*
 * Inventory Profiles Next
 *
 *   Copyright (c) 2019-2020 jsnimda <7615255+jsnimda@users.noreply.github.com>
 *   Copyright (c) 2021-2022 Plamen K. Kosseff <p.kosseff@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.anti_ad.mc.ipnext.mixin;


import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.anti_ad.mc.ipnext.Log;
import org.anti_ad.mc.ipnext.config.Debugs;
import org.anti_ad.mc.ipnext.config.Features;
import org.anti_ad.mc.ipnext.config.LockedSlotsSettings;
import org.anti_ad.mc.ipnext.event.LockSlotsHandler;
import org.anti_ad.mc.ipnext.event.LockedSlotKeeper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class MixinPlayerInventory {


    @Shadow @Final public NonNullList<ItemStack> items;

    @Unique
    private ItemStack addedStack = null;

    @Inject(at = @At(value = "HEAD"),
            method = "getFreeSlot",
            cancellable = true)
    public void getEmptySlot(CallbackInfoReturnable<Integer> info) {
        var inventoryPlayer = ((Inventory)((Object)this)).player;
        if (inventoryPlayer.equals(Minecraft.getInstance().player)) {
            if (Features.INSTANCE.getENABLE_LOCK_SLOTS().getValue() &&
                !LockedSlotsSettings.INSTANCE.getLOCKED_SLOTS_ALLOW_PICKUP_INTO_EMPTY().getValue()
                && !Debugs.INSTANCE.getFORCE_SERVER_METHOD_FOR_LOCKED_SLOTS().getValue()) {
                Log.INSTANCE.trace("Added stack = %s".formatted(addedStack != null ? addedStack.getItem().toString() : "null"));
                for (int i = 0; i < this.items.size(); ++i) {
                    if (LockedSlotsSettings.INSTANCE.getLOCKED_SLOTS_EMPTY_HOTBAR_AS_SEMI_LOCKED().getValue()
                            && Inventory.isHotbarSlot(i)
                            && !LockedSlotKeeper.INSTANCE.isOnlyHotbarFree()
                            && !LockedSlotKeeper.INSTANCE.isIgnored(addedStack)
                            && LockedSlotKeeper.INSTANCE.isHotBarSlotEmpty(i)) {
                        continue;
                    }
                    if (!LockSlotsHandler.INSTANCE.isSlotLocked(i)) {
                        if ((this.items.get(i)).isEmpty()) {
                            info.setReturnValue(i);
                            return;
                        }
                    }
                }
                info.setReturnValue(-1);
            }
        }
    }

    @Inject(at = @At(value = "HEAD"),
            method = "addResource(Lnet/minecraft/world/item/ItemStack;)I")
    public void addStackPre(ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        addedStack = itemStack;
        Log.INSTANCE.trace("1. Added stack = %s".formatted(addedStack != null ? addedStack.getItem().toString() : "null"));
    }

    @Inject(at = @At(value = "TAIL"),
            method = "addResource(Lnet/minecraft/world/item/ItemStack;)I")
    public void addStackPost(ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        Log.INSTANCE.trace("1. Removed stack = %s".formatted(addedStack != null ? addedStack.getItem().toString() : "null"));
        addedStack = null;
    }

    @Inject(at = @At(value = "HEAD"),
            method = "addResource(ILnet/minecraft/world/item/ItemStack;)I")
    public void addStackPre(int slot, ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        Log.INSTANCE.trace("2. Added stack = %s".formatted(addedStack != null ? addedStack.getItem().toString() : "null"));
        addedStack = itemStack;
    }

    @Inject(at = @At(value = "TAIL"),
            method = "addResource(ILnet/minecraft/world/item/ItemStack;)I")
    public void addStackPost(int slot, ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        Log.INSTANCE.trace("2. Removed stack = %s".formatted(addedStack != null ? addedStack.getItem().toString() : "null"));
        addedStack = null;
    }

    @Inject(at = @At(value = "HEAD"),
            method = "add(ILnet/minecraft/world/item/ItemStack;)Z")
    public void addPre(int slot, ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        Log.INSTANCE.trace("2. Added stack = %s".formatted(addedStack != null ? addedStack.getItem().toString() : "null"));
        addedStack = itemStack;
    }

    @Inject(at = @At(value = "TAIL"),
            method = "add(ILnet/minecraft/world/item/ItemStack;)Z")
    public void addPost(int slot, ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        Log.INSTANCE.trace("2. Removed stack = %s".formatted(addedStack != null ? addedStack.getItem().toString() : "null"));
        addedStack = null;
    }


    @Inject(at = @At(value = "HEAD"),
            method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V")
    public void placeItemBackInInventoryPre$IPN(ItemStack itemStack, boolean shouldSendSetSlotPacket, CallbackInfo ci) {
        Log.INSTANCE.trace("3. Added stack = %s".formatted(addedStack != null ? addedStack.getItem().toString() : "null"));
        addedStack = itemStack;
    }

    @Inject(at = @At(value = "TAIL"),
            method = "placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;Z)V")
    public void placeItemBackInInventoryPost$IPN(ItemStack itemStack, boolean shouldSendSetSlotPacket, CallbackInfo ci) {
        Log.INSTANCE.trace("3. Removed stack = %s".formatted(addedStack != null ? addedStack.getItem().toString() : "null"));
        addedStack = null;
    }



}
