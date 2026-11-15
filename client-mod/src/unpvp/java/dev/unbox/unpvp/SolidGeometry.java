package dev.unbox.unpvp;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.List;
import java.io.*;
import com.google.gson.*;
import org.lwjgl.opengl.GL11;
/** The same vector shapes drive visual feedback, hit testing and slider projection. */
final class SolidGeometry {
    private static final Map<String,Shape> SHAPES=new HashMap<>();
    private static final Map<String,Rectangle2D> BOUNDS=new HashMap<>();
    private static Rectangle2D bounds(String name){return BOUNDS.computeIfAbsent(name,n->SHAPES.get(n).getBounds2D());}
    private static final LinkedHashMap<String,SolidTexture> MASKS=new LinkedHashMap<>(32,.75f,true);private static long maskBytes;
    static final Curve THEME=new Curve("theme-curve","M1185 134H1218C1256.66 134 1288 165.34 1288 204V248");
    static final Curve TITLE=new Curve("title-opacity","M542 474V558C542 591.14 568.86 618 602 618H684");
    static final Curve QUICK=new Curve("quick-opacity","M566 432V508C566 541.14 592.86 568 626 568H718");
    static {
        SHAPES.put("toggle-track",new RoundRectangle2D.Double(0,0,46,26,26,26));
        SHAPES.put("toggle-knob",new Ellipse2D.Double(0,0,18,18));
        try(InputStream in=SolidGeometry.class.getResourceAsStream("/assets/unbox/solid/geometry.json")){
            JsonObject data=new JsonParser().parse(new InputStreamReader(in,"UTF-8")).getAsJsonObject();
            data.entrySet().forEach(e->SHAPES.put(e.getKey(),VectorScene.path(e.getValue().getAsString())));
        }catch(Exception e){throw new IllegalStateException("UI geometry is missing",e);}
    }
    static boolean contains(String name,double x,double y,double w,double h){Shape shape=SHAPES.get(name);Rectangle2D b=bounds(name);return shape.contains(b.getX()+x*b.getWidth()/w,b.getY()+y*b.getHeight()/h);}
    static void draw(String name,float x,float y,float w,float h,int color){
        if(w<=0||h<=0)return;Rectangle2D b=bounds(name);
        float pixels=new net.minecraft.client.gui.ScaledResolution(net.minecraft.client.Minecraft.getMinecraft()).getScaleFactor()*Painter.scale;
        int pw=Math.max(1,(int)Math.ceil(w*pixels)),ph=Math.max(1,(int)Math.ceil(h*pixels));
        // Quantized high-resolution masks avoid allocating on each animated height/position.
        int bucket=Math.max(2,(int)Math.ceil(Math.max(pw/b.getWidth(),ph/b.getHeight())));
        String key=name+":"+bucket;SolidTexture mask=MASKS.get(key);
        if(mask==null){int tw=Math.max(1,(int)Math.ceil(b.getWidth()*bucket)),th=Math.max(1,(int)Math.ceil(b.getHeight()*bucket));BufferedImage image=new BufferedImage(tw,th,BufferedImage.TYPE_INT_ARGB);Graphics2D g=image.createGraphics();try{g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.scale(tw/b.getWidth(),th/b.getHeight());g.translate(-b.getX(),-b.getY());g.setColor(Color.WHITE);g.fill(SHAPES.get(name));}finally{g.dispose();}mask=new SolidTexture(image);MASKS.put(key,mask);maskBytes+=(long)tw*th*4;while(maskBytes>8L*1024*1024&&MASKS.size()>1){Iterator<SolidTexture> i=MASKS.values().iterator();SolidTexture old=i.next();maskBytes-=(long)old.width*old.height*4;old.close();i.remove();}}mask.draw(x,y,w,h,color);
    }
    static boolean icon(String name,float x,float y,float size,int color){Shape shape=SolidSurface.iconShape(name);if(shape==null)return false;String key="icon:"+name;if(!SHAPES.containsKey(key)){SHAPES.put(key,shape);BOUNDS.put(key,new Rectangle2D.Double(0,0,65,65));}draw(key,x,y,size,size,color);return true;}
    static void clear(){for(SolidTexture t:MASKS.values())t.close();MASKS.clear();maskBytes=0;}
    static void centeredLabel(String name,int cx,int cy,int color){Rectangle2D b=bounds(name);int w=(int)Math.round(b.getWidth()),h=(int)Math.round(b.getHeight());draw(name,cx-w/2,cy-h/2,w,h,color);}
    static void verticalTab(int cx,int cy,int color){GL11.glPushMatrix();GL11.glTranslatef(cx-24,cy+42,0);GL11.glRotatef(-90,0,0,1);draw("tab",0,0,84,48,color);GL11.glPopMatrix();}
    static final class Curve {
        final String name;final List<Point2D.Double> points=new ArrayList<>();private final double[] distance;
        Curve(String name,String path){
            this.name=name;Path2D line=VectorScene.path(path);SHAPES.put(name,new BasicStroke(3,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND).createStrokedShape(line));
            PathIterator iterator=line.getPathIterator(null,.1);double[] p=new double[6];while(!iterator.isDone()){int type=iterator.currentSegment(p);if(type==PathIterator.SEG_MOVETO||type==PathIterator.SEG_LINETO)points.add(new Point2D.Double(p[0],p[1]));iterator.next();}
            distance=new double[points.size()];for(int i=1;i<distance.length;i++)distance[i]=distance[i-1]+points.get(i).distance(points.get(i-1));
        }
        Point2D.Double point(double t){double d=clamp(t,0,1)*distance[distance.length-1];int i=1;while(i<distance.length-1&&distance[i]<d)i++;Point2D.Double a=points.get(i-1),b=points.get(i);double f=(d-distance[i-1])/(distance[i]-distance[i-1]);return new Point2D.Double(a.x+(b.x-a.x)*f,a.y+(b.y-a.y)*f);}
        double nearest(double x,double y){double best=Double.POSITIVE_INFINITY,result=0;for(int i=1;i<points.size();i++){Point2D.Double a=points.get(i-1),b=points.get(i);double dx=b.x-a.x,dy=b.y-a.y;double f=clamp(((x-a.x)*dx+(y-a.y)*dy)/(dx*dx+dy*dy),0,1);double d=Point2D.distanceSq(x,y,a.x+f*dx,a.y+f*dy);if(d<best){best=d;result=(distance[i-1]+f*(distance[i]-distance[i-1]))/distance[distance.length-1];}}return result;}
        boolean contains(double x,double y){return point(nearest(x,y)).distance(x,y)<=18;}
        void draw(int color){Rectangle2D b=bounds(name);SolidGeometry.draw(name,(float)b.getX(),(float)b.getY(),(float)b.getWidth(),(float)b.getHeight(),color);}
    }
    static double clamp(double v,double a,double b){return Math.max(a,Math.min(b,v));}
}
