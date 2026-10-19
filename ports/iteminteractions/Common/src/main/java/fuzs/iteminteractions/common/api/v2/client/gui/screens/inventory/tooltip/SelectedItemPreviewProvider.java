package fuzs.iteminteractions.common.api.v2.client.gui.screens.inventory.tooltip;

import fuzs.iteminteractions.common.api.v2.world.item.storage.ItemStorageHolder;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.config.ClientConfig;
import fuzs.puzzleslib.common.api.client.gui.v2.tooltip.TooltipRenderHelper;
import fuzs.puzzleslib.common.api.util.v1.CommonHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * A provider for the tooltip components displayed for a selected item inside an item storage.
 */
public interface SelectedItemPreviewProvider {
    /**
     * The translation key for the item count appended to an item name.
     */
    String ITEM_COUNT_KEY = ItemInteractions.id("container")
            .toLanguageKey(Registries.elementsDirPath(Registries.ITEM), "item_count");
    /**
     * The empty instance.
     */
    SelectedItemPreviewProvider INSTANCE = new SelectedItemPreviewProvider() {
        // NO-OP
    };

    /**
     * Get the provider for the selected item from the tooltip image of the provided item storage.
     *
     * @param holder    the holder for the item storage
     * @param itemStack the selected item
     * @return the provider for the selected item
     */
    static SelectedItemPreviewProvider getSelectedItemTooltipProvider(ItemStorageHolder holder, ItemStack itemStack) {
        TooltipComponent tooltipComponent = getTooltipComponent(holder, itemStack);
        if (tooltipComponent != null) {
            ClientTooltipComponent clientComponent = TooltipRenderHelper.createImageComponent(tooltipComponent);
            if (clientComponent instanceof SelectedItemPreviewProvider tooltipProvider) {
                return tooltipProvider.unwrap();
            }
        }

        return SelectedItemPreviewProvider.INSTANCE;
    }

    private static @Nullable TooltipComponent getTooltipComponent(ItemStorageHolder holder, ItemStack itemStack) {
        return holder.getTooltipImage(itemStack, CommonHelper.getClientPlayer())
                .flatMap(Function.identity())
                .orElse(null);
    }

    /**
     * Append the number of contained items to the item name.
     *
     * @param itemName      the item name
     * @param contentsCount the number of items contained in the selected item
     * @return the item name with the item count appended
     */
    static Component getItemNameWithCount(Component itemName, int contentsCount) {
        if (contentsCount > 0) {
            return Component.translatable(ITEM_COUNT_KEY, itemName, contentsCount).withStyle(ChatFormatting.GRAY);
        } else {
            return itemName;
        }
    }

    /**
     * @return the underlying provider for the selected item
     */
    default SelectedItemPreviewProvider unwrap() {
        return this;
    }

    /**
     * Create the tooltip components for the selected item.
     *
     * @param holder       the holder for the item storage
     * @param selectedItem the selected item
     * @return the tooltip components
     */
    default List<ClientTooltipComponent> createSelectedItemComponents(ItemStorageHolder holder, ItemStack selectedItem) {
        List<ClientTooltipComponent> tooltipComponents = new ArrayList<>();
        Component itemName = this.createItemName(holder, selectedItem);
        tooltipComponents.add(ClientTooltipComponent.create(itemName.getVisualOrderText()));
        return tooltipComponents;
    }

    /**
     * Create the name component for the selected item.
     *
     * @param holder       the holder for the item storage
     * @param selectedItem the selected item
     * @return the item name component
     */
    default Component createItemName(ItemStorageHolder holder, ItemStack selectedItem) {
        Component itemName = selectedItem.getStyledHoverName();
        if (ItemInteractions.CONFIG.get(ClientConfig.class).itemStorageContentsCount) {
            int contentsCount = holder.getContentsCount(selectedItem, CommonHelper.getClientPlayer());
            return getItemNameWithCount(itemName, contentsCount);
        } else {
            return itemName;
        }
    }
}
