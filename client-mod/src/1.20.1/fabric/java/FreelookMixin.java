package dev.unbox.client.mixin;
import dev.unbox.client.*;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=freelook.freelook.client.FreelookmodClient.class,remap=false) public class FreelookMixin {
 @Shadow private static CameraType lastPerspective;
 @Inject(method="lambda$onInitializeClient$0",at=@At("HEAD"),cancellable=true)private static void gate(KeyMapping menu,KeyMapping key,freelook.freelook.VariableStorage store,Minecraft mc,CallbackInfo ci){if(!UnboxClient.enabled("freelook")||FreecamController.active()||mc.player==null){if(freelook.freelook.client.FreelookmodClient.isFreeLooking&&lastPerspective!=null)mc.options.setCameraType(lastPerspective);freelook.freelook.client.FreelookmodClient.isFreeLooking=false;ci.cancel();}}
}
