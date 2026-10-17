package dev.unbox.client.mixin;

import dev.unbox.client.SurvivalHostMods;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="de.miraculixx.veinminerClient.KeyBindManager",remap=false)
public abstract class VeinMinerClientGateMixin {
    @Unique private boolean unbox$wasActive;
    @Shadow(remap=false) public abstract void onDisconnect();
    @Inject(method={"tick","checkBlockTarget","scrollPattern"},at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$hostPermission(CallbackInfo ci){
        if(!SurvivalHostMods.veinClientActive()){
            unbox$discardClicks();
            if(unbox$wasActive){unbox$wasActive=false;onDisconnect();}
            ci.cancel();
        }else{
            unbox$wasActive=true;
            if(Minecraft.getInstance().screen!=null){unbox$discardClicks();ci.cancel();}
        }
    }
    @Inject(method="onDisconnect",at=@At("HEAD"),remap=false)
    private void unbox$resetKey(CallbackInfo ci){unbox$wasActive=false;unbox$discardClicks();}
    @Unique private void unbox$discardClicks(){KeyMapping key=SurvivalHostMods.key();if(key!=null)while(key.consumeClick()){} }
    @Inject(method="isPressed",at=@At("HEAD"),cancellable=true,remap=false)
    private void unbox$inactiveKey(CallbackInfoReturnable<Boolean> cir){
        if(!SurvivalHostMods.veinClientActive())cir.setReturnValue(false);
    }
}
