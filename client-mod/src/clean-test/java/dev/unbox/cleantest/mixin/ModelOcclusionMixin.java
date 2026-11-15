package dev.unbox.cleantest.mixin;
import dev.unbox.cleantest.ModelOcclusion;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(RenderGlobal.class)
public class ModelOcclusionMixin {
 @Inject(method="renderEntities",at=@At("HEAD")) private void begin(Entity view,ICamera camera,float partial,CallbackInfo ci){ModelOcclusion.begin(partial);}
 @Inject(method="renderEntities",at=@At("RETURN")) private void end(Entity view,ICamera camera,float partial,CallbackInfo ci){ModelOcclusion.end();}
 @Inject(method="markBlockRangeForRenderUpdate",at=@At("HEAD")) private void blocks(int a,int b,int c,int d,int e,int f,CallbackInfo ci){ModelOcclusion.invalidate();}
}
