package fuzs.iteminteractions.common.impl.client.gui;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.InputConstants;
import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorageHolder;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.client.helper.ItemDecorationsHelper;
import fuzs.iteminteractions.common.impl.config.ClickActionScheme;
import fuzs.iteminteractions.common.impl.config.ClientConfig;
import fuzs.iteminteractions.common.impl.config.ServerConfig;
import fuzs.iteminteractions.common.impl.network.client.ServerboundSelectedItemMessage;
import fuzs.iteminteractions.common.impl.world.inventory.ItemSlot;
import fuzs.iteminteractions.common.impl.world.item.component.SelectedItem;
import fuzs.puzzleslib.common.api.network.v4.MessageSender;
import net.minecraft.client.gui.BundleMouseActions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2i;
import org.joml.Vector2ic;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import java.util.Stack;

/**
 * @see BundleMouseActions
 */
public class ItemStorageMouseActions extends BundleMouseActions implements CustomItemSlotMouseAction {
    private final AbstractContainerScreen<?> screen;
    private final Set<Slot> clickedDraggingSlots = Sets.newIdentityHashSet();
    private final Set<Slot> allDraggingSlots = Sets.newIdentityHashSet();
    @Nullable
    private ClickAction clickAction;
    private final Stack<ItemSlot> nestedStorageItem = new Stack<>();
    private OptionalInt nestedStorageItemSlot = OptionalInt.of(-1);

    public ItemStorageMouseActions(AbstractContainerScreen<?> screen) {
        super(screen.minecraft);
        this.screen = screen;
    }

    @Override
    public boolean matches(Slot slot) {
        return CustomItemSlotMouseAction.super.matches(slot);
    }

    @Override
    public boolean matches(ItemStack itemStack) {
        return !ItemStorageHolder.ofItem(itemStack).isEmpty();
    }

