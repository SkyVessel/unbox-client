package dev.unbox.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.unbox.client.UnboxClient;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.20 stores only one binding per key; C is also vanilla's save-hotbar modifier. */
@Mixin(KeyMapping.class)
public abstract class LegacyZoomKeyMixin {
 @Inject(method="set",at=@At("TAIL"))
 private static void unboxZoomKey(InputConstants.Key key,boolean down,CallbackInfo ci){
  var zoom=UnboxClient.zoomKey;
  if(zoom!=null&&!zoom.isUnbound()&&zoom.saveString().equals(key.getName()))zoom.setDown(down);
 }
}
