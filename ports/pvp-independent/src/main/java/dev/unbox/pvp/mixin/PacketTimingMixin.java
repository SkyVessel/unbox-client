package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.ConnectionDiagnostics;
import net.minecraft.network.*;
import net.minecraft.util.IThreadListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(PacketThreadUtil.class)
public class PacketTimingMixin {
    @Inject(method="checkThreadAndEnqueue",at=@At("HEAD"))
    private static void unbox$queueTime(Packet packet,INetHandler handler,IThreadListener listener,CallbackInfo ci){if(listener.isCallingFromMinecraftThread())ConnectionDiagnostics.handled(packet);}
}
