package fuzs.iteminteractions.common.impl.data.client;

import fuzs.iteminteractions.common.api.v2.client.gui.screens.inventory.tooltip.SelectedItemPreviewProvider;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.client.core.KeyType;
import fuzs.iteminteractions.common.impl.client.gui.screens.inventory.tooltip.DynamicClientTooltipComponent;
import fuzs.iteminteractions.common.impl.config.ItemHeldByCursorTooltip;
import fuzs.iteminteractions.common.impl.config.ItemStorageTooltip;
import fuzs.puzzleslib.common.api.client.data.v2.AbstractLanguageProvider;
import fuzs.puzzleslib.common.api.data.v2.core.DataProviderContext;

public class ModLanguageProvider extends AbstractLanguageProvider {

    public ModLanguageProvider(DataProviderContext context) {
        super(context);
    }

    @Override
    public void addTranslations(TranslationBuilder builder) {
        builder.add(DynamicClientTooltipComponent.REVEAL_CONTENTS_TRANSLATION_KEY, "%s %s to reveal contents");
        builder.add(SelectedItemPreviewProvider.ITEM_COUNT_KEY, "%s (%s)");
        builder.add(KeyType.HOLD_COMPONENT, "Hold");
        builder.add(KeyType.TOGGLE_COMPONENT, "Toggle");
        builder.add(KeyType.SHIFT_COMPONENT, "Shift");
        builder.add(KeyType.CONTROL_COMPONENT, "Control");
        builder.add(KeyType.COMMAND_COMPONENT, "Command");
        builder.add(KeyType.ALT_COMPONENT, "Alt");
        builder.add(ItemStorageTooltip.KEY_MAPPING, "Toggle Item Storage Tooltip");
        builder.add(ItemHeldByCursorTooltip.KEY_MAPPING, "Toggle Item Held By Cursor Tooltip");
        builder.addKeyCategory(ItemInteractions.MOD_ID, ItemInteractions.MOD_NAME);
    }
}
