package fuzs.iteminteractions.common.impl.handler;

import fuzs.iteminteractions.common.impl.network.ClientboundEnderChestContentMessage;
import fuzs.iteminteractions.common.impl.network.ClientboundEnderChestSlotMessage;
import fuzs.iteminteractions.common.impl.network.client.ServerboundEnderChestContentMessage;
import fuzs.puzzleslib.common.api.network.v4.MessageSender;
import fuzs.puzzleslib.common.api.network.v4.PlayerSet;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class EnderChestSyncHandler {

    public static void onPlayerJoin(ServerPlayer serverPlayer) {
        broadcastFullState(serverPlayer);
    }

    public static void onAfterChangeDimension(ServerPlayer serverPlayer, ServerLevel originalLevel, ServerLevel newLevel) {
        broadcastFullState(serverPlayer);
    }

    public static void onRespawn(ServerPlayer serverPlayer, boolean originalStillAlive) {
        broadcastFullState(serverPlayer);
    }

    public static void onContainerOpen(ServerPlayer serverPlayer, AbstractContainerMenu container) {
        if (container instanceof ChestMenu chestMenu
                && chestMenu.getContainer() == serverPlayer.getEnderChestInventory()) {
            broadcastFullState(serverPlayer);
            chestMenu.addSlotListener(new ContainerListener() {
                @Override
                public void slotChanged(AbstractContainerMenu menu, int slotIndex, ItemStack itemStack) {
                    // Vanilla only syncs ender chest contents to the open ender chest menu, but not to the ender chest container stored on the player.
                    // We use the player ender chest container both on the client & server, though, so we sync all contents manually.
                    Slot slot = menu.getSlot(slotIndex);
                    if (slot.container == serverPlayer.getEnderChestInventory()) {
                        MessageSender.broadcast(PlayerSet.ofPlayer(serverPlayer),
                                new ClientboundEnderChestSlotMessage(slot.getContainerSlot(), itemStack));
                    }
                }

                @Override
                public void dataChanged(AbstractContainerMenu menu, int dataIndex, int dataValue) {
                    // NO-OP
                }
            });
        }
    }

    public static void broadcastCreativeState(Player player) {
        // This is only required for the creative mode inventory, as it doesn't sync contents using default menu packets.
        // Instead, it uses custom packets which do not work for item interactions in a menu.
        if (player.hasInfiniteMaterials()
                && player.containerMenu instanceof CreativeModeInventoryScreen.ItemPickerMenu) {
            MessageSender.broadcast(new ServerboundEnderChestContentMessage(player.getEnderChestInventory()
                    .getItems()));
        }
    }

    public static void broadcastFullState(ServerPlayer serverPlayer) {
        MessageSender.broadcast(PlayerSet.ofPlayer(serverPlayer),
                new ClientboundEnderChestContentMessage(serverPlayer.getEnderChestInventory().getItems()));
    }

    public static void setEnderChestContent(Player player, List<ItemStack> items) {
        Container container = player.getEnderChestInventory();
        // Safeguard against mods only changing ender chest size on one side.
        int size = Math.min(items.size(), container.getContainerSize());
        for (int i = 0; i < size; ++i) {
            container.setItem(i, items.get(i));
        }
    }
}
