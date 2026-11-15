package dev.unbox.client.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.unbox.client.GraveModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.server.level.BlockDestructionProgress;
import net.minecraft.world.level.block.Blocks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LevelRenderer.class) public abstract class GraveBreakingRenderMixin {
 @Shadow @Final private it.unimi.dsi.fastutil.ints.Int2ObjectMap<BlockDestructionProgress> destroyingBlocks;
 @Inject(method="renderLevel",at=@At("TAIL"))private void cracks(PoseStack pose,float tick,long time,boolean outline,Camera camera,GameRenderer renderer,LightTexture light,org.joml.Matrix4f projection,CallbackInfo ci){var mc=Minecraft.getInstance();if(mc.level==null)return;var buffers=mc.renderBuffers().crumblingBufferSource();for(var breaking:destroyingBlocks.values()){var pos=breaking.getPos();int progress=breaking.getProgress();if(progress<0||progress>9||!mc.level.getBlockState(pos).is(Blocks.STRUCTURE_VOID)||!GraveModel.isGraveAnchor(mc.level,pos))continue;var c=camera.getPosition();pose.pushPose();try{pose.translate(pos.getX()-c.x,pos.getY()-c.y,pos.getZ()-c.z+.375);pose.scale(1,1,.25f);mc.getBlockRenderer().renderBreakingTexture(Blocks.STONE.defaultBlockState(),pos,mc.level,pose,buffers.getBuffer(ModelBakery.DESTROY_TYPES.get(progress)));}finally{pose.popPose();}}buffers.endBatch();}
}
