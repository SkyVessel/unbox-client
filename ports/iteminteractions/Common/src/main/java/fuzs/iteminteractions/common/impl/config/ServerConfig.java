package fuzs.iteminteractions.common.impl.config;

import fuzs.puzzleslib.common.api.config.v3.Config;
import fuzs.puzzleslib.common.api.config.v3.ConfigCore;

public class ServerConfig implements ConfigCore {
    @Config(description = "Support dragging the mouse while holding an item with contents to insert hovered items, or to extract contents to empty hovered slots.")
    public boolean enableMouseDragging = true;
    @Config(description = {
            "Support moving only a single item from item contents instead of the whole item stack from the selected slot when clicking while a modifier key is held.",
            "The scroll wheel can also be used for moving items even more quickly."
    })
    public boolean enableSingleItemMovement = true;
}
