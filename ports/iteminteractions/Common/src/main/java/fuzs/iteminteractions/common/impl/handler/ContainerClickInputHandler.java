package fuzs.iteminteractions.common.impl.handler;

import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorageHolder;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.puzzleslib.common.api.event.v1.core.EventResult;
import fuzs.puzzleslib.common.api.network.v4.NetworkingHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ContainerClickInputHandler {

    public static EventResult onContainerItemClicked(ItemStack hoveredItem, Slot hoveredSlot, ItemStack itemHeldByCursor, SlotAccess slotHeldByCursor, ClickAction clickAction, Player player) {
        // Filter out vanilla clients, so they do not trigger any interactions from the mod.
        if (player instanceof ServerPlayer serverPlayer && !NetworkingHelper.isModPresentClientside(serverPlayer,
                ItemInteractions.MOD_ID)) {
            return EventResult.PASS;
        }

        ItemStorageHolder holderHeldByCursor = ItemStorageHolder.ofItem(itemHeldByCursor);
        ItemStorageHolder hoveredHolder = ItemStorageHolder.ofItem(hoveredItem);
        if (holderHeldByCursor.allowModification(itemHeldByCursor, player)) {
            if (holderHeldByCursor.overrideStackedOnOther(itemHeldByCursor,
                    slotHeldByCursor,
                    hoveredItem,
                    hoveredSlot,
                    clickAction,
                    player)) {
                return EventResult.ALLOW;
            }

            // The item held by the cursor did not handle the interaction, so try the hovered item instead.
            if (hoveredHolder.allowModification(hoveredItem, player) && hoveredHolder.overrideOtherStackedOnMe(
                    hoveredItem,
                    hoveredSlot,
                    itemHeldByCursor,
                    slotHeldByCursor,
                    clickAction,
                    player)) {
                return EventResult.ALLOW;
            } else {
                return EventResult.DENY;
            }
        } else if (hoveredHolder.allowModification(hoveredItem, player)) {
            return hoveredHolder.overrideOtherStackedOnMe(hoveredItem,
                    hoveredSlot,
                    itemHeldByCursor,
                    slotHeldByCursor,
                    clickAction,
                    player) ? EventResult.ALLOW : EventResult.DENY;
        } else {
            return EventResult.PASS;
        }
    }
}
