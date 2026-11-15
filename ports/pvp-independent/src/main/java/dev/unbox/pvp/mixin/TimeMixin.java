package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** PolyTime visual-time adaptation; server WorldInfo is deliberately never changed. */
@Mixin(World.class)
public class TimeMixin {
 @Shadow @Final public boolean isRemote;
 @Inject(method="getWorldTime",at=@At("HEAD"),cancellable=true) private void time(CallbackInfoReturnable<Long> cir){if(isRemote&&PvpSettings.on("time"))cir.setReturnValue((long)PvpSettings.number("time.ticks",6000,0,23999));}
 // Vanilla sky, fog and light calculations bypass World.getWorldTime and read WorldInfo.
 @Redirect(method={"getCelestialAngle","getMoonPhase","getCurrentMoonPhaseFactor"},at=@At(value="INVOKE",target="Lnet/minecraft/world/storage/WorldInfo;getWorldTime()J"))
 private long skyTime(WorldInfo info){return isRemote&&PvpSettings.on("time")?PvpSettings.number("time.ticks",6000,0,23999):info.getWorldTime();}
}
