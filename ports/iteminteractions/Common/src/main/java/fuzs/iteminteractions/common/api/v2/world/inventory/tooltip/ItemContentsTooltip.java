package fuzs.iteminteractions.common.api.v2.world.inventory.tooltip;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

/**
 * A tooltip component displaying the items of a container in a grid.
 *
 * @param itemList        the items inside the container
 * @param selectedItem    the selected item index
 * @param gridWidth       the number of columns of the grid
 * @param gridHeight      the number of rows of the grid
 * @param backgroundColor the background color of the tooltip
 */
public record ItemContentsTooltip(NonNullList<ItemStack> itemList,
                                  int selectedItem,
                                  int gridWidth,
                                  int gridHeight,
                                  int backgroundColor) implements TooltipComponent {

}
