package dev.unbox.unpvp;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import com.google.gson.*;

/** Java 8 adaptation: immutable requests, one worker, at most one pending surface. */
final class SolidSurface implements AutoCloseable {
    static final class Op {
        final String kind,text;final int x,y,w,h,color,size;
        Op(String kind,String text,int x,int y,int w,int h,int color,int size){this.kind=kind;this.text=text;this.x=x;this.y=y;this.w=w;this.h=h;this.color=color;this.size=size;}
    }
    static final class Result {
        final String key;final SolidTexture.Pixels backing,ink,atlas;
        Result(String key,BufferedImage backing,BufferedImage ink,BufferedImage atlas){this(key,backing==null?null:new SolidTexture.Pixels(backing),new SolidTexture.Pixels(ink),atlas==null?null:new SolidTexture.Pixels(atlas));}
        Result(String key,SolidTexture.Pixels backing,SolidTexture.Pixels ink,SolidTexture.Pixels atlas){this.key=key;this.backing=backing;this.ink=ink;this.atlas=atlas;}
    }
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Unbox solid UI");t.setDaemon(true);return t;});
    private static final Map<String,VectorScene> scenes=new HashMap<>();
    private static final LinkedHashMap<String,Result> CACHE=new LinkedHashMap<>(8,.75f,true);
    private static long cachedBytes;static int cacheHits;
    private String request="",cacheKey="";
    private static long bytes(Result r){long n=0;for(SolidTexture.Pixels i:new SolidTexture.Pixels[]{r.backing,r.ink,r.atlas})if(i!=null)n+=(long)i.width*i.height*4;return n;}
    private static synchronized Result cached(String key){Result r=CACHE.get(key);if(r!=null)cacheHits++;return r;}
    private static synchronized void remember(String key,Result r){if(CACHE.containsKey(key))return;long size=bytes(r);if(size>128L*1024*1024)return;while(cachedBytes+size>128L*1024*1024&&!CACHE.isEmpty()){String first=CACHE.keySet().iterator().next();cachedBytes-=bytes(CACHE.remove(first));}CACHE.put(key,r);cachedBytes+=size;}
    private static String fingerprint(String scene,int w,int h,double zoom,List<Op> ops,List<Op> cards,int ch,int cw){StringBuilder b=new StringBuilder(scene).append(':').append(w).append(':').append(h).append(':').append(zoom).append(':').append(ch).append(':').append(cw);for(List<Op> list:Arrays.asList(ops,cards)){b.append('|');for(Op o:list)b.append(o.kind).append(':').append(o.text.length()).append(':').append(o.text).append(':').append(o.x).append(':').append(o.y).append(':').append(o.w).append(':').append(o.h).append(':').append(o.color).append(':').append(o.size).append(';');}return b.toString();}
    private static final Map<String,BufferedImage> ICONS=new ConcurrentHashMap<>();
    private static final Map<String,Shape> PATHS=loadPaths();
    private static final class FontHolder {static final Font FONT=loadFont();}
    private Future<Result> pending;private String shown="";private boolean closed;
    private static Map<String,Shape> loadPaths(){Map<String,Shape> out=new HashMap<>();try(InputStream in=SolidSurface.class.getResourceAsStream("/assets/unbox/solid/icons.json")){JsonObject j=new JsonParser().parse(new InputStreamReader(in,"UTF-8")).getAsJsonObject();for(Map.Entry<String,JsonElement> e:j.entrySet())out.put(e.getKey(),VectorScene.path(e.getValue().getAsString()));}catch(Exception e){throw new IllegalStateException(e);}for(String id:new String[]{"armor","potions","bobbing","hurtcam","classic","resources","lookback","itemphysics","tab","sneak","time","nickname","blur","hand","sprint","fps","cps","keystrokes","ping","crosshair","chat","scoreboard","particles","hotbar","performance","hitcolor"})try(InputStream svg=SolidSurface.class.getResourceAsStream("/assets/unbox/solid/icons/"+id+".svg")){if(svg!=null)out.put(id,VectorIcons.read(svg));}catch(Exception e){throw new IllegalStateException("Invalid SVG icon "+id,e);}out.put("viewmodel",out.get("hand"));out.put("fullbright",out.get("time"));return out;}
    static Shape iconShape(String name){return PATHS.get(name);}
    static Font font(){return FontHolder.FONT;}
    private static Font loadFont(){try(InputStream in=SolidSurface.class.getResourceAsStream("/assets/unbox/solid/outfit.ttf")){return Font.createFont(Font.TRUETYPE_FONT,in);}catch(Exception e){throw new IllegalStateException("Missing Outfit font",e);}}
    boolean ready(String key){return shown.equals(key);}
    Result poll(String key,String scene,int w,int h,double zoom,List<Op> ops,List<Op> cards,int cardHeight,int cardWidth){
        if(closed)return null;Result result=null;
        if(!request.equals(key)){request=key;cacheKey=fingerprint(scene,w,h,zoom,ops,cards,cardHeight,cardWidth);Result hit=cached(cacheKey);if(hit!=null){if(pending!=null)pending.cancel(true);pending=null;shown=key;return new Result(key,hit.backing,hit.ink,hit.atlas);}}
        if(pending!=null&&pending.isDone()){
            try{Result next=pending.get();if(next.key.equals(key)){result=next;shown=key;}}
            catch(Exception e){throw new IllegalStateException("UI surface failed",e);}finally{pending=null;}
        }
        if(!key.equals(shown)&&pending==null){final String memo=cacheKey;final List<Op> snapshot=new ArrayList<>(ops),cardSnapshot=new ArrayList<>(cards);pending=worker.submit(()->{
            BufferedImage back=scene.endsWith("-title")?null:scene(scene+"-background").render(w,h,zoom,true),ink=scene(scene).render(w,h,zoom,false);
            SolidLayout layout=new SolidLayout(scene,w,h,zoom);List<Op> mapped=new ArrayList<>();
            for(Op o:snapshot)mapped.add(new Op(o.kind,o.text,layout.x(o.x,o.y),layout.y(o.x,o.y),o.w,o.h,o.color,o.size));
            paint(ink,mapped,layout.pixels,0,0);BufferedImage atlas=null;
            if(cardHeight>0){double pixels=Math.min(layout.pixels,8192./cardHeight);atlas=new BufferedImage(Math.max(1,(int)Math.ceil(cardWidth*pixels)),Math.max(1,(int)Math.ceil(cardHeight*pixels)),BufferedImage.TYPE_INT_ARGB);paint(atlas,cardSnapshot,pixels,0,0);}
            Result rendered=new Result(key,back,ink,atlas);remember(memo,rendered);return rendered;
        });}return result;
    }
    private static synchronized VectorScene scene(String name)throws Exception{VectorScene s=scenes.get(name);if(s==null){s=new VectorScene(name);scenes.put(name,s);}return s;}
    static void paint(BufferedImage image,List<Op> ops,double scale,double x,double y){Graphics2D g=image.createGraphics();try{
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);g.translate(x,y);g.scale(scale,scale);
        for(Op o:ops){g.setColor(new Color(o.color,true));switch(o.kind){
            case "round":g.fill(new RoundRectangle2D.Float(o.x,o.y,o.w,o.h,o.size*2,o.size*2));break;
            case "text":case "center":{
                g.setFont((font().canDisplayUpTo(o.text)<0?font():new Font("Dialog",Font.PLAIN,o.size)).deriveFont((float)o.size));String t=o.text;
                while(t.length()>1&&g.getFontMetrics().stringWidth(t)>o.w)t=t.substring(0,t.offsetByCodePoints(t.length(),-Math.min(2,t.codePointCount(0,t.length()))))+"…";
                float tx=o.kind.equals("center")?o.x+(o.w-g.getFontMetrics().stringWidth(t))/2f:o.x;g.drawString(t,tx,o.y+o.size);break;
            }
            case "icon":{
                if(PATHS.containsKey(o.text)){Graphics2D v=(Graphics2D)g.create();try{v.translate(o.x,o.y);v.scale(o.w/65.,o.h/65.);v.fill(PATHS.get(o.text));}finally{v.dispose();}break;}
                BufferedImage icon=ICONS.get(o.text);if(icon==null){try(InputStream in=SolidSurface.class.getResourceAsStream("/assets/unbox/textures/ui/"+o.text+".png")){if(in!=null){icon=ImageIO.read(in);ICONS.put(o.text,icon);}}catch(IOException e){throw new IllegalStateException(e);}}
                if(icon!=null){BufferedImage tint=new BufferedImage(icon.getWidth(),icon.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D t=tint.createGraphics();try{t.drawImage(icon,0,0,null);t.setComposite(AlphaComposite.SrcIn);t.setColor(new Color(o.color,true));t.fillRect(0,0,tint.getWidth(),tint.getHeight());}finally{t.dispose();}g.drawImage(tint,o.x,o.y,o.w,o.h,null);}break;
            }
        }}
    }finally{g.dispose();}}
    public void close(){if(closed)return;closed=true;if(pending!=null)pending.cancel(true);worker.shutdownNow();pending=null;}
}
