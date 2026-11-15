package dev.unbox.unpvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.*;
/** GPU-only previous-frame crossfade. Keep the previous page until the next surface is ready. */
final class UiTransition {
 private static int texture,w,h;private static long started;private static GuiScreen target;
 static int captures;static long readyDelay;private static long requested;
 static void open(GuiScreen next){capture(next,false);}
 static void refresh(GuiScreen next){if(texture==0)capture(next,true);}
 private static void capture(GuiScreen next,boolean refresh){Minecraft mc=Minecraft.getMinecraft();if(!refresh&&next==mc.currentScreen||!(next instanceof UiScreen||mc.currentScreen instanceof UiScreen)||SolidTheme.reduced())return;
  // The first title has its own short entrance; there is no previous UI yet.
  if(mc.currentScreen==null&&mc.theWorld==null)return;
  long began=System.nanoTime();release();w=mc.displayWidth;h=mc.displayHeight;int old=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);mc.getFramebuffer().bindFramebuffer(false);texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,33071);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,33071);GL11.glCopyTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,0,0,w,h,0);GlStateManager.bindTexture(0);GlStateManager.bindTexture(old);target=next;requested=began;captures++;
 }
 static void frame(){if(texture==0)return;Minecraft mc=Minecraft.getMinecraft();if(mc.currentScreen!=target||w!=mc.displayWidth||h!=mc.displayHeight||SolidTheme.reduced()){release();return;}
  long now=System.nanoTime();if(started==0&&(!(target instanceof SolidScreen)||((SolidScreen)target).surfaceReady())){started=now;readyDelay=(now-requested)/1000000;if(Boolean.getBoolean("unpvp.test.solid")||Boolean.getBoolean("unpvp.test.pvp"))System.out.println("[unpvp-transition] "+(target==null?"game":target.getClass().getSimpleName())+" ready-ms="+readyDelay);}
  float t=started==0?0:Math.min(1,(now-started)/120000000f);if(t>=1){release();return;}float alpha=1-t*t*(3-2*t);
  int oldProgram=GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);GL20.glUseProgram(0);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPushMatrix();GL11.glLoadIdentity();GL11.glOrtho(0,w,h,0,-1,1);GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glLoadIdentity();Painter.begin(1,0);
  try{GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glColor4f(1,1,1,alpha);GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,1);GL11.glVertex2f(0,0);GL11.glTexCoord2f(0,0);GL11.glVertex2f(0,h);GL11.glTexCoord2f(1,0);GL11.glVertex2f(w,h);GL11.glTexCoord2f(1,1);GL11.glVertex2f(w,0);GL11.glEnd();}finally{Painter.end();GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glPopMatrix();GL11.glMatrixMode(GL11.GL_MODELVIEW);GL20.glUseProgram(oldProgram);}
 }
 static boolean active(){return texture!=0;}
 private static void release(){if(texture!=0)GL11.glDeleteTextures(texture);texture=0;started=0;target=null;}
}
