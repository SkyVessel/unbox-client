package dev.unbox.client;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.*;

/** The same vector shapes drive visual feedback, hit testing and slider projection. */
final class SolidGeometry {
    private static final Map<String,Shape> SHAPES=new HashMap<>();
    private static final Map<String,Rectangle2D> BOUNDS=new HashMap<>();
    private static Rectangle2D bounds(String name){return BOUNDS.computeIfAbsent(name,n->SHAPES.get(n).getBounds2D());}
    private static final Map<String,Identifier> MASKS=new HashMap<>();
    static final Curve THEME=new Curve("theme-curve","M1185 134H1218C1256.66 134 1288 165.34 1288 204V248");
    static final Curve TITLE=new Curve("title-opacity","M542 474V558C542 591.14 568.86 618 602 618H684");
    static final Curve QUICK=new Curve("quick-opacity","M566 432V508C566 541.14 592.86 568 626 568H718");
    static {
        SHAPES.put("toggle-track",new RoundRectangle2D.Double(0,0,46,26,26,26));
        SHAPES.put("toggle-knob",new Ellipse2D.Double(0,0,18,18));
        try(var in=SolidGeometry.class.getResourceAsStream("/assets/unbox/solid/geometry.json")){
            var data=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
            data.entrySet().forEach(e->SHAPES.put(e.getKey(),VectorScene.path(e.getValue().getAsString())));
        }catch(Exception e){throw new IllegalStateException("UI geometry is missing",e);}
        try(var in=SolidGeometry.class.getResourceAsStream("/assets/unbox/solid/icons.json")){
            var data=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();
            SHAPES.put("icon-fullbright",VectorScene.path(data.get("fullbright").getAsString()));BOUNDS.put("icon-fullbright",new Rectangle2D.Double(0,0,65,65));
        }catch(Exception e){throw new IllegalStateException("Daytime icon is missing",e);}
    }
    static boolean contains(String name,double x,double y,double w,double h){Shape shape=SHAPES.get(name);var b=bounds(name);return shape.contains(b.getX()+x*b.getWidth()/w,b.getY()+y*b.getHeight()/h);}
    static void draw(GuiGraphicsExtractor g,String name,int x,int y,int w,int h,int color){
        if(w<=0||h<=0)return;
        Identifier id=MASKS.computeIfAbsent(name,n->{
            Shape shape=SHAPES.get(n);var b=bounds(n);int width=Math.max(1,(int)Math.ceil(b.getWidth()*4)),height=Math.max(1,(int)Math.ceil(b.getHeight()*4));
            BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);var p=image.createGraphics();p.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);p.scale(width/b.getWidth(),height/b.getHeight());p.translate(-b.getX(),-b.getY());p.setColor(Color.WHITE);p.fill(shape);p.dispose();
            NativeImage nativeImage=new NativeImage(width,height,false);for(int row=0;row<height;row++)for(int col=0;col<width;col++)nativeImage.setPixel(col,row,image.getRGB(col,row));
            Identifier texture=Identifier.fromNamespaceAndPath("unbox","solid_geometry/"+n);Minecraft.getInstance().getTextureManager().register(texture,new MaskTexture(nativeImage));return texture;
        });
        var b=bounds(name);int tw=Math.max(1,(int)Math.ceil(b.getWidth()*4)),th=Math.max(1,(int)Math.ceil(b.getHeight()*4));
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,x,y,0f,0f,w,h,tw,th,tw,th,color);
    }
    private static final class MaskTexture extends DynamicTexture{MaskTexture(NativeImage image){super(()->"Unbox geometry",image);sampler=RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,FilterMode.LINEAR,FilterMode.LINEAR,false);}}
    static void centeredLabel(GuiGraphicsExtractor g,String name,int cx,int cy,int color){var b=bounds(name);int w=(int)Math.round(b.getWidth()),h=(int)Math.round(b.getHeight());draw(g,name,cx-w/2,cy-h/2,w,h,color);}
    static void verticalTab(GuiGraphicsExtractor g,int cx,int cy,int color){g.pose().pushMatrix();g.pose().translate(cx-24,cy+42);g.pose().rotate((float)-Math.PI/2);draw(g,"tab",0,0,84,48,color);g.pose().popMatrix();}
    static final class Curve {
        final String name;final List<Point2D.Double> points=new ArrayList<>();private final double[] distance;
        Curve(String name,String path){
            this.name=name;Path2D line=VectorScene.path(path);SHAPES.put(name,new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(line));
            var iterator=line.getPathIterator(null,.1);double[] p=new double[6];while(!iterator.isDone()){int type=iterator.currentSegment(p);if(type==PathIterator.SEG_MOVETO||type==PathIterator.SEG_LINETO)points.add(new Point2D.Double(p[0],p[1]));iterator.next();}
            distance=new double[points.size()];for(int i=1;i<distance.length;i++)distance[i]=distance[i-1]+points.get(i).distance(points.get(i-1));
        }
        Point2D.Double point(double t){double d=Math.clamp(t,0,1)*distance[distance.length-1];int i=1;while(i<distance.length-1&&distance[i]<d)i++;var a=points.get(i-1);var b=points.get(i);double f=(d-distance[i-1])/(distance[i]-distance[i-1]);return new Point2D.Double(a.x+(b.x-a.x)*f,a.y+(b.y-a.y)*f);}
        double nearest(double x,double y){double best=Double.POSITIVE_INFINITY,result=0;for(int i=1;i<points.size();i++){var a=points.get(i-1);var b=points.get(i);double dx=b.x-a.x,dy=b.y-a.y;double f=Math.clamp(((x-a.x)*dx+(y-a.y)*dy)/(dx*dx+dy*dy),0,1);double d=Point2D.distanceSq(x,y,a.x+f*dx,a.y+f*dy);if(d<best){best=d;result=(distance[i-1]+f*(distance[i]-distance[i-1]))/distance[distance.length-1];}}return result;}
        boolean contains(double x,double y){return point(nearest(x,y)).distance(x,y)<=18;}
        void draw(GuiGraphicsExtractor g,int color){var b=bounds(name);g.pose().pushMatrix();g.pose().translate((float)b.getX(),(float)b.getY());g.pose().scale((float)(b.getWidth()/Math.ceil(b.getWidth())),(float)(b.getHeight()/Math.ceil(b.getHeight())));SolidGeometry.draw(g,name,0,0,(int)Math.ceil(b.getWidth()),(int)Math.ceil(b.getHeight()),color);g.pose().popMatrix();}
    }
}
