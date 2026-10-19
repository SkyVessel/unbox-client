package fuzs.iteminteractions.common.impl.client.handler;

import fuzs.iteminteractions.common.impl.client.gui.CustomItemSlotMouseAction;
import fuzs.puzzleslib.common.api.client.gui.v2.ScreenHelper;
import fuzs.puzzleslib.common.api.event.v1.core.EventResult;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.ItemSlotMouseAction;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;

import java.util.OptionalInt;

public class ItemSlotMouseActionHandler {

    public static void onAfterBackground(AbstractContainerScreen<?> screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
            if (itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction) {
                customMouseAction.onExtractBackground(guiGraphics, mouseX, mouseY, partialTicks);
            }
        }
    }

    public static void onAfterForeground(AbstractContainerScreen<?> screen, GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
            if (itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction) {
                customMouseAction.onExtractForeground(guiGraphics, mouseX, mouseY, ScreenHelper.getPartialTick());
            }
        }
    }

    public static EventResult onBeforeMouseClicked(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
        ItemStack itemHeldByCursor = screen.getMenu().getCarried();
        if (!itemHeldByCursor.isEmpty()) {
            for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
                if (itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction && customMouseAction.matches(
                        itemHeldByCursor) && customMouseAction.onMouseClicked(event,
                        OptionalInt.empty(),
                        itemHeldByCursor)) {
                    return EventResult.INTERRUPT;
                }
            }
        }

        if (screen.hoveredSlot != null && screen.hoveredSlot.hasItem()) {
            for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
                if (itemMouseAction.matches(screen.hoveredSlot)
                        && itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction
                        && customMouseAction.onMouseClicked(event,
                        OptionalInt.of(screen.hoveredSlot.index),
                        screen.hoveredSlot.getItem())) {
                    return EventResult.INTERRUPT;
                }
            }
        }

        return EventResult.PASS;
    }

    public static EventResult onBeforeMouseRelease(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
        ItemStack itemHeldByCursor = screen.getMenu().getCarried();
        if (!itemHeldByCursor.isEmpty()) {
            for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
                if (itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction && customMouseAction.matches(
                        itemHeldByCursor) && customMouseAction.onMouseReleased(event,
                        OptionalInt.empty(),
                        itemHeldByCursor)) {
                    return EventResult.INTERRUPT;
                }
            }
        }

        if (screen.hoveredSlot != null && screen.hoveredSlot.hasItem()) {
            for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
                if (itemMouseAction.matches(screen.hoveredSlot)
                        && itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction
                        && customMouseAction.onMouseReleased(event,
                        OptionalInt.of(screen.hoveredSlot.index),
                        screen.hoveredSlot.getItem())) {
                    return EventResult.INTERRUPT;
                }
            }
        }

        return EventResult.PASS;
    }

    public static EventResult onBeforeMouseDragged(AbstractContainerScreen<?> screen, MouseButtonEvent event, double dragX, double dragY) {
        ItemStack itemHeldByCursor = screen.getMenu().getCarried();
        if (!itemHeldByCursor.isEmpty()) {
            for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
                if (itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction && customMouseAction.matches(
                        itemHeldByCursor) && customMouseAction.onMouseDragged(event,
                        dragX,
                        dragY,
                        OptionalInt.empty(),
                        itemHeldByCursor)) {
                    return EventResult.INTERRUPT;
                }
            }
        }

        if (screen.hoveredSlot != null && screen.hoveredSlot.hasItem()) {
            for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
                if (itemMouseAction.matches(screen.hoveredSlot)
                        && itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction
                        && customMouseAction.onMouseDragged(event,
                        dragX,
                        dragY,
                        OptionalInt.of(screen.hoveredSlot.index),
                        screen.hoveredSlot.getItem())) {
                    return EventResult.INTERRUPT;
                }
            }
        }

        return EventResult.PASS;
    }

    public static EventResult onBeforeMouseScroll(AbstractContainerScreen<?> screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // The hovered slot case for this event is already handled by vanilla.
        ItemStack itemHeldByCursor = screen.getMenu().getCarried();
        if (!itemHeldByCursor.isEmpty()) {
            for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
                if (itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction && customMouseAction.matches(
                        itemHeldByCursor)) {
                    customMouseAction.onMouseScrolled(horizontalAmount,
                            verticalAmount,
                            OptionalInt.empty(),
                            itemHeldByCursor);
                    return EventResult.INTERRUPT;
                }
            }
        }

        return EventResult.PASS;
    }

    public static EventResult onBeforeKeyPress(AbstractContainerScreen<?> screen, KeyEvent keyEvent) {
        ItemStack itemHeldByCursor = screen.getMenu().getCarried();
        if (!itemHeldByCursor.isEmpty()) {
            for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
                if (itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction && customMouseAction.matches(
                        itemHeldByCursor) && customMouseAction.onKeyPressed(keyEvent,
                        OptionalInt.empty(),
                        itemHeldByCursor)) {
                    return EventResult.INTERRUPT;
                }
            }
        }

        if (screen.hoveredSlot != null && screen.hoveredSlot.hasItem()) {
            for (ItemSlotMouseAction itemMouseAction : screen.itemSlotMouseActions) {
                if (itemMouseAction.matches(screen.hoveredSlot)
                        && itemMouseAction instanceof CustomItemSlotMouseAction customMouseAction
                        && customMouseAction.onKeyPressed(keyEvent,
                        OptionalInt.of(screen.hoveredSlot.index),
                        screen.hoveredSlot.getItem())) {
                    return EventResult.INTERRUPT;
                }
            }
        }

        return EventResult.PASS;
    }
}
