package dev.unbox.client.mixin;

import dev.unbox.client.FreecamController;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Vanilla omits LocalPlayer whenever another entity owns the camera; Freecam needs the body visible. */
@Mixin(LevelRenderer.class)
public abstract class FreecamBodyRenderMixin {
    @Redirect(method="extractVisibleEntities",at=@At(value="INVOKE",target="Lnet/minecraft/client/Camera;entity()Lnet/minecraft/world/entity/Entity;",ordinal=3))
    private Entity unbox$renderLocalBody(Camera camera) {
        return FreecamController.active() ? Minecraft.getInstance().player : camera.entity();
    }
}
