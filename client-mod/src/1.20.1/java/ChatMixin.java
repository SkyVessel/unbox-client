package dev.unbox.client.mixin;
import dev.unbox.client.UnboxClient;
import dev.unbox.client.OptifineCape;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ChatComponent.class)
public class ChatMixin {
 @Inject(method="getLinesPerPage",at=@At("RETURN"),cancellable=true)
 private void unboxLines(CallbackInfoReturnable<Integer> ci){if(UnboxClient.enabled("chat"))ci.setReturnValue(Math.min(ci.getReturnValue(),UnboxClient.number("chat.lines",20,1,40)));}
 // Chat-only color/opacity: never modifies the global text background option.
 @Redirect(method="render",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"))
 private void background(net.minecraft.client.gui.GuiGraphics g,int x1,int y1,int x2,int y2,int original){
  int color=original;if(UnboxClient.enabled("chat")&&(original&0xffffff)==0){float vanilla=net.minecraft.client.Minecraft.getInstance().options.textBackgroundOpacity().get().floatValue();float fade=vanilla>0?Math.min(1,(original>>>24)/(255f*vanilla)):1;int alpha=Math.round(fade*UnboxClient.number("chat.opacity",50,0,100)*2.55f);color=(alpha<<24)|(dev.unbox.client.ClientHud.color("chat.color","#000000")&0xffffff);}g.fill(x1,y1,x2,y2,color);
 }
}
