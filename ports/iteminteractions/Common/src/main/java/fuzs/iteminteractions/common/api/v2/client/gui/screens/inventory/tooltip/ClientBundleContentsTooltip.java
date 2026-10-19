package fuzs.iteminteractions.common.api.v2.client.gui.screens.inventory.tooltip;

import fuzs.iteminteractions.common.api.v2.world.inventory.tooltip.BundleContentsTooltip;
import fuzs.iteminteractions.common.impl.client.helper.ItemStorageTooltipHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientBundleTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;

import java.util.List;
import java.util.stream.Stream;

/**
 * A {@link ClientItemStorageTooltip} for displaying the contents and weight of a bundle.
 */
public class ClientBundleContentsTooltip extends ClientBundleTooltip implements ClientItemStorageTooltip {
    private final Fraction weight;
    private final int gridWidth;
    private final int gridHeight;
    private final boolean overflow;
    private final boolean combined;
    private final boolean drawProgressBar;

    /**
     * @param tooltip the tooltip component created by the bundle item storage
     */
    public ClientBundleContentsTooltip(BundleContentsTooltip tooltip) {
        this(tooltip.contents(), tooltip.weight(), tooltip.gridWidth(), tooltip.gridHeight(), false, false, true);
    }

    private ClientBundleContentsTooltip(BundleContents contents, Fraction weight, int gridWidth, int gridHeight, boolean overflow, boolean combined, boolean drawProgressBar) {
        super(contents);
        this.weight = weight;
        this.gridWidth = gridWidth;
        this.gridHeight = gridHeight;
        this.overflow = overflow;
        this.combined = combined;
        this.drawProgressBar = drawProgressBar;
    }

    @Override
    public int getWidth(Font font) {
        return this.contents.isEmpty() ? super.getWidth(font) : this.gridSizeX() * SLOT_SIZE;
    }

    @Override
    public int backgroundHeight() {
        return this.drawProgressBar ? super.backgroundHeight() : this.itemGridHeight();
    }

    @Override
    public int gridSizeX() {
        return this.gridWidth;
    }

    @Override
    public int gridSizeY() {
        return this.gridHeight;
    }

    @Override
    public int maxGridSizeX() {
        return 7;
    }

    @Override
    public int slotCount() {
        return this.contents.size();
    }

