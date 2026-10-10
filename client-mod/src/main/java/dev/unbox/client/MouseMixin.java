package dev.unbox.client.mixin;
import dev.unbox.client.UnboxClient;
import dev.unbox.client.OptifineCape;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(MouseHandler.class)
public class MouseMixin {
 @Unique private final net.minecraft.util.SmoothDouble unboxX=new net.minecraft.util.SmoothDouble(),unboxY=new net.minecraft.util.SmoothDouble();
 @Unique private long unboxTurnTime;
 @Inject(method="onScroll",at=@At("HEAD"),cancellable=true)
 private void unboxScroll(long window,double horizontal,double vertical,CallbackInfo ci){if(UnboxClient.adjustZoom(vertical)){UnboxClient.save();ci.cancel();}}
 @Redirect(method="turnPlayer",at=@At(value="INVOKE",target="Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
 private void unboxTurn(net.minecraft.client.player.LocalPlayer player,double x,double y){
  long now=System.nanoTime();double dt=unboxTurnTime==0?.016:Math.min(.1,(now-unboxTurnTime)/1e9);unboxTurnTime=now;
  if(dev.unbox.client.Freelook.active()&&UnboxClient.enabled("freelook")){
   if(dev.unbox.client.ClientHud.flag("freelook.invertX",false))x=-x;if(dev.unbox.client.ClientHud.flag("freelook.invertY",false))y=-y;
   if(dev.unbox.client.ClientHud.flag("freelook.smooth",false)){x=unboxX.getNewDeltaValue(x,Math.min(1,dt*20));y=unboxY.getNewDeltaValue(y,Math.min(1,dt*20));}else{unboxX.reset();unboxY.reset();}
  }else {unboxX.reset();unboxY.reset();}
  if(UnboxClient.zooming()&&dev.unbox.client.ClientHud.flag("zoom.adaptive",true)){double f=UnboxClient.number("zoom.factor",3,2,12);x/=f;y/=f;}
  dev.unbox.client.Freelook.turn(player,x,y);
 }

 @Inject(method="onButton",at=@At("HEAD"))
 private void unboxClick(long window,MouseButtonInfo button,int action,CallbackInfo ci){UnboxClient.mouse(button.button(),action);}
}
