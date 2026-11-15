package dev.unbox.unpvp;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

/** Bounded cache for changing labels; static text is baked by the surface worker. */
final class SolidText {
    private static final FontRenderContext FRC=new FontRenderContext(null,true,true);
    private static final LinkedHashMap<String,SolidTexture> CACHE=new LinkedHashMap<>(128,.75f,true);
    private static long bytes;
    private static Font font(String s,float size){Font f=SolidSurface.font();return (f.canDisplayUpTo(s)<0?f:new Font("Dialog",Font.PLAIN,12)).deriveFont(size);}
    static float width(String text,int size){return (float)font(text,size).getStringBounds(text,FRC).getWidth();}
    static void draw(String text,float x,float y,int size,int color,boolean centered){
        if(text.isEmpty())return;float pixels=new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor()*Painter.scale;int fontPixels=Math.max(8,(int)Math.ceil(size*pixels));float ratio=fontPixels/(float)size;
        String key=fontPixels+":"+text;SolidTexture texture=CACHE.get(key);
        if(texture==null){SolidTexture.Pixels pixelsReady=prepared(key);texture=new SolidTexture(pixelsReady==null?raster(text,fontPixels):pixelsReady,false);CACHE.put(key,texture);bytes+=(long)texture.width*texture.height*4;while(CACHE.size()>512||bytes>8L*1024*1024){Iterator<SolidTexture> i=CACHE.values().iterator();SolidTexture old=i.next();bytes-=(long)old.width*old.height*4;old.close();i.remove();}}
        texture.draw(x-(centered?width(text,size)/2:0)-2/ratio,y-2/ratio,texture.width/ratio,texture.height/ratio,color);
    }
    private static final LinkedHashMap<String,SolidTexture.Pixels> PREPARED=new LinkedHashMap<>();
    private static long preparedBytes;private static volatile int generation;
    private static synchronized SolidTexture.Pixels prepared(String key){SolidTexture.Pixels p=PREPARED.remove(key);if(p!=null)preparedBytes-=(long)p.width*p.height*4;return p;}
    private static SolidTexture.Pixels raster(String text,int pixels){Font f=font(text,pixels);int w=(int)Math.ceil(f.getStringBounds(text,FRC).getWidth())+4,h=(int)Math.ceil(pixels*1.5)+4;BufferedImage image=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);g.setColor(Color.WHITE);g.setFont(f);g.drawString(text,2,2+g.getFontMetrics().getAscent());}finally{g.dispose();}return new SolidTexture.Pixels(image);}
    static void prepare(Collection<String> labels,float pixelScale){final int version=++generation,pixels=Math.max(8,(int)Math.ceil(10*pixelScale));final Set<String> snapshot=new LinkedHashSet<>(labels);Thread worker=new Thread(()->{for(String label:snapshot){if(version!=generation)return;if(label.isEmpty())continue;SolidTexture.Pixels p=raster(label,pixels);synchronized(SolidText.class){if(version!=generation)return;String key=pixels+":"+label;if(!PREPARED.containsKey(key)){PREPARED.put(key,p);preparedBytes+=(long)p.width*p.height*4;}while(preparedBytes>8L*1024*1024){Iterator<SolidTexture.Pixels> i=PREPARED.values().iterator();SolidTexture.Pixels old=i.next();preparedBytes-=(long)old.width*old.height*4;i.remove();}}}},"Unbox settings text");worker.setDaemon(true);worker.start();}
    static void clear(){for(SolidTexture t:CACHE.values())t.close();CACHE.clear();bytes=0;synchronized(SolidText.class){generation++;PREPARED.clear();preparedBytes=0;}}
}
