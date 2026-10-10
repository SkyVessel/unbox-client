package dev.unbox.client.mixin;
import dev.unbox.client.LoginTransport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.login.ServerboundCustomQueryAnswerPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryAnswerPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ServerboundCustomQueryAnswerPacket.class)
public class NeoLoginAnswerMixin {
 @Inject(method="readPayload",at=@At("HEAD"),cancellable=true)
 private static void unboxDecode(int id,FriendlyByteBuf b,CallbackInfoReturnable<CustomQueryAnswerPayload> ci){if(LoginTransport.ours(id))ci.setReturnValue(b.readBoolean()?new LoginTransport.Answer(LoginTransport.read(b)):null);}
}
