package dev.unbox.client.mixin;
import dev.unbox.client.LoginTransport;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.login.ClientboundCustomQueryPacket;
import net.minecraft.network.protocol.login.custom.CustomQueryPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientboundCustomQueryPacket.class)
public class NeoLoginQueryMixin {
 @Inject(method="readPayload",at=@At("HEAD"),cancellable=true)
 private static void unboxDecode(Identifier id,FriendlyByteBuf b,CallbackInfoReturnable<CustomQueryPayload> ci){if(LoginTransport.ours(id))ci.setReturnValue(new LoginTransport.Query(id,LoginTransport.read(b)));}
}
