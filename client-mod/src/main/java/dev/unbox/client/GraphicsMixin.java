package dev.unbox.client.mixin;
import dev.unbox.client.GraphicsAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GuiGraphicsExtractor.class)
public abstract class GraphicsMixin implements GraphicsAccess {
 @Shadow @Final private GuiRenderState guiRenderState;
 @Shadow public abstract Matrix3x2fStack pose();
 @Unique private java.util.ArrayDeque<ScreenRectangle> unbox$clips;
 public GuiRenderState unboxState(){return guiRenderState;}
 public ScreenRectangle unboxClip(){return unbox$clips==null?null:unbox$clips.peek();}
 @Inject(method="enableScissor",at=@At("TAIL"))private void unboxPush(int x,int y,int right,int bottom,CallbackInfo ci){if(unbox$clips==null)unbox$clips=new java.util.ArrayDeque<>();ScreenRectangle r=new ScreenRectangle(x,y,Math.max(0,right-x),Math.max(0,bottom-y)).transformMaxBounds(pose());if(!unbox$clips.isEmpty()){r=r.intersection(unbox$clips.peek());if(r==null)r=ScreenRectangle.empty();}unbox$clips.push(r);}
 @Inject(method="disableScissor",at=@At("TAIL"))private void unboxPop(CallbackInfo ci){if(unbox$clips!=null&&!unbox$clips.isEmpty())unbox$clips.pop();}
}
