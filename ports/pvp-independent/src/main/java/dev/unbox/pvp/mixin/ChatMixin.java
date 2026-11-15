package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.gui.GuiNewChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(GuiNewChat.class)
public class ChatMixin {
 @Inject(method="getChatScale",at=@At("HEAD"),cancellable=true) private void scale(CallbackInfoReturnable<Float> ci){if(PvpSettings.on("chat"))ci.setReturnValue(PvpSettings.number("chat.scale",100,50,100)/100f);}
 @Inject(method="getChatWidth",at=@At("HEAD"),cancellable=true) private void width(CallbackInfoReturnable<Integer> ci){if(PvpSettings.on("chat"))ci.setReturnValue(GuiNewChat.calculateChatboxWidth(PvpSettings.number("chat.width",100,20,100)/100f));}
 @Inject(method="getLineCount",at=@At("RETURN"),cancellable=true) private void lines(CallbackInfoReturnable<Integer> ci){if(PvpSettings.on("chat"))ci.setReturnValue(Math.min(ci.getReturnValue(),PvpSettings.number("chat.lines",20,1,50)));}
 @ModifyArg(method="drawChat",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/GuiNewChat;drawRect(IIIII)V",ordinal=0),index=4)
 private int background(int color){if(!PvpSettings.on("chat"))return color;if(!PvpSettings.flag("chat.background",false))return 0;int alpha=Math.min(255,(color>>>24)*2)*PvpSettings.number("chat.opacity",50,0,100)/100;return alpha<<24|(PvpSettings.color("chat.color","#000000")&0xffffff);}
 @ModifyArg(method="drawChat",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/FontRenderer;drawStringWithShadow(Ljava/lang/String;FFI)I"),index=3)
 private int text(int color){return PvpSettings.on("chat")?((color>>>24)*PvpSettings.number("chat.textOpacity",100,10,100)/100<<24)|(color&0xffffff):color;}
 @ModifyConstant(method="setChatLine",constant=@Constant(intValue=100)) private int history(int value){return PvpSettings.on("chat")?PvpSettings.number("chat.history",500,100,2000):value;}
}
