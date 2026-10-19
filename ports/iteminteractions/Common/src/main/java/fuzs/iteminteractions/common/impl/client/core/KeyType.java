package fuzs.iteminteractions.common.impl.client.core;

import fuzs.iteminteractions.common.impl.ItemInteractions;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public interface KeyType {
    Component HOLD_COMPONENT = createDescriptionComponent("hold");
    Component TOGGLE_COMPONENT = createDescriptionComponent("toggle");
    Component SHIFT_COMPONENT = createDescriptionComponent("shift");
    Component CONTROL_COMPONENT = createDescriptionComponent("control");
    Component COMMAND_COMPONENT = createDescriptionComponent("command");
    Component ALT_COMPONENT = createDescriptionComponent("alt");

    boolean isUsed();

    default @Nullable Component getComponent(String translationId) {
        return Component.translatable(translationId,
                this.getUsageComponent(),
                this.getNameComponent().copy().withStyle(ChatFormatting.LIGHT_PURPLE)).withStyle(ChatFormatting.GRAY);
    }

    default Component getUsageComponent() {
        return HOLD_COMPONENT;
    }

    Component getNameComponent();

    static Component createDescriptionComponent(String serializedName) {
        return Component.translatable(ItemInteractions.id("container")
                .toLanguageKey(Registries.elementsDirPath(Registries.ITEM), "tooltip." + serializedName));
    }
}
