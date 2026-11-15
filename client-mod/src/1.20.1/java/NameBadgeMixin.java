package dev.unbox.client.mixin;
import dev.unbox.client.*;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(EntityRenderer.class) public abstract class NameBadgeMixin {
 @ModifyVariable(method="renderNameTag",at=@At("HEAD"),argsOnly=true)private Component badge(Component name,Entity entity){if(entity instanceof Player){name=LocalNickname.render(entity.getUUID(),name);if(NameBadges.known(entity.getUUID()))name=NameBadges.prefix(entity.getUUID(),name);}return name;}
}
