package fuzs.puzzleslib.neoforge.mixin;
import fuzs.puzzleslib.neoforge.impl.event.NativeServerResourcesEvent;
import net.minecraft.server.*;
import net.minecraft.core.*;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Local;
@Mixin(ReloadableServerResources.class)
abstract class ReloadableServerResourcesNeoForgeMixin {
 @Unique private RegistryAccess puzzleslib$registries;
 @Inject(method="<init>",at=@At("RETURN"))
 private void capture(CallbackInfo ci,@Local(argsOnly=true) LayeredRegistryAccess<RegistryLayer> registries){puzzleslib$registries=registries.compositeAccess();}
 @Inject(method="updateComponentsAndStaticRegistryTags",at=@At("TAIL"))
 private void loaded(CallbackInfo ci){NeoForge.EVENT_BUS.post(new NativeServerResourcesEvent((ReloadableServerResources)(Object)this,puzzleslib$registries));}
}
