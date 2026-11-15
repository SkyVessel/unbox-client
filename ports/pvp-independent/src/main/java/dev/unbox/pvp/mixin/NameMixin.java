package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(EntityPlayer.class)
public class NameMixin {
 @Inject(method="getDisplayName",at=@At("RETURN"),cancellable=true) private void local(CallbackInfoReturnable<IChatComponent> ci){EntityPlayer self=(EntityPlayer)(Object)this;if(!self.worldObj.isRemote||self!=net.minecraft.client.Minecraft.getMinecraft().thePlayer)return;String nick=PvpSettings.nickname(self.getName());if(!nick.equals(self.getName())){ChatComponentText text=new ChatComponentText(nick);text.setChatStyle(ci.getReturnValue().getChatStyle().createShallowCopy());ci.setReturnValue(text);}}
}
