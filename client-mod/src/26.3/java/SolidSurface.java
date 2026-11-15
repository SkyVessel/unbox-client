package dev.unbox.client;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;

/** Immutable paint requests. One worker, one pending render; stale results never reach the GPU. */
final class SolidSurface implements AutoCloseable {
    record Op(String kind,String text,int x,int y,int w,int h,int color,int size) {}
    record Result(String key,BufferedImage backing,BufferedImage ink,BufferedImage atlas) {}
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Unbox solid UI");t.setDaemon(true);return t;});
    private final Map<String,VectorScene> scenes=new HashMap<>();
    private static final Map<String,BufferedImage> ICONS=new ConcurrentHashMap<>();
    private static final class FontHolder { static final Font FONT=loadFont(); }
    private static final Map<String,Shape> PATHS=loadPaths();
    private static Map<String,Shape> loadPaths(){Map<String,Shape> out=new HashMap<>();try(var in=SolidSurface.class.getResourceAsStream("/assets/unbox/solid/icons.json")){var j=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject();j.entrySet().forEach(e->out.put(e.getKey(),VectorScene.path(e.getValue().getAsString())));}catch(Exception e){throw new IllegalStateException(e);}return out;}
    private static final long LIMIT=128L*1024*1024;
    private static long bytes;
    private static final LinkedHashMap<String,Result> CACHE=new LinkedHashMap<>(8,.75f,true);
    private static long size(BufferedImage image){return image==null?0:4L*image.getWidth()*image.getHeight();}
    private static long size(Result r){return size(r.backing)+size(r.ink)+size(r.atlas);}
    private static synchronized Result cached(String key){return CACHE.get(key);}
    private static synchronized Result remember(Result result){
        if(size(result)>LIMIT)return result;Result old=CACHE.put(result.key,result);if(old!=null)bytes-=size(old);bytes+=size(result);
        var iterator=CACHE.values().iterator();while(bytes>LIMIT&&iterator.hasNext()){bytes-=size(iterator.next());iterator.remove();}return result;
    }
    private Future<Result> pending;private String shown="";private boolean closed;
    private static Font loadFont(){try(var in=SolidSurface.class.getResourceAsStream("/assets/unbox/solid/outfit.ttf")){return Font.createFont(Font.TRUETYPE_FONT,in);}catch(Exception e){throw new IllegalStateException("Missing Outfit font",e);}}
    boolean ready(String key){return shown.equals(key);}
    Result poll(String key,String scene,int w,int h,double zoom,List<Op> ops,List<Op> cards,int cardHeight,int cardWidth){
        if(closed)return null;Result result=null;
        if(!key.equals(shown)){Result hit=cached(key);if(hit!=null){shown=key;return hit;}}
        if(pending!=null&&pending.isDone()){
            try{Result next=pending.get();if(next.key.equals(key)){result=next;shown=key;}}
            catch(Exception e){throw new IllegalStateException("UI surface failed",e);}finally{pending=null;}
        }
        if(!key.equals(shown)&&pending==null){List<Op> snapshot=List.copyOf(ops),cardSnapshot=List.copyOf(cards);pending=worker.submit(()->{
            BufferedImage back=scene.endsWith("-title")?null:scene(scene+"-background").render(w,h,zoom,true),ink=scene(scene).render(w,h,zoom,false);
            SolidLayout layout=new SolidLayout(scene,w,h,zoom);double scale=layout.pixels;
            paint(ink,snapshot.stream().map(o->new Op(o.kind,o.text,layout.x(o.x,o.y),layout.y(o.x,o.y),o.w,o.h,o.color,o.size)).toList(),scale,0,0);
            BufferedImage atlas=null;
            if(cardHeight>0){double pixels=Math.min(scale,8192./cardHeight);atlas=new BufferedImage(Math.max(1,(int)Math.ceil(cardWidth*pixels)),Math.max(1,(int)Math.ceil(cardHeight*pixels)),BufferedImage.TYPE_INT_ARGB);paint(atlas,cardSnapshot,pixels,0,0);}
            return remember(new Result(key,back,ink,atlas));
        });}return result;
    }
    private VectorScene scene(String name)throws Exception{VectorScene s=scenes.get(name);if(s==null){s=new VectorScene(name);scenes.put(name,s);}return s;}
    static void paint(BufferedImage image,List<Op> ops,double scale,double x,double y){Graphics2D g=image.createGraphics();try{
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.translate(x,y);g.scale(scale,scale);
        for(Op o:ops){g.setColor(new Color(o.color,true));switch(o.kind){
            case "bottom"->{Shape old=g.getClip();g.clip(new Rectangle2D.Float(o.x,o.y+145,o.w,o.h-145));g.fill(new RoundRectangle2D.Float(o.x,o.y,o.w,o.h,60,60));g.setClip(old);}
            case "round"->g.fill(new RoundRectangle2D.Float(o.x,o.y,o.w,o.h,o.size*2,o.size*2));
            case "text","center"->{g.setFont((FontHolder.FONT.canDisplayUpTo(o.text)<0?FontHolder.FONT:new Font("Dialog",Font.PLAIN,o.size)).deriveFont((float)o.size));String t=o.text;while(t.length()>1&&g.getFontMetrics().stringWidth(t)>o.w)t=t.substring(0,t.length()-2)+"…";float tx=o.kind.equals("center")?o.x+(o.w-g.getFontMetrics().stringWidth(t))/2f:o.x;g.drawString(t,tx,o.y+o.size);}
            case "icon"->{if(PATHS.containsKey(o.text)){Graphics2D v=(Graphics2D)g.create();v.translate(o.x,o.y);v.scale(o.w/65.,o.h/65.);v.fill(PATHS.get(o.text));v.dispose();break;}BufferedImage icon=ICONS.computeIfAbsent(o.text,id->{try(var in=SolidSurface.class.getResourceAsStream("/assets/unbox/textures/ui/"+id+".png")){return in==null?null:ImageIO.read(in);}catch(Exception e){return null;}});if(icon!=null){BufferedImage tint=new BufferedImage(icon.getWidth(),icon.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D t=tint.createGraphics();t.drawImage(icon,0,0,null);t.setComposite(AlphaComposite.SrcIn);t.setColor(new Color(o.color,true));t.fillRect(0,0,tint.getWidth(),tint.getHeight());t.dispose();g.drawImage(tint,o.x,o.y,o.w,o.h,null);}}
        }}
    }finally{g.dispose();}}
    @Override public void close(){if(closed)return;closed=true;if(pending!=null)pending.cancel(true);worker.shutdownNow();pending=null;/* Worker owns its scene map until termination. */}
}
