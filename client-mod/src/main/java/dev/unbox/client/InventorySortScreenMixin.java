package dev.unbox.client.mixin;

import dev.unbox.client.InventorySorter;
import dev.unbox.client.UnboxClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventorySortScreenMixin extends AbstractRecipeBookScreen<InventoryMenu> {
    private InventorySortScreenMixin(InventoryMenu menu, RecipeBookComponent<?> recipes, Inventory inventory, Component title) {
        super(menu, recipes, inventory, title);
    }

    @Inject(method="init", at=@At("TAIL"))
    private void unbox$sortButton(CallbackInfo ci) {
        if (!UnboxClient.enabled("inventory")) return;
        addRenderableWidget(Button.builder(Component.literal("Sort"), button -> InventorySorter.sort())
            .bounds(leftPos + imageWidth + 4, topPos, 50, 20).build());
    }
}
