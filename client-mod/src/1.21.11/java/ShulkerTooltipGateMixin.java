package dev.unbox.client.mixin;

import dev.unbox.client.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Pseudo @Mixin(targets="fuzs.iteminteractions.impl.client.core.BackedActivationTypeProvider",remap=false)
public interface ShulkerTooltipGateMixin {
    @Inject(method="isActive",at=@At("HEAD"),cancellable=true,remap=false)
    default void unbox$preview(CallbackInfoReturnable<Boolean> result){
        String kind=getClass().getName();
        if(!ClientIntegrations.active("shulker")
            ||kind.contains("VisualItemContents")&&(Platform.loaded("shulkerboxtooltip")||ClientHud.value("shulker.preview","SHIFT").equals("NEVER"))
            ||kind.contains("SelectedItemTooltips")&&!ClientHud.flag("shulker.nested",true))result.setReturnValue(false);
    }

    @Pseudo @Mixin(targets="fuzs.iteminteractions.api.v1.client.tooltip.AbstractClientItemContentsTooltip",remap=false)
    abstract class Counts {
        @Redirect(method="drawSlot",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;renderItemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V",remap=true),remap=false)
        private void unbox$counts(GuiGraphics graphics,Font font,ItemStack stack,int x,int y){
            if(ClientHud.flag("shulker.count",true))graphics.renderItemDecorations(font,stack,x,y);
        }
    }
}
