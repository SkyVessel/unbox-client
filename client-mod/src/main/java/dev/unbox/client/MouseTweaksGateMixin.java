package dev.unbox.client.mixin;

import dev.unbox.client.ClientIntegrations;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Mouse Tweaks rereads its own file on screen changes, inside the input callback. */
@Pseudo
@Mixin(targets="yalter.mousetweaks.Main",remap=false)
public abstract class MouseTweaksGateMixin {
 @Shadow private static Screen openScreen;
 @Inject(method={"onMouseClicked","onMouseReleased","onMouseDrag","onMouseScrolled"},at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$input(CallbackInfoReturnable<Boolean> result){
  if(!ClientIntegrations.active("mousetweaks")){
   // Re-enabling must initialize the handler and clear the old drag/scroll state,
   // even when no different Screen instance has been opened in the meantime.
   openScreen=null;
   result.setReturnValue(false);
  }
 }
 @Inject(method="updateScreen",at=@At("RETURN"),remap=false)
 private static void unbox$afterReload(CallbackInfo ci){ClientIntegrations.refresh("mousetweaks");}
}
