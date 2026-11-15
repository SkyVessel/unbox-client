package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.shader.*;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** PolyBlur phosphor pipeline (GPL-3.0), independent of the active world shader and HUD. */
@Mixin(EntityRenderer.class)
public class BlurMixin {
 @Unique private ShaderGroup unbox$blur;@Unique private int unbox$width,unbox$height;@Unique private boolean unbox$failed;
 @Inject(method="updateCameraAndRender",at=@At("HEAD")) private void lifecycle(float partial,long time,CallbackInfo ci){Minecraft mc=Minecraft.getMinecraft();boolean enabled=PvpSettings.on("blur")&&mc.theWorld!=null&&mc.currentScreen==null&&OpenGlHelper.shadersSupported;if(!enabled){if(unbox$blur!=null)unbox$blur.deleteShaderGroup();unbox$blur=null;unbox$failed=false;return;}if(unbox$failed)return;
  try{if(unbox$blur==null){unbox$blur=new ShaderGroup(mc.getTextureManager(),mc.getResourceManager(),mc.getFramebuffer(),new ResourceLocation("shaders/post/phosphor_motion_blur.json"));unbox$width=0;}if(unbox$width!=mc.displayWidth||unbox$height!=mc.displayHeight){unbox$width=mc.displayWidth;unbox$height=mc.displayHeight;unbox$blur.createBindFramebuffers(unbox$width,unbox$height);}for(Shader shader:((ShaderAccess)unbox$blur).unbox$shaders()){ShaderUniform weight=shader.getShaderManager().getShaderUniform("Weight");if(weight!=null)weight.set(Math.max(.1f,1-PvpSettings.number("blur.strength",3,1,9)/10f+.1f));}}
  catch(Exception e){if(unbox$blur!=null)unbox$blur.deleteShaderGroup();unbox$blur=null;unbox$failed=true;System.err.println("[unpvp] Motion blur unavailable: "+e.getMessage());}finally{mc.getFramebuffer().bindFramebuffer(true);}
 }
 @Inject(method="updateCameraAndRender",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/RenderGlobal;renderEntityOutlineFramebuffer()V",shift=At.Shift.AFTER))
 private void render(float partial,long time,CallbackInfo ci){if(unbox$blur!=null){GlStateManager.matrixMode(5890);GlStateManager.pushMatrix();GlStateManager.loadIdentity();unbox$blur.loadShaderGroup(partial);GlStateManager.popMatrix();GlStateManager.matrixMode(5888);Minecraft.getMinecraft().getFramebuffer().bindFramebuffer(true);}}
}
