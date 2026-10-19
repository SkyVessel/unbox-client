package dev.unbox.client.mixin;

import dev.unbox.client.WorldFeatures;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Drain disabled mod-specific clicks so modifiers/undo/redo cannot open or run later. */
@Mixin(KeyMapping.class)
public abstract class BuildingClickGateMixin {
    @Shadow @Final private String name;
    @Shadow private int clickCount;
    @Inject(method="consumeClick",at=@At("HEAD"),cancellable=true)
    private void unbox$buildingClick(CallbackInfoReturnable<Boolean> result){
        if(name.startsWith("key.effortlessbuilding.")&&!WorldFeatures.buildingClient()){clickCount=0;result.setReturnValue(false);}
    }
}
