package dev.unbox.unpvp;
import java.io.File;
import java.nio.ByteBuffer;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
/** Compare the rendered image with the actual window drawable, before its buffer swap. */
public final class PresentationProbe {
 private static int frames;private static boolean done;
 public static void check(){if(!Boolean.getBoolean("unpvp.test.presentation")||done)return;Minecraft mc=Minecraft.getMinecraft();if(!(mc.currentScreen instanceof SolidScreen)||!((SolidScreen)mc.currentScreen).surfaceReady()||++frames<90)return;done=true;
  int bound=GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),w=mc.displayWidth,h=mc.displayHeight;
  try{mc.getFramebuffer().bindFramebuffer(false);ByteBuffer expected=read(w,h);mc.getFramebuffer().unbindFramebuffer();GL11.glReadBuffer(GL11.GL_BACK);ByteBuffer actual=read(w,h);int bad=0,total=0;for(int y=8;y<h-8;y+=8)for(int x=8;x<w-8;x+=8){int at=(y*w+x)*4;total++;for(int c=0;c<3;c++)if(Math.abs((actual.get(at+c)&255)-(expected.get(at+c)&255))>2){bad++;break;}}
   File dir=new File(mc.mcDataDir,"presentation-probe");dir.mkdirs();BufferedImage img=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int at=(y*w+x)*4;img.setRGB(x,h-1-y,((actual.get(at)&255)<<16)|((actual.get(at+1)&255)<<8)|(actual.get(at+2)&255));}ImageIO.write(img,"png",new File(dir,"window-drawable.png"));
   System.out.println("[unpvp-presentation] drawable="+w+"x"+h+" mismatch="+bad+"/"+total);if(bad>total/100||GL11.glGetError()!=0)throw new IllegalStateException("Window drawable does not match full-resolution framebuffer");System.out.println("[unpvp-presentation] PASS");
  }catch(Exception e){e.printStackTrace();System.out.println("[unpvp-presentation] FAILED");}finally{OpenGlHelperCompat.bind(bound);mc.shutdown();}
 }
 private static ByteBuffer read(int w,int h){ByteBuffer b=BufferUtils.createByteBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);return b;}
 private static final class OpenGlHelperCompat{static void bind(int id){net.minecraft.client.renderer.OpenGlHelper.glBindFramebuffer(net.minecraft.client.renderer.OpenGlHelper.GL_FRAMEBUFFER,id);}}
}
