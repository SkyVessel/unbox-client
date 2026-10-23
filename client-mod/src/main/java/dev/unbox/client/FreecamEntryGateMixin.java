package dev.unbox.client.mixin;

import dev.unbox.client.Freelook;
import dev.unbox.client.FreecamController;
import dev.unbox.client.UnboxClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cover both regular and tripod entry; exiting a running camera must always remain possible. */
@Pseudo @Mixin(targets="net.xolt.freecam.Freecam", remap=false)
public abstract class FreecamEntryGateMixin {
    @Inject(method="postTick",at=@At("HEAD"),cancellable=true,remap=false)
    private static void unbox$beforeKeys(Minecraft mc,CallbackInfo ci) {
        if (UnboxClient.enabled("freecam") && mc.screen==null) return;
        if (!UnboxClient.enabled("freecam")) FreecamController.stop();
        // Drain only Freecam's own queued clicks. In particular, never consume hotbar
        // keys via its hold-to-place-tripod handler while this module is disabled.
        for (var key:mc.options.keyMappings) if (key.getName().startsWith("key.freecam."))
            while (key.consumeClick()) {}
        ci.cancel();
    }
    @Inject(method={"toggle", "toggleTripod"}, at=@At("HEAD"), cancellable=true, remap=false)
    private static void unbox$beforeActivation(CallbackInfo ci) {
        if (FreecamController.active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!UnboxClient.enabled("freecam") || mc.player == null || mc.screen != null) { ci.cancel(); return; }
        FreecamController.prepareActivation();
        if (Freelook.active()) Freelook.suspend();
    }
}