    @Override
    public void onExtractBackground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.clickedDraggingSlots.isEmpty()) {
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(this.screen.leftPos, this.screen.topPos);
            this.extractSlotHighlights(guiGraphics, mouseX, mouseY, AbstractContainerScreen.SLOT_HIGHLIGHT_BACK_SPRITE);
            guiGraphics.pose().popMatrix();
        }

        ItemStack itemStack = this.getHoveredSlotTooltipItem();
        ItemStorageHolder holder = ItemStorageHolder.ofItem(itemStack);
        if (this.allowModification(holder, itemStack)) {
            guiGraphics.setTooltipForNextFrame(this.screen.getFont(),
                    this.screen.getTooltipFromContainerItem(itemStack),
                    itemStack.getTooltipImage(),
                    mouseX,
                    mouseY,
                    itemStack.get(DataComponents.TOOLTIP_STYLE));
        }
    }

    /**
     * Finds an item to override the hovered item tooltip with, like the item held by the cursor. To be used with the
     * single item moving feature to be able to continuously see what's going on.
     *
     * @see AbstractContainerScreen#extractTooltip(GuiGraphicsExtractor, int, int)
     */
    private ItemStack getHoveredSlotTooltipItem() {
        boolean itemHeldByCursorTooltip = ItemInteractions.CONFIG.get(ClientConfig.class).itemHeldByCursorTooltip.isUsed();
        if (!this.nestedStorageItem.isEmpty() && (this.nestedStorageItemSlot.isPresent() || itemHeldByCursorTooltip)) {
            return this.nestedStorageItem.peek().item();
        } else if (itemHeldByCursorTooltip) {
            return this.screen.getMenu().getCarried();
        } else {
            return ItemStack.EMPTY;
        }
    }

    @Override
    public void onExtractForeground(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.clickedDraggingSlots.isEmpty()) {
            this.extractSlotHighlights(guiGraphics,
                    mouseX,
                    mouseY,
                    AbstractContainerScreen.SLOT_HIGHLIGHT_FRONT_SPRITE);
        }
    }

    private void extractSlotHighlights(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, Identifier slotHighlightSprite) {
        for (Slot slot : this.screen.getMenu().slots) {
            if (slot.isHighlightable() && this.clickedDraggingSlots.contains(slot)) {
                // slots will sometimes be added to dragged slots when simply clicking on a slot, so don't render our overlay then
                if (this.clickedDraggingSlots.size() > 1 || !this.screen.isHovering(slot, mouseX, mouseY)) {
                    guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                            slotHighlightSprite,
                            slot.x - 4,
                            slot.y - 4,
                            24,
                            24);
                }
            }
        }
    }

    @Override
    public boolean onMouseClicked(MouseButtonEvent event, OptionalInt slotIndex, ItemStack itemStack) {
        if (this.blockAllMouseActions(slotIndex)) {
            return true;
        }

        if (!ItemInteractions.CONFIG.get(ServerConfig.class).enableMouseDragging || slotIndex.isPresent()) {
            return false;
        }

        this.clearDraggingSlots();
        ItemStorageHolder holder = ItemStorageHolder.ofItem(itemStack);
        if (holder.allowModification(itemStack, this.minecraft.player)) {
            Slot slot = this.screen.getHoveredSlot(event.x(), event.y());
            if (slot != null) {
                ClickActionScheme scheme = this.getControlScheme();
                this.clickAction = this.getClickActionFromScheme(scheme,
                        event.button(),
                        slot,
                        holder,
                        itemStack,
                        this.minecraft.player);
                return this.clickAction != null;
            }
        }

        return false;
    }

    /**
     * Item storage interactions are not supported for nested items as the server does not know which item tooltip is
     * currently showing other than the item in the hovered slot or held by the cursor.
     */
    private boolean blockAllMouseActions(OptionalInt slotIndex) {
        return !this.nestedStorageItem.isEmpty() && Objects.equals(slotIndex, this.nestedStorageItemSlot) && (
                slotIndex.isPresent()
                        || ItemInteractions.CONFIG.get(ClientConfig.class).itemHeldByCursorTooltip.isUsed());
    }

    private void clearDraggingSlots() {
        this.clickAction = null;
        this.clickedDraggingSlots.clear();
        this.allDraggingSlots.clear();
    }

    private @Nullable ClickAction getClickActionFromScheme(ClickActionScheme scheme, int buttonNum, Slot slot, ItemStorageHolder holder, ItemStack itemStack, Player player) {
        return switch (scheme) {
            case SPLIT_INPUT -> {
                yield this.getClickActionFromButtonNum(buttonNum);
            }
            case SINGLE_INPUT -> {
                yield buttonNum == InputConstants.MOUSE_BUTTON_RIGHT ?
                        this.getClickActionFromSlot(slot, holder, itemStack, player) : null;
            }
        };
    }

    private ClickActionScheme getControlScheme() {
        if (ItemInteractions.CONFIG.get(ClientConfig.class).extractSingleItemOnly()) {
            return ClickActionScheme.SPLIT_INPUT;
        } else {
            return ItemInteractions.CONFIG.get(ClientConfig.class).controlScheme;
        }
    }

    private @Nullable ClickAction getClickActionFromButtonNum(int buttonNum) {
        return switch (buttonNum) {
            case InputConstants.MOUSE_BUTTON_LEFT -> ClickAction.PRIMARY;
            case InputConstants.MOUSE_BUTTON_RIGHT -> ClickAction.SECONDARY;
            default -> null;
        };
    }

    private @Nullable ClickAction getClickActionFromSlot(Slot slot, ItemStorageHolder holder, ItemStack itemStack, Player player) {
        if (this.matchesSecondaryClickAction(slot, holder, itemStack, player)) {
            return ClickAction.SECONDARY;
        } else if (this.matchesPrimaryClickAction(slot, holder, itemStack, player)) {
            return ClickAction.PRIMARY;
        } else {
            return null;
        }
    }

    private boolean matchesPrimaryClickAction(Slot slot, ItemStorageHolder holder, ItemStack itemStack, Player player) {
        return slot.hasItem() && holder.canAddItem(itemStack, slot.getItem(), player);
    }

    private boolean matchesSecondaryClickAction(Slot slot, ItemStorageHolder holder, ItemStack itemStack, Player player) {
        return !slot.hasItem() && !holder.getItemContainer(itemStack, player).isEmpty();
    }

    @Override
    public boolean onMouseReleased(MouseButtonEvent event, OptionalInt slotIndex, ItemStack itemStack) {
        if (this.blockAllMouseActions(slotIndex)) {
            return true;
        }

        if (!ItemInteractions.CONFIG.get(ServerConfig.class).enableMouseDragging || slotIndex.isPresent()) {
            return false;
        }

        ClickAction lastClickAction = this.getLastClickAction();
        boolean handleMouseRelease = this.isMouseReleaseHandled();
        this.clearDraggingSlots();
        if (lastClickAction != null) {
            // Play this manually at the end as we suppress all interaction sounds played while dragging.
            ItemStorageHolder holder = ItemStorageHolder.ofItem(itemStack);
            switch (lastClickAction) {
                case PRIMARY -> {
                    holder.storage().playInsertSound(this.minecraft.player);
                }
                case SECONDARY -> {
                    holder.storage().playRemoveOneSound(this.minecraft.player);
                }
            }
        }

        return handleMouseRelease;
    }

    private @Nullable ClickAction getLastClickAction() {
        return !this.clickedDraggingSlots.isEmpty() ? this.clickAction : null;
    }

    private boolean isMouseReleaseHandled() {
        return this.allDraggingSlots.size() > 1 || !this.clickedDraggingSlots.isEmpty();
    }

    @Override
    public boolean onMouseDragged(MouseButtonEvent event, double dragX, double dragY, OptionalInt slotIndex, ItemStack itemStack) {
        if (this.blockAllMouseActions(slotIndex)) {
            return true;
        }

        if (!ItemInteractions.CONFIG.get(ServerConfig.class).enableMouseDragging || slotIndex.isPresent()) {
            return false;
        }

        if (this.clickAction != null) {
            ItemStorageHolder holder = ItemStorageHolder.ofItem(itemStack);
            if (!holder.allowModification(itemStack, this.minecraft.player)) {
                this.clearDraggingSlots();
                return false;
            }

            Slot slot = this.screen.getHoveredSlot(event.x(), event.y());
            if (slot != null && this.screen.getMenu().canDragTo(slot) && !this.allDraggingSlots.contains(slot)) {
                if (this.shouldSlotBeClicked(this.clickAction, slot, holder, itemStack, this.minecraft.player)) {
                    this.clickedDraggingSlots.add(slot);
                    this.screen.slotClicked(slot, slot.index, event.button(), ContainerInput.PICKUP);
                }

                this.allDraggingSlots.add(slot);
                return true;
            }
        }

        return false;
    }

    private boolean shouldSlotBeClicked(ClickAction clickAction, Slot slot, ItemStorageHolder holder, ItemStack itemStack, Player player) {
        return switch (clickAction) {
            case PRIMARY -> {
                yield this.matchesPrimaryClickAction(slot, holder, itemStack, player);
            }
            case SECONDARY -> {
                yield this.matchesSecondaryClickAction(slot, holder, itemStack, player)
                        || ItemInteractions.CONFIG.get(ClientConfig.class).extractSingleItemOnly() && holder.hasAnyOf(
                        itemStack,
                        slot.getItem(),
                        player,
                        true);
            }
        };
    }

    @Override
    public boolean isDragging() {
        return this.clickAction != null;
    }

    @Override
    public boolean onMouseScrolled(double scrollX, double scrollY, int slotIndex, ItemStack itemStack) {
        return CustomItemSlotMouseAction.super.onMouseScrolled(scrollX, scrollY, slotIndex, itemStack);
    }

    @Override
    public boolean onMouseScrolled(double scrollX, double scrollY, OptionalInt slotIndex, ItemStack itemStack) {
        if (!ItemInteractions.CONFIG.get(ClientConfig.class).itemStorageTooltip.isUsed()) {
            return false;
        }

        Slot slot = this.screen.hoveredSlot;
        if (slot != null && ItemInteractions.CONFIG.get(ClientConfig.class).extractSingleItemOnly()) {
            if (this.allowModification(ItemStorageHolder.ofItem(itemStack), slot, slotIndex, itemStack)) {
                int wheel = this.onMouseScroll(scrollX, scrollY);
                if (wheel != 0) {
                    int buttonNum = this.getMouseButtonFromWheel(wheel);
                    this.screen.slotClicked(slot, slot.index, buttonNum, ContainerInput.PICKUP);
                }

                return true;
            }
        }

        if (!ItemInteractions.CONFIG.get(ClientConfig.class).itemHeldByCursorTooltip.isUsed() && slotIndex.isEmpty()) {
            return false;
        }

        ItemStack storageItem = this.getStorageItem(slotIndex, itemStack);
        ItemStorageHolder holder = ItemStorageHolder.ofItem(storageItem);
        if (this.allowModification(holder, slot, slotIndex, storageItem)) {
            int wheel = this.onMouseScroll(scrollX, scrollY);
            if (wheel != 0) {
                Vector2ic scrollXY;
                if (ItemInteractions.CONFIG.get(ClientConfig.class).verticalTooltipScrolling.isUsed()) {
                    scrollXY = new Vector2i(0, wheel);
                } else {
                    scrollXY = new Vector2i(-wheel, 0);
                }

                this.scrollSelectedItem(holder, slotIndex, storageItem, scrollXY);
            }

            return true;
        }

        return false;
    }

    private boolean allowModification(ItemStorageHolder holder, ItemStack itemStack) {
        return this.allowModification(holder, null, OptionalInt.empty(), itemStack);
    }

    /**
     * Don't use {@link net.minecraft.world.inventory.AbstractContainerMenu#getSlot(int)} for getting the slot for the
     * index, it does not work in the creative inventory menu.
     */
    private boolean allowModification(ItemStorageHolder holder, @Nullable Slot slot, OptionalInt slotIndex, ItemStack itemStack) {
        if (holder.allowModification(itemStack, this.minecraft.player) && holder.hasContents(itemStack,
                this.minecraft.player)) {
            if (slotIndex.isEmpty() || !this.nestedStorageItem.isEmpty()) {
                return true;
            } else {
                return slot != null && slot.index == slotIndex.getAsInt()
                        && ItemDecorationsHelper.allowSlotModification(slot, itemStack, this.minecraft.player);
            }
        } else {
            return false;
        }
    }

    /**
     * @see net.minecraft.client.MouseHandler#onScroll(long, double, double)
     */
    private int onMouseScroll(double scrollX, double scrollY) {
        Vector2i wheelXY = this.scrollWheelHandler.onMouseScroll(scrollX, scrollY);
        return wheelXY.y == 0 ? -wheelXY.x : wheelXY.y;
    }

    private int getMouseButtonFromWheel(int wheel) {
        if (ItemInteractions.CONFIG.get(ClientConfig.class).reverseSingleItemScrolling ? wheel < 0 : wheel > 0) {
            return InputConstants.MOUSE_BUTTON_RIGHT;
        } else {
            return InputConstants.MOUSE_BUTTON_LEFT;
        }
    }

    private void scrollSelectedItem(ItemStorageHolder holder, OptionalInt slotIndex, ItemStack itemStack, Vector2ic scrollXY) {
        Container container = holder.getItemContainer(itemStack, this.minecraft.player);
        int updatedSelectedItem = holder.storage()
                .scrollSelectedItem(itemStack, this.minecraft.player, container, scrollXY);
        int previousSelectedItem = holder.storage().getSelectedItem(itemStack, this.minecraft.player);
        if (previousSelectedItem != updatedSelectedItem) {
            this.toggleSelectedItem(itemStack, slotIndex, updatedSelectedItem, false);
        }
    }

    @Override
    public boolean onKeyPressed(KeyEvent event, OptionalInt slotIndex, ItemStack itemStack) {
        if (!ItemInteractions.CONFIG.get(ClientConfig.class).itemStorageTooltip.isUsed()) {
            return false;
        }

        if (!ItemInteractions.CONFIG.get(ClientConfig.class).itemHeldByCursorTooltip.isUsed() && slotIndex.isEmpty()) {
            return false;
        }

        ItemStack storageItem = this.getStorageItem(slotIndex, itemStack);
        ItemStorageHolder storageHolder = ItemStorageHolder.ofItem(storageItem);
        if (!this.allowModification(storageHolder, this.screen.hoveredSlot, slotIndex, storageItem)) {
            return false;
        }

        if (ItemInteractions.CONFIG.get(ClientConfig.class).nestedItemTooltips) {
            if (event.isSelection()) {
                if (this.pushNestedStorageItem(storageHolder, slotIndex, storageItem)) {
                    this.playButtonClickSound(SoundEvents.ITEM_FRAME_ADD_ITEM);
                } else {
                    this.playButtonClickSound(SoundEvents.BUNDLE_INSERT_FAIL);
                }

                return true;
            } else if (event.key() == InputConstants.KEY_BACKSPACE) {
                if (this.popNestedStorageItem(slotIndex, itemStack)) {
                    this.playButtonClickSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM);
                } else {
                    this.playButtonClickSound(SoundEvents.BUNDLE_INSERT_FAIL);
                }

                return true;
            }
        }

        int scrollX = 0;
        int scrollY = 0;
        if (event.isLeft()) {
            scrollX--;
        }

        if (event.isRight()) {
            scrollX++;
        }

        if (event.isUp()) {
            scrollY--;
        }

        if (event.isDown()) {
            scrollY++;
        }

        if (scrollX != 0 || scrollY != 0) {
            this.scrollSelectedItem(storageHolder, slotIndex, storageItem, new Vector2i(scrollX, scrollY));
            return true;
        }

        return false;
    }

    private ItemStack getStorageItem(OptionalInt slotIndex, ItemStack originalItem) {
        return this.nestedStorageItem.isEmpty() || !Objects.equals(slotIndex, this.nestedStorageItemSlot) ?
                originalItem : this.nestedStorageItem.peek().item();
    }

    private void playButtonClickSound(SoundEvent sound) {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F));
    }

    private boolean pushNestedStorageItem(ItemStorageHolder holder, OptionalInt slotIndex, ItemStack itemStack) {
        Container container = holder.getItemContainer(itemStack, this.minecraft.player);
        int selectedItem = holder.storage().getSelectedItem(itemStack, this.minecraft.player);
        ItemStack item = container.getItem(selectedItem);
        ItemStorageHolder itemHolder = ItemStorageHolder.ofItem(item);
        if (this.allowModification(itemHolder, item)) {
            if (!Objects.equals(slotIndex, this.nestedStorageItemSlot)) {
                this.nestedStorageItem.clear();
                this.nestedStorageItemSlot = slotIndex;
            }

            itemHolder.storage()
                    .toggleSelectedItem(itemStack, this.minecraft.player, SelectedItem.DEFAULT_SELECTED_ITEM, false);
            this.nestedStorageItem.push(new ItemSlot(item.copy(), selectedItem));
            return true;
        }

        return false;
    }

    private boolean popNestedStorageItem(OptionalInt slotIndex, ItemStack originalItem) {
        if (!this.nestedStorageItem.isEmpty()) {
            int slotNum = this.nestedStorageItem.pop().slotNum();
            ItemStack item = this.getStorageItem(slotIndex, originalItem);
            ItemStorageHolder itemHolder = ItemStorageHolder.ofItem(item);
            itemHolder.storage().toggleSelectedItem(item, this.minecraft.player, slotNum, false);
            if (this.nestedStorageItem.isEmpty()) {
                this.nestedStorageItemSlot = OptionalInt.of(-1);
            }

            return true;
        }

        return false;
    }

    @Override
    public void onStopHovering(Slot slot) {
        if (!ItemInteractions.CONFIG.get(ClientConfig.class).itemHeldByCursorTooltip.isUsed()) {
            this.clearNestedStorageItem(slot.getItem());
            super.onStopHovering(slot);
        }
    }

    /**
     * @see BundleMouseActions#onSlotClicked(Slot, ContainerInput)
     */
    @Override
    public void onSlotClicked(Slot slot, ContainerInput containerInput) {
        this.clearNestedStorageItem(slot.getItem());
        if (containerInput == ContainerInput.QUICK_MOVE || containerInput == ContainerInput.SWAP) {
            this.toggleSelectedItem(slot.getItem(),
                    OptionalInt.of(slot.index),
                    SelectedItem.DEFAULT_SELECTED_ITEM,
                    true);
        }
    }

    private void clearNestedStorageItem(ItemStack itemStack) {
        if (!this.nestedStorageItem.isEmpty()) {
            // Attempt resetting the selected item on the client so it's inline with the server again to avoid desyncing.
            int slotNum = this.nestedStorageItem.getFirst().slotNum();
            ItemStorageHolder.ofItem(itemStack)
                    .storage()
                    .toggleSelectedItem(itemStack, this.minecraft.player, slotNum, false);
            this.nestedStorageItem.clear();
        }

        this.nestedStorageItemSlot = OptionalInt.of(-1);
    }

    @Override
    public void toggleSelectedBundleItem(ItemStack bundleItem, int slotIndex, int updatedSelectedItem) {
        this.toggleSelectedItem(bundleItem, OptionalInt.of(slotIndex), updatedSelectedItem, false);
    }

    private void toggleSelectedItem(ItemStack bundleItem, OptionalInt slotIndex, int updatedSelectedItem, boolean slotClicked) {
        ItemStorageHolder.ofItem(bundleItem)
                .storage()
                .toggleSelectedItem(bundleItem, this.minecraft.player, updatedSelectedItem, slotClicked);
        // Item storage interactions are not supported for nested items as the server does not know which item tooltip is currently showing other than the item in the hovered slot or held by the cursor.
        if (this.nestedStorageItem.isEmpty()) {
            MessageSender.broadcast(new ServerboundSelectedItemMessage(slotIndex, updatedSelectedItem, slotClicked));
        }
    }
}
