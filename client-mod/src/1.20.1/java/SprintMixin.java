package dev.unbox.client.mixin;
import dev.unbox.client.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(LocalPlayer.class) public class SprintMixin {
 @Redirect(method="aiStep",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;isDown()Z"))private boolean sprint(net.minecraft.client.KeyMapping key){var mc=Minecraft.getInstance();return key.isDown()||(key==mc.options.keySprint&&UnboxClient.enabled("sprint")&&mc.screen==null&&!FreecamController.active());}
}
