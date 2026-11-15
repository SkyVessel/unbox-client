package dev.unbox.client.mixin;
import dev.unbox.client.UnboxClient;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Local visual time only; never writes world data or sends a time command. */
@Mixin(Level.class)
public class DaytimeMixin {
 @Inject(method="getDayTime",at=@At("RETURN"),cancellable=true)
 private void unboxDaytime(CallbackInfoReturnable<Long> ci){if(((Level)(Object)this).isClientSide()&&UnboxClient.enabled("daytime"))ci.setReturnValue(Math.floorDiv(ci.getReturnValue(),24000L)*24000+UnboxClient.number("daytime.ticks",6000,0,23999));}
}
