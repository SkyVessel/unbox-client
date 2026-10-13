package dev.unbox.client.mixin;
import dev.unbox.client.NameBadges;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(EntityRenderer.class)
public abstract class NameBadgeMixin {
 @Inject(method="extractRenderState",at=@At("TAIL"))
 private void unbox$nameBadge(Entity entity,EntityRenderState state,float partialTick,CallbackInfo ci){if(entity instanceof Player&&state.nameTag!=null&&NameBadges.known(entity.getUUID()))state.nameTag=NameBadges.prefix(state.nameTag);}
}
