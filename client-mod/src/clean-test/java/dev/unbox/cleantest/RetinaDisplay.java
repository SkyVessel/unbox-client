package dev.unbox.cleantest;

import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.Display;

/** Window/input coordinates are points; Minecraft's render targets are physical pixels. */
public final class RetinaDisplay {
    private static final boolean MAC=System.getProperty("os.name","").startsWith("Mac");
    private static int previousScale=1;
    private RetinaDisplay(){}
    public static int scale(){return MAC&&Display.isCreated()?Math.max(1,Math.round(Display.getPixelScaleFactor())):1;}
    public static int pixels(int points){return points*scale();}
    public static int points(int pixels){return pixels/scale();}
    public static int width(){return pixels(Math.max(1,Display.getWidth()));}
    public static int height(){return pixels(Math.max(1,Display.getHeight()));}
    public static void initialize(Minecraft mc){mc.displayWidth=width();mc.displayHeight=height();previousScale=scale();report(mc);}
    public static boolean needsResize(Minecraft mc){
        if(!Display.isCreated())return false;
        Display.wasResized(); // Consume LWJGL's flag; scale-only changes also need a resize.
        int factor=scale(),w=width(),h=height();
        boolean changed=mc.displayWidth!=w||mc.displayHeight!=h||previousScale!=factor
            ||mc.getFramebuffer().framebufferWidth!=w||mc.getFramebuffer().framebufferHeight!=h;
        previousScale=factor;
        if(changed)System.out.println("[unpvp-retina] resize window="+Display.getWidth()+"x"+Display.getHeight()+" scale="+factor+" framebuffer="+w+"x"+h);
        return changed;
    }
    private static void report(Minecraft mc){System.out.println("[unpvp-retina] window="+Display.getWidth()+"x"+Display.getHeight()+" scale="+scale()+" framebuffer="+mc.displayWidth+"x"+mc.displayHeight);}
}