    /**
     * @see ClientBundleTooltip#extractImage(Font, int, int, int, int, GuiGraphicsExtractor)
     */
    @Override
    public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics) {
        if (this.contents.isEmpty()) {
            extractEmptyBundleTooltip(font, x, y, w, h, graphics);
        } else {
            this.extractBundleWithItemsTooltip(font, x, y, w, h, graphics, this.weight);
        }
    }

    /**
     * @see ClientBundleTooltip#extractBundleWithItemsTooltip(Font, int, int, int, int, GuiGraphicsExtractor,
     *         Fraction)
     */
    @Override
    public void extractBundleWithItemsTooltip(Font font, int x, int y, int w, int h, GuiGraphicsExtractor graphics, Fraction weight) {
        List<ItemStackTemplate> shownItems = this.getShownItems(this.contents.size());
        int xStartPos = x + this.getCustomContentXOffset(font, w) + this.getWidth(font);
        int yStartPos = y + this.gridSizeY() * SLOT_SIZE;
        int slotNum = 1;
        for (int rowNum = 1; rowNum <= this.gridSizeY(); rowNum++) {
            for (int columnNum = 1; columnNum <= this.gridSizeX(); columnNum++) {
                int drawX = xStartPos - columnNum * SLOT_SIZE;
                int drawY = yStartPos - rowNum * SLOT_SIZE;
                if (shouldRenderItemSlot(shownItems, slotNum)) {
                    this.extractSlot(slotNum, drawX, drawY, shownItems, slotNum, font, graphics);
                    slotNum++;
                }
            }
        }

        this.extractSelectedItemTooltip(font, graphics, x, y, w);
        if (this.drawProgressBar) {
            this.extractCustomProgressbar(x + this.getCustomContentXOffset(font, w),
                    y + this.itemGridHeight() + 4,
                    font,
                    graphics,
                    weight);
        }
    }

    /**
     * @see ClientBundleTooltip#extractSlot(int, int, int, List, int, Font, GuiGraphicsExtractor)
     */
    @Override
    public void extractSlot(int slotNum, int drawX, int drawY, List<ItemStackTemplate> shownItems, int seed, Font font, GuiGraphicsExtractor graphics) {
        boolean hasOverflowSprite = this.overflow && slotNum == 1;
        int itemVisualOrderIndex = shownItems.size() - slotNum;
        boolean hasHighlight = !hasOverflowSprite && itemVisualOrderIndex == this.contents.getSelectedItemIndex();
        if (hasHighlight) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_BACK_SPRITE, drawX, drawY, 24, 24);
        } else {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_BACKGROUND_SPRITE, drawX, drawY, 24, 24);
        }

        if (hasOverflowSprite) {
            graphics.text(font, "...", drawX + 6, drawY + 8, 0xFFFFFFFF, false);
        } else {
            ItemStack item = shownItems.get(itemVisualOrderIndex).create();
            graphics.item(item, drawX + 4, drawY + 4, seed);
            if (this.combined) {
                ItemStorageTooltipHelper.itemDecorations(graphics, font, item, drawX + 4, drawY + 4);
            } else {
                graphics.itemDecorations(font, item, drawX + 4, drawY + 4);
            }
        }

        if (hasHighlight) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_HIGHLIGHT_FRONT_SPRITE, drawX, drawY, 24, 24);
        }
    }

    @Override
    public void extractSelectedItemTooltip(Font font, GuiGraphicsExtractor graphics, int x, int y, int w) {
        ClientItemStorageTooltip.super.extractSelectedItemTooltip(font, graphics, x, y, w);
    }

    @Override
    public ItemStack getSelectedItem() {
        ItemStackTemplate selectedItem = this.contents.getSelectedItem();
        return selectedItem != null ? selectedItem.create() : ItemStack.EMPTY;
    }

    @Override
    public Stream<ItemStack> getAllItems() {
        return this.contents.itemCopyStream();
    }

    @Override
    public ClientTooltipComponent createItemPreview(NonNullList<ItemStack> previewItems, int gridWidth, int gridHeight, boolean overflow, boolean combined) {
        return new ClientBundleContentsTooltip(new BundleContents(previewItems.stream()
                .map(ItemStackTemplate::fromNonEmptyStack)
                .toList()), Fraction.ZERO, gridWidth, gridHeight, overflow, combined, false);
    }

    /**
     * Get the x offset required for horizontally centering the item grid.
     *
     * @param font         the font to render with
     * @param tooltipWidth the width of the tooltip
     * @return the content x offset
     *
     * @see ClientBundleTooltip#getContentXOffset(int)
     */
    public int getCustomContentXOffset(Font font, int tooltipWidth) {
        return (tooltipWidth - this.getWidth(font)) / 2;
    }

    /**
     * Extract the progress bar showing the bundle weight.
     *
     * @param x        the x position of the progress bar
     * @param y        the y position of the progress bar
     * @param font     the font to render with
     * @param graphics the graphics helper
     * @param weight   the weight of the bundle relative to its maximum weight
     *
     * @see ClientBundleTooltip#extractProgressbar(int, int, Font, GuiGraphicsExtractor, Fraction)
     */
    public void extractCustomProgressbar(int x, int y, Font font, GuiGraphicsExtractor graphics, Fraction weight) {
        int progressBarWidth = this.getWidth(font);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                getProgressBarTexture(weight),
                x + PROGRESSBAR_BORDER,
                y,
                this.getCustomProgressBarFill(weight, progressBarWidth),
                PROGRESSBAR_HEIGHT);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                PROGRESSBAR_BORDER_SPRITE,
                x,
                y,
                progressBarWidth,
                PROGRESSBAR_HEIGHT);
        Component progressBarFillText = getCustomProgressBarFillText(weight);
        graphics.centeredText(font, progressBarFillText, x + progressBarWidth / 2, y + 3, -1);
    }

    /**
     * @param weight           the weight of the bundle relative to its maximum weight
     * @param progressBarWidth the width of the progress bar
     * @return the width of the progress bar fill
     *
     * @see ClientBundleTooltip#getProgressBarFill(Fraction)
     */
    public int getCustomProgressBarFill(Fraction weight, int progressBarWidth) {
        int progressBarFill = progressBarWidth - PROGRESSBAR_BORDER * 2;
        return Mth.clamp(Mth.mulAndTruncate(weight, progressBarFill), 0, progressBarFill);
    }

    /**
     * @param weight the weight of the bundle relative to its maximum weight
     * @return the progress bar text
     *
     * @see ClientBundleTooltip#getProgressBarFillText(Fraction)
     */
    public static Component getCustomProgressBarFillText(Fraction weight) {
        Component progressBarFillText = ClientBundleTooltip.getProgressBarFillText(weight);
        if (progressBarFillText != null) {
            return progressBarFillText;
        } else {
            return Component.translatable("loading.progress", (int) (weight.doubleValue() * 100.0));
        }
    }
}
