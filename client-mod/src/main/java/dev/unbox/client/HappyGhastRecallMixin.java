package dev.unbox.client.mixin;
import dev.unbox.client.HappyGhastRecall;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(HappyGhast.class)
public abstract class HappyGhastRecallMixin extends Animal {
 protected HappyGhastRecallMixin(EntityType<? extends Animal> type,Level level){super(type,level);}
 @Inject(method="registerGoals",at=@At("TAIL"))
 private void unbox$recallGoal(CallbackInfo ci){goalSelector.addGoal(1,HappyGhastRecall.goal((HappyGhast)(Object)this));}
 @Inject(method="mobInteract",at=@At("HEAD"))
 private void unbox$interact(Player player,InteractionHand hand,CallbackInfoReturnable<InteractionResult> ci){if(player instanceof net.minecraft.server.level.ServerPlayer)HappyGhastRecall.interacted((HappyGhast)(Object)this,player);}
}
