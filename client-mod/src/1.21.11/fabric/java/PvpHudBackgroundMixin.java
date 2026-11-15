package dev.unbox.client.mixin;
import dev.unbox.client.ClientHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
/** Optional upstream HUD. Preserve bars and icons; only the panel and its border are transparent. */
@Pseudo
@Mixin(targets="com.shieldreturn.ShieldCooldownHud",remap=false)
public class PvpHudBackgroundMixin {
 @ModifyArg(method="drawPanel",at=@At(value="INVOKE",target="Lnet/minecraft/class_332;method_25294(IIIII)V",ordinal=0,remap=false),index=4,remap=false)
 private static int unboxBorder(int color){return ClientHud.flag("pvp.shieldreturn.background",false)?color:0;}
 @ModifyArg(method="drawPanel",at=@At(value="INVOKE",target="Lnet/minecraft/class_332;method_25294(IIIII)V",ordinal=1,remap=false),index=4,remap=false)
 private static int unboxBackground(int color){return ClientHud.flag("pvp.shieldreturn.background",false)?color:0;}
}
