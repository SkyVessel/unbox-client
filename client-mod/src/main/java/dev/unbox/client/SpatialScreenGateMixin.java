package dev.unbox.client.mixin;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.client.gui.screens.Screen;
@Pseudo @Mixin(targets="org.tastytrash.spatialGUI.client.SpatialGUIClient",remap=false)
public abstract class SpatialScreenGateMixin {
 @Inject(method="isEnabled",at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$enabled(CallbackInfoReturnable<Boolean> result){if(!dev.unbox.client.ClientIntegrations.active("spatial"))result.setReturnValue(false);}
 @Inject(method="shouldHookScreen",at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$ownMenus(Screen screen,CallbackInfoReturnable<Boolean> result){if(screen!=null&&screen.getClass().getName().startsWith("dev.unbox.client."))result.setReturnValue(false);}
}
