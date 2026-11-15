package dev.unbox.unpvp;
import java.awt.image.BufferedImage;
import java.util.*;
import org.lwjgl.opengl.GL11;

/** Render-thread textures. Immutable page pixels share bounded resident GPU storage across navigation. */
final class SolidTexture implements AutoCloseable {
    final int id,width,height;private boolean closed;private final Resident resident;
    static int alive,uploads,reuses;static long residentBytes;
    static final long LIMIT=128L*1024*1024;
    private static final LinkedHashMap<Pixels,Resident> CACHE=new LinkedHashMap<>(8,.75f,true);
    static final class Pixels {final int width,height;final java.nio.ByteBuffer rgba;Pixels(BufferedImage image){width=image.getWidth();height=image.getHeight();rgba=TexturePixels.rgba(image,false,true);}}
    private static final class Resident {final int id;final long bytes;int users;Resident(int id,Pixels p){this.id=id;bytes=(long)p.width*p.height*4;}}
    SolidTexture(BufferedImage image){this(new Pixels(image),false);}
    SolidTexture(Pixels pixels){this(pixels,true);}
    SolidTexture(Pixels pixels,boolean shared){width=pixels.width;height=pixels.height;Resident hit=shared?CACHE.get(pixels):null;
        if(hit!=null){resident=hit;id=hit.id;hit.users++;reuses++;return;}
        id=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,33071);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,33071);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels.rgba.duplicate());alive++;uploads++;
        if(shared&&(long)width*height*4<=LIMIT){resident=new Resident(id,pixels);resident.users=1;CACHE.put(pixels,resident);residentBytes+=resident.bytes;trim();}else resident=null;
    }
    private static void trim(){Iterator<Resident> i=CACHE.values().iterator();while(residentBytes>LIMIT&&i.hasNext()){Resident r=i.next();if(r.users==0){GL11.glDeleteTextures(r.id);alive--;residentBytes-=r.bytes;i.remove();}}}
    static void clearIdle(){Iterator<Resident> i=CACHE.values().iterator();while(i.hasNext()){Resident r=i.next();if(r.users==0){GL11.glDeleteTextures(r.id);alive--;residentBytes-=r.bytes;i.remove();}}}
    static int activeResidents(){int count=0;for(Resident r:CACHE.values())if(r.users>0)count++;return count;}
    void draw(float x,float y,float w,float h,int color){if(closed)return;int c=Painter.tint(color);GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);GL11.glColor4f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,(c>>>24)/255f);GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex2f(x,y);GL11.glTexCoord2f(0,1);GL11.glVertex2f(x,y+h);GL11.glTexCoord2f(1,1);GL11.glVertex2f(x+w,y+h);GL11.glTexCoord2f(1,0);GL11.glVertex2f(x+w,y);GL11.glEnd();}
    public void close(){if(!closed){closed=true;if(resident!=null){resident.users--;trim();}else{GL11.glDeleteTextures(id);alive--;}}}
}
