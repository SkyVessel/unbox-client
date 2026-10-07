package dev.unbox.client.mixin;
import dev.unbox.client.UnboxClient;
import dev.unbox.client.OptifineCape;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyboardInput.class)
public class SprintMixin {
 @Inject(method="tick",at=@At("RETURN"))
 private void unboxSprint(CallbackInfo ci){if(!UnboxClient.enabled("sprint")||Minecraft.getInstance().screen!=null)return;var self=(ClientInput)(Object)this;var i=self.keyPresses;self.keyPresses=new Input(i.forward(),i.backward(),i.left(),i.right(),i.jump(),i.shift(),true);}
}
