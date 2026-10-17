package dev.unbox.client.mixin;

import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.class)
public interface GraveDisplayAccess {
    @Invoker("setTransformation") void unbox$setTransformation(com.mojang.math.Transformation transform);
    @Invoker("setWidth") void unbox$setWidth(float width);
    @Invoker("setHeight") void unbox$setHeight(float height);
    @Invoker("setViewRange") void unbox$setViewRange(float range);
    @Invoker("setBillboardConstraints") void unbox$setBillboard(Display.BillboardConstraints billboard);
}
