package dev.unbox.unpvp;
import net.minecraft.client.Minecraft;
public final class UiFramePolicy {
    private UiFramePolicy(){}
    public static int limit(){Minecraft mc=Minecraft.getMinecraft();return mc.currentScreen instanceof UiScreen?Math.max(30,mc.gameSettings.limitFramerate):-1;}
}
