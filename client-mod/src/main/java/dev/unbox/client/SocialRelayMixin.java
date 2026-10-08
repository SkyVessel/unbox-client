package dev.unbox.client.mixin;
import dev.unbox.client.SocialBridge;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo @Mixin(targets="link.e4mc.Mirror",remap=false)
public class SocialRelayMixin {
 @Inject(method="copyToClipboard",at=@At("HEAD"),remap=false)
 private static void unboxDomain(String value,CallbackInfoReturnable<ClickEvent> ci){SocialBridge.relayDomain(value);}
}
