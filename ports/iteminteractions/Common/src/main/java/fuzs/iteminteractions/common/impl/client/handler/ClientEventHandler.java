package fuzs.iteminteractions.common.impl.client.handler;

import com.google.common.collect.ImmutableMap;
import fuzs.iteminteractions.common.api.v2.client.gui.screens.inventory.tooltip.SelectedItemPreviewProvider;
import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorageHolder;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.client.gui.CustomItemSlotMouseAction;
import fuzs.iteminteractions.common.impl.client.gui.ItemStorageMouseActions;
import fuzs.iteminteractions.common.impl.config.ClientConfig;
import fuzs.iteminteractions.common.impl.init.ModRegistry;
import fuzs.iteminteractions.common.impl.network.client.ServerboundContainerClientInputMessage;
import fuzs.iteminteractions.common.impl.world.item.component.ControlScheme;
import fuzs.iteminteractions.common.impl.world.item.container.ItemStorageManager;
import fuzs.puzzleslib.common.api.event.v1.core.EventResult;
import fuzs.puzzleslib.common.api.event.v1.data.MutableFloat;
import fuzs.puzzleslib.common.api.event.v1.data.MutableValue;
import fuzs.puzzleslib.common.api.network.v4.MessageSender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public class ClientEventHandler {
    private static final Set<SoundEvent> BUNDLE_SOUNDS = Set.of(SoundEvents.BUNDLE_INSERT,
            SoundEvents.BUNDLE_INSERT_FAIL,
            SoundEvents.BUNDLE_REMOVE_ONE);

    private static ControlScheme lastSentControlScheme = ControlScheme.DEFAULT;

    public static EventResult onPlaySoundAtEntity(Level level, Entity entity, MutableValue<Holder<SoundEvent>> soundEvent, MutableValue<SoundSource> soundSource, MutableFloat soundVolume, MutableFloat soundPitch) {
        // Prevent the bundle sounds from being spammed when dragging.
        // Not a nice solution, but it works.
        if (isDragging() && soundSource.get() == SoundSource.PLAYERS && BUNDLE_SOUNDS.contains(soundEvent.get()
                .value())) {
            return EventResult.INTERRUPT;
        } else {
            return EventResult.PASS;
        }
    }

    private static boolean isDragging() {
        if (Minecraft.getInstance().screen instanceof AbstractContainerScreen<?> screen) {
            for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
                if (itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction
                        && customMouseAction.isDragging()) {
                    return true;
                }
            }
        }

        return false;
    }

    public static void onItemTooltip(ItemStack itemStack, List<Component> tooltipLines, Item.TooltipContext tooltipContext, @Nullable Player player, TooltipFlag tooltipFlag) {
        // Hide vanilla container contents on tooltips, they are no longer necessary with our custom implementation.
        if (itemStack.has(DataComponents.CONTAINER) && !ItemStorageHolder.ofItem(itemStack).isEmpty()) {
            tooltipLines.removeIf((Component component) -> {
                if (component.getContents() instanceof TranslatableContents contents) {
                    return "item.container.item_count".equals(contents.getKey()) || "item.container.more_items".equals(
                            contents.getKey());
                } else {
                    return false;
                }
            });
        }

        if (player != null && !tooltipLines.isEmpty()
                && ItemInteractions.CONFIG.get(ClientConfig.class).itemStorageContentsCount) {
            int contentsCount = ItemStorageHolder.ofItem(itemStack).getContentsCount(itemStack, player);
            if (contentsCount > 0) {
                tooltipLines.set(0,
                        SelectedItemPreviewProvider.getItemNameWithCount(tooltipLines.getFirst(), contentsCount));
            }
        }
    }

    public static void onAfterInit(AbstractContainerScreen<?> screen, int screenWidth, int screenHeight, List<AbstractWidget> widgets, UnaryOperator<AbstractWidget> addWidget, Consumer<AbstractWidget> removeWidget) {
        screen.itemSlotMouseActions.addFirst(new ItemStorageMouseActions(screen));
    }

    public static void onPlayerLeave(LocalPlayer player, MultiPlayerGameMode multiPlayerGameMode, Connection connection) {
        ItemStorageManager.setItemStorageDefinitions(ImmutableMap.of());
    }

    /**
     * This must be sent before any slot click action is performed server side. For vanilla this can be caused by either
     * mouse clicks (normal menu interactions) or key presses (hotbar keys for swapping items to those slots).
     * <p>
     * All screens sending the normal click packet are handled via
     * {@link MultiPlayerGameMode#handleInventoryButtonClick(int, int)}, only the creative screen needs additional
     * handling which happens in {@link AbstractContainerScreen#onMouseClickAction(Slot, ContainerInput)}.
     * <p>
     * While the latter option works for all screens, we keep both to be extra safe, especially with other mods.
     *
     * @see MultiPlayerGameMode#ensureHasSentCarriedItem()
     */
    public static void ensureHasSentContainerClientInput(Player player) {
        ControlScheme controlScheme = ItemInteractions.CONFIG.get(ClientConfig.class).packControlScheme();
        if (!Objects.equals(controlScheme, lastSentControlScheme)) {
            lastSentControlScheme = controlScheme;
            ModRegistry.CONTROL_SCHEME_ATTACHMENT_TYPE.set(player, controlScheme);
            MessageSender.broadcast(new ServerboundContainerClientInputMessage(controlScheme));
        }
    }
}
