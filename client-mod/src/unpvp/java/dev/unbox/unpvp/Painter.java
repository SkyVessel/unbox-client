package dev.unbox.unpvp;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;

/** Modern PanelStyle geometry and atlas metrics, rendered by the 1.8.9 OpenGL backend. */
final class Painter {
    static int TEXT=0xff29262f,MUTED=0xff64606b,ACCENT=0xffc9203d;
    static float opacity=1,scale=1,offset=0;
    private static final Map<String,Integer> textures=new HashMap<>();
    private static final Map<String,float[]> layouts=new LinkedHashMap<String,float[]>(256,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,float[]> e){return size()>256;}};
    private static String chars;private static int[] advances;
    private static int oldTexture;
    private static final java.nio.IntBuffer scissor=BufferUtils.createIntBuffer(16);
    private static final Deque<int[]> clips=new ArrayDeque<>();
    static void begin(float s,float dy){
        TEXT=SolidTheme.text();MUTED=SolidTheme.muted();ACCENT=SolidTheme.accent();
        oldTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPushMatrix();GL11.glScalef(s,s,1);GL11.glTranslatef(0,dy,0);
        GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_CULL_FACE);
        scale=s;offset=dy;
    }
    static void end(){GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glPopMatrix();GL11.glPopAttrib();GlStateManager.bindTexture(0);GlStateManager.bindTexture(oldTexture);GlStateManager.color(1,1,1,1);opacity=1;}
    static int tint(int c){return (c&0xffffff)|(Math.round((c>>>24)*opacity)<<24);}
    private static void color(int c){c=tint(c);GL11.glColor4f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,(c>>>24)/255f);}
    static void fill(float x,float y,float w,float h,int c){if(w<=0||h<=0)return;GL11.glDisable(GL11.GL_TEXTURE_2D);color(c);GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2f(x,y);GL11.glVertex2f(x,y+h);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x+w,y);GL11.glEnd();}
    static void outline(float x,float y,float w,float h,int c){fill(x,y,w,1,c);fill(x,y+h-1,w,1,c);fill(x,y,1,h,c);fill(x+w-1,y,1,h,c);}
    static void gradient(float x,float y,float w,float h,int top,int bottom){GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glShadeModel(GL11.GL_SMOOTH);GL11.glBegin(GL11.GL_QUADS);color(top);GL11.glVertex2f(x,y);GL11.glVertex2f(x+w,y);color(bottom);GL11.glVertex2f(x+w,y+h);GL11.glVertex2f(x,y+h);GL11.glEnd();GL11.glShadeModel(GL11.GL_FLAT);}
    static void round(float x,float y,float w,float h,float r,int c){if(w<=0||h<=0)return;Edges.round(x,y,w,h,Math.min(r,Math.min(w,h)/2),c,false);}
    static void bottomRound(float x,float y,float w,float h,float r,int c){Edges.round(x,y,w,h,r,c,true);}
    static int texture(String name){
        Integer found=textures.get(name);if(found!=null)return found;
        try(InputStream in=Painter.class.getResourceAsStream("/assets/unbox/textures/ui/"+name+".png")){
            if(in==null){if(!name.equals("mods"))return texture("mods");throw new IOException(name);}
            BufferedImage img=ImageIO.read(in);int w=img.getWidth(),h=img.getHeight();ByteBuffer b=TexturePixels.rgba(img,false,true);
            int id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,33071);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,33071);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);org.lwjgl.opengl.ARBFramebufferObject.glGenerateMipmap(GL11.GL_TEXTURE_2D);textures.put(name,id);return id;
        }catch(IOException e){throw new IllegalStateException("Missing Unbox UI asset "+name,e);}
    }
    static void warm(){SolidSurface.font();}

    private static void bind(String name){GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture(name));}
    private static void quad(float x,float y,float w,float h,float u,float v,float uw,float vh){GL11.glTexCoord2f(u,v);GL11.glVertex2f(x,y);GL11.glTexCoord2f(u,v+vh);GL11.glVertex2f(x,y+h);GL11.glTexCoord2f(u+uw,v+vh);GL11.glVertex2f(x+w,y+h);GL11.glTexCoord2f(u+uw,v);GL11.glVertex2f(x+w,y);}
    static void icon(String name,float x,float y,float size,int c){if(SolidGeometry.icon(name,x,y,size,c))return;bind(name);color(c);GL11.glBegin(GL11.GL_QUADS);quad(x,y,size,size,0,0,1,1);GL11.glEnd();}
    private static void metrics(){if(advances!=null)return;try(Reader r=new InputStreamReader(Painter.class.getResourceAsStream("/assets/unbox/ui/metrics.json"),"UTF-8")){JsonObject j=new JsonParser().parse(r).getAsJsonObject();chars=j.get("chars").getAsString();advances=new Gson().fromJson(j.get("advances"),int[].class);}catch(IOException e){throw new IllegalStateException(e);}}
    static float advance(char c){return SolidText.width(String.valueOf(c),10);}
    static int width(String s){return Math.round(SolidText.width(s,10));}
    static void text(String s,float x,float y,int c){SolidText.draw(s,x,y,10,c,false);}
    static void center(String s,float x,float y,int c){text(s,x-width(s)/2f,y,c);}
    static void clip(float x,float y,float w,float h){Minecraft m=Minecraft.getMinecraft();net.minecraft.client.gui.ScaledResolution r=new net.minecraft.client.gui.ScaledResolution(m);float px=r.getScaleFactor()*scale;
        java.nio.IntBuffer old=scissor;old.clear();GL11.glGetInteger(GL11.GL_SCISSOR_BOX,old);boolean enabled=GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);clips.push(new int[]{enabled?1:0,old.get(0),old.get(1),old.get(2),old.get(3)});
        int x1=(int)Math.floor(x*px),y2=m.displayHeight-(int)Math.floor((y+offset)*px),x2=(int)Math.ceil((x+w)*px),y1=m.displayHeight-(int)Math.ceil((y+h+offset)*px);
        if(enabled){x1=Math.max(x1,old.get(0));y1=Math.max(y1,old.get(1));x2=Math.min(x2,old.get(0)+old.get(2));y2=Math.min(y2,old.get(1)+old.get(3));}
        GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(x1,y1,Math.max(0,x2-x1),Math.max(0,y2-y1));
    }
    static void unclip(){int[] previous=clips.pop();GL11.glScissor(previous[1],previous[2],previous[3],previous[4]);if(previous[0]==0)GL11.glDisable(GL11.GL_SCISSOR_TEST);else GL11.glEnable(GL11.GL_SCISSOR_TEST);}
    static int mix(int a,int b,float t){int c=0;for(int s=0;s<32;s+=8)c|=(Math.round(((a>>>s)&255)*(1-t)+((b>>>s)&255)*t)&255)<<s;return c;}
    static final class Motion {float value;long time;float to(float target){long now=System.nanoTime();float dt=time==0?0:Math.min(.1f,(now-time)/1e9f);time=now;if(UiState.flag("ui.reducedMotion",false))value=target;else value+=(target-value)*(1-(float)Math.exp(-dt*18));return value;}}
}
