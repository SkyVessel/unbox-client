package io.github.a5b84.statuseffectbars.mixin;

import static io.github.a5b84.statuseffectbars.StatusEffectBars.config;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.a5b84.statuseffectbars.StatusEffectBarRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge adds effect-aware text rendering; the background call stays common. */
@Mixin(EffectsInInventory.class)
public abstract class EffectsInInventoryMixin {
  @WrapOperation(method = "extractEffects", at = @At(value = "INVOKE", target =
      "Lnet/minecraft/client/gui/screens/inventory/EffectsInInventory;extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/Component;IIZI)I"))
  private int drawBar(EffectsInInventory owner, GuiGraphicsExtractor graphics, Font font,
      Component name, Component duration, int x, int y, boolean ambient, int maxWidth,
      Operation<Integer> original, @Local(name = "effect") MobEffectInstance effect) {
    int width = original.call(owner, graphics, font, name, duration, x, y, ambient, maxWidth);
    StatusEffectBarRenderer.render(graphics, null, effect, x, y, width,
        EffectsInInventory.SPRITE_SQUARE_SIZE, config.inventoryLayout);
    return width;
  }
}
