package dev.unbox.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.unbox.client.GraveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The invisible grave anchor has no baked model for vanilla's crack pass. */
@Mixin(LevelRenderer.class)
public abstract class GraveBreakingRenderMixin {
    @Inject(method="submitBlockDestroyAnimation", at=@At("HEAD"))
    private void unbox$graveCracks(PoseStack pose, SubmitNodeCollector collector, LevelRenderState state, CallbackInfo ci) {
        var mc=Minecraft.getInstance();
        if(mc.level==null)return;
        for(var breaking:state.blockBreakingRenderStates){
            var pos=breaking.blockPos();
            if(!breaking.blockState().is(Blocks.STRUCTURE_VOID)||!GraveModel.isGraveAnchor(mc.level,pos))continue;
            var stone=Blocks.STONE.defaultBlockState();
            var model=mc.getModelManager().getBlockStateModelSet().get(stone);
            var camera=state.cameraRenderState.pos;
            pose.pushPose();
            // Match the stone display exactly: one block wide/high and 1/4 block deep.
            pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z+.375);
            pose.scale(1,1,.25f);
            collector.submitBreakingBlockModel(pose,model,stone.getSeed(pos),breaking.progress());
            pose.popPose();
        }
    }
}
