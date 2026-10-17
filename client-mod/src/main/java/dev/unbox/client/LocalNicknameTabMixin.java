package dev.unbox.client.mixin;

import dev.unbox.client.LocalNickname;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
public abstract class LocalNicknameTabMixin {
    @Inject(method="getNameForDisplay", at=@At("RETURN"), cancellable=true)
    private void unbox$localNickname(PlayerInfo info, CallbackInfoReturnable<Component> ci) {
        ci.setReturnValue(LocalNickname.render(info.getProfile().id(), ci.getReturnValue()));
    }
}
