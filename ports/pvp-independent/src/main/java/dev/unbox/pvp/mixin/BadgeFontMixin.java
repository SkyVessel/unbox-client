package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.*;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(FontRenderer.class)
public class BadgeFontMixin {
 @Shadow private float posX,posY,alpha;
 @Inject(method="getCharWidth",at=@At("HEAD"),cancellable=true)
 private void unboxWidth(char c,CallbackInfoReturnable<Integer> ci){int id=NameBadges.glyph(c);if(id>=0)ci.setReturnValue(BadgeTextures.width(id));}
 // OptiFine measures strings through its additional float-width method.
 @Inject(method="getCharWidthFloat",at=@At("HEAD"),cancellable=true,remap=false,require=0)
 private void unboxOptifineWidth(char c,CallbackInfoReturnable<Float> ci){int id=NameBadges.glyph(c);if(id>=0)ci.setReturnValue((float)BadgeTextures.width(id));}
 @Inject(method="renderChar",at=@At("HEAD"),cancellable=true)
 private void unboxGlyph(char c,boolean italic,CallbackInfoReturnable<Float> ci){int id=NameBadges.glyph(c);if(id>=0)ci.setReturnValue(BadgeTextures.glyph(id,posX,posY,alpha));}
}
