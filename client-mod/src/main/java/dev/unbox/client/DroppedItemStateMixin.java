package dev.unbox.client.mixin;

import dev.unbox.client.DroppedItemState;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(ItemEntityRenderState.class)
public abstract class DroppedItemStateMixin implements DroppedItemState {
    @Unique private boolean unbox$grounded;
    @Unique private double unbox$speed;
    public void unbox$motion(boolean grounded, double speed) { unbox$grounded=grounded; unbox$speed=speed; }
    public boolean unbox$grounded() { return unbox$grounded; }
    public double unbox$speed() { return unbox$speed; }
}
