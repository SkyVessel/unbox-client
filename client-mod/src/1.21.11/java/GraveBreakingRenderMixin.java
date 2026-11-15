package dev.unbox.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import dev.unbox.client.GraveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 1.21.11 submits cracking geometry directly to the destruction buffer. */
@Mixin(LevelRenderer.class)
public abstract class GraveBreakingRenderMixin {
    @Inject(method="renderBlockDestroyAnimation", at=@At("HEAD"))
    private void unbox$graveCracks(PoseStack pose, MultiBufferSource.BufferSource buffers, LevelRenderState state, CallbackInfo ci) {
        var mc=Minecraft.getInstance();
        if(mc.level==null)return;
        for(var breaking:state.blockBreakingRenderStates){
            var pos=breaking.blockPos;
            if(!breaking.blockState.is(Blocks.STRUCTURE_VOID)||!GraveModel.isGraveAnchor(mc.level,pos))continue;
            var camera=state.cameraRenderState.pos;
            pose.pushPose();
            pose.translate(pos.getX()-camera.x,pos.getY()-camera.y,pos.getZ()-camera.z+.375);
            pose.scale(1,1,.25f);
            var vertices=new SheetedDecalTextureGenerator(buffers.getBuffer(ModelBakery.DESTROY_TYPES.get(breaking.progress)),pose.last(),1);
            mc.getBlockRenderer().renderBreakingTexture(Blocks.STONE.defaultBlockState(),pos,breaking,pose,vertices);
            pose.popPose();
        }
    }
}
