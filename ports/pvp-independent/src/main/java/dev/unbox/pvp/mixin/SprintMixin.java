package dev.unbox.pvp.mixin;

import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Apply auto sprint at vanilla's input decision, before movement state is sent. */
@Mixin(EntityPlayerSP.class)
public abstract class SprintMixin {
    @Shadow protected int sprintToggleTimer;

    @Redirect(method="onLivingUpdate",at=@At(value="FIELD",target="Lnet/minecraft/client/entity/EntityPlayerSP;sprintToggleTimer:I",opcode=Opcodes.GETFIELD))
    private int unbox$disableDoubleTap(EntityPlayerSP player){
        if(PvpSettings.on("sprint")){sprintToggleTimer=0;return 0;}
        return sprintToggleTimer;
    }

    @Redirect(method="onLivingUpdate",at=@At(value="INVOKE",target="Lnet/minecraft/client/settings/KeyBinding;isKeyDown()Z"))
    private boolean unbox$automaticSprint(KeyBinding binding){
        Minecraft mc=Minecraft.getMinecraft();
        if(binding==mc.gameSettings.keyBindSprint&&PvpSettings.on("sprint"))
            return mc.currentScreen==null&&mc.inGameHasFocus;
        return binding.isKeyDown();
    }
}
