package fuzs.iteminteractions.common.api.v2.world.inventory.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;

/**
 * A tooltip component displaying the items of a bundle as well as its weight.
 *
 * @param contents   the bundle contents
 * @param weight     the weight of the bundle relative to its maximum weight
 * @param gridWidth  the number of columns of the grid
 * @param gridHeight the number of rows of the grid
 */
public record BundleContentsTooltip(BundleContents contents,
                                    Fraction weight,
                                    int gridWidth,
                                    int gridHeight) implements TooltipComponent {

}
