package fuzs.iteminteractions.common.impl.client.gui.screens.inventory.tooltip;

import fuzs.iteminteractions.common.api.v2.client.gui.screens.inventory.tooltip.ClientItemStorageTooltip;
import fuzs.iteminteractions.common.api.v2.client.gui.screens.inventory.tooltip.SelectedItemPreviewProvider;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.client.core.KeyType;
import fuzs.iteminteractions.common.impl.config.ClientConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.function.Function;

public record DynamicClientTooltipComponent(ClientItemStorageTooltip component) implements ClientTooltipComponent, SelectedItemPreviewProvider {
    public static final String REVEAL_CONTENTS_TRANSLATION_KEY = ItemInteractions.id("container")
            .toLanguageKey(Registries.elementsDirPath(Registries.ITEM), "tooltip.reveal_contents");

    public static <T extends TooltipComponent> Function<? super T, DynamicClientTooltipComponent> wrapFactory(Function<? super T, ? extends ClientItemStorageTooltip> factory) {
        return (T tooltipComponent) -> new DynamicClientTooltipComponent(factory.apply(tooltipComponent));
    }

    @Override
    public int getHeight(Font font) {
        KeyType type = ItemInteractions.CONFIG.get(ClientConfig.class).itemStorageTooltip;
        if (!type.isUsed()) {
            return type.getComponent(REVEAL_CONTENTS_TRANSLATION_KEY) != null ? font.lineHeight + 1 : 0;
        } else {
            return this.component.getHeight(font);
        }
    }

    @Override
    public int getWidth(Font font) {
        KeyType type = ItemInteractions.CONFIG.get(ClientConfig.class).itemStorageTooltip;
        if (!type.isUsed()) {
            Component component = type.getComponent(REVEAL_CONTENTS_TRANSLATION_KEY);
            return component != null ? font.width(component) : 0;
        } else {
            return this.component.getWidth(font);
        }
    }

    @Override
    public boolean showTooltipWithItemInHand() {
        if (ItemInteractions.CONFIG.get(ClientConfig.class).itemStorageTooltip.isUsed()) {
            return this.component.showTooltipWithItemInHand();
        } else {
            return false;
        }
    }

    @Override
    public void extractText(GuiGraphicsExtractor guiGraphics, Font font, int x, int y) {
        KeyType type = ItemInteractions.CONFIG.get(ClientConfig.class).itemStorageTooltip;
        if (!type.isUsed()) {
            Component component = type.getComponent(REVEAL_CONTENTS_TRANSLATION_KEY);
            if (component != null) {
                guiGraphics.text(font, component, x, y, -1);
            }
        }
    }

    @Override
    public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor guiGraphics) {
        if (ItemInteractions.CONFIG.get(ClientConfig.class).itemStorageTooltip.isUsed()) {
            this.component.extractImage(font, x, y, width, height, guiGraphics);
        }
    }

    @Override
    public SelectedItemPreviewProvider unwrap() {
        return this.component;
    }
}
