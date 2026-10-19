package dev.unbox.client.mixin;

import dev.unbox.client.LogoResources;
import java.util.List;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;

@Mixin(MultiPackResourceManager.class)
public abstract class LogoResourcePriorityMixin {
    @ModifyVariable(method="<init>",at=@At("HEAD"),argsOnly=true,index=2)
    private static List<PackResources> unbox$identityPriority(List<PackResources> value,PackType type,List<PackResources> packs){return LogoResources.prioritize(type,value);}
}
