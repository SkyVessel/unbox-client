package dev.unbox.client.mixin;
import dev.unbox.client.UnboxClient;
import freelook.freelook.FreeLookMod;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=FreeLookMod.class,remap=false)
public abstract class FreelookMixin {
 @Shadow private void stopFreeLooking(Minecraft client){throw new AssertionError();}
 @Inject(method="startFreeLooking",at=@At("HEAD"),cancellable=true)
 private void unboxStart(Minecraft client,CallbackInfo ci){if(!UnboxClient.enabled("freelook"))ci.cancel();}
 @Inject(method="onTickEnd",at=@At("HEAD"))
 private void unboxStop(Minecraft client,CallbackInfo ci){if(!UnboxClient.enabled("freelook")&&FreeLookMod.isFreeLooking)stopFreeLooking(client);}
}
