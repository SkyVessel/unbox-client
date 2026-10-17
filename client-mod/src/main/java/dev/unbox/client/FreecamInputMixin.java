package dev.unbox.client.mixin;

import dev.unbox.client.FreecamController;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class FreecamInputMixin extends ClientInput {
    @Inject(method="tick", at=@At("RETURN"))
    private void unbox$holdBody(CallbackInfo ci) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (!FreecamController.active() || player == null || player.input != (Object)this) return;
        keyPresses = new Input(false, false, false, false, false, false, false);
        moveVector = Vec2.ZERO;
    }
}
