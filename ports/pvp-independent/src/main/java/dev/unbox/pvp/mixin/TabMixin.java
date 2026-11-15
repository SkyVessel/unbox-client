package dev.unbox.pvp.mixin;
import dev.unbox.unpvp.PvpSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiPlayerTabOverlay;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
/** Header/footer and numeric ping hooks adapted from VanillaHUD v2.2.12 (GPL-3.0). */
@Mixin(GuiPlayerTabOverlay.class)
public class TabMixin {
 @Shadow private IChatComponent header,footer;
 @Redirect(method="renderPlayerlist",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/GuiPlayerTabOverlay;header:Lnet/minecraft/util/IChatComponent;")) private IChatComponent header(GuiPlayerTabOverlay self){return PvpSettings.on("tab")&&!PvpSettings.flag("tab.header",true)?null:header;}
 @Redirect(method="renderPlayerlist",at=@At(value="FIELD",target="Lnet/minecraft/client/gui/GuiPlayerTabOverlay;footer:Lnet/minecraft/util/IChatComponent;")) private IChatComponent footer(GuiPlayerTabOverlay self){return PvpSettings.on("tab")&&!PvpSettings.flag("tab.footer",true)?null:footer;}
 @Inject(method="drawPing",at=@At("HEAD"),cancellable=true) private void ping(int width,int x,int y,NetworkPlayerInfo info,CallbackInfo ci){if(PvpSettings.on("tab")&&PvpSettings.flag("tab.ping",true)){String s=info.getResponseTime()<0?"--":String.valueOf(info.getResponseTime());Minecraft mc=Minecraft.getMinecraft();net.minecraft.client.renderer.GlStateManager.pushMatrix();net.minecraft.client.renderer.GlStateManager.translate(x+width-1,y+2,0);net.minecraft.client.renderer.GlStateManager.scale(.5f,.5f,1);mc.fontRendererObj.drawStringWithShadow(s,-mc.fontRendererObj.getStringWidth(s),0,0xffffff);net.minecraft.client.renderer.GlStateManager.popMatrix();ci.cancel();}}
 @Inject(method="getPlayerName",at=@At("RETURN"),cancellable=true) private void nickname(NetworkPlayerInfo info,CallbackInfoReturnable<String> ci){if(!info.getGameProfile().getId().equals(Minecraft.getMinecraft().getSession().getProfile().getId()))return;String name=info.getGameProfile().getName(),nick=PvpSettings.nickname(name);if(!name.equals(nick))ci.setReturnValue(ci.getReturnValue().replace(name,nick));}
}
