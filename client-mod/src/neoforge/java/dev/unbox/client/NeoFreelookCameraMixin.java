package dev.unbox.client.mixin;
import dev.unbox.client.Freelook;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(Camera.class)
public class NeoFreelookCameraMixin {
 @Redirect(method="alignWithEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
 private float unboxYaw(Entity e,float delta){return Freelook.active()&&e==Minecraft.getInstance().player?Freelook.yaw():e.getViewYRot(delta);}
 @Redirect(method="alignWithEntity",at=@At(value="INVOKE",target="Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
 private float unboxPitch(Entity e,float delta){return Freelook.active()&&e==Minecraft.getInstance().player?Freelook.pitch():e.getViewXRot(delta);}
}
