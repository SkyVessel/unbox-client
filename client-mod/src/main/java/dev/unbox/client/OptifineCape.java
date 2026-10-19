package dev.unbox.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.NativeImage;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;

public final class OptifineCape {
 private static final class Entry {
  final CompletableFuture<Identifier> texture=new CompletableFuture<>();
  volatile long retryAt=Long.MAX_VALUE,lastUsed=System.nanoTime();
 }
 private static final ConcurrentHashMap<String,Entry> CACHE=new ConcurrentHashMap<>();
 private static final ExecutorService WORK=Executors.newFixedThreadPool(2,Thread.ofVirtual().name("unbox-cape-",0).factory());
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 private static final System.Logger LOG=System.getLogger("Unbox/OptifineCape");
 private static final long RETRY=Duration.ofSeconds(30).toNanos(),MISSING=Duration.ofMinutes(5).toNanos(),IDLE=Duration.ofMinutes(5).toNanos();
 private static Object world;private static int ticks;
 /** Called on the client tick, so texture disposal never happens midway through a render. */
 public static void tick(){
  var mc=Minecraft.getInstance();boolean clear=world!=mc.level||!UnboxClient.enabled("cape");world=mc.level;
  if(!clear&&++ticks%600!=0)return;
  long now=System.nanoTime();
  CACHE.forEach((name,entry)->{if((clear||entry.texture.isDone()&&now-entry.lastUsed>IDLE)&&CACHE.remove(name,entry)){
   var id=entry.texture.getNow(null);if(id!=null)mc.getTextureManager().release(id);
  }});
 }
 public static Identifier texture(String name){
  if(name==null||!name.matches("[A-Za-z0-9_]{1,16}"))return null;
  long now=System.nanoTime();var existing=CACHE.get(name);
  if(existing!=null){existing.lastUsed=now;if(!existing.texture.isDone()||now<existing.retryAt)return existing.texture.getNow(null);if(!CACHE.remove(name,existing))return null;}
  var entry=new Entry();if(CACHE.putIfAbsent(name,entry)!=null)return null;
  WORK.submit(()->{
   if(CACHE.get(name)!=entry){entry.texture.complete(null);return;}
   // OptiFine's public cape service uses HTTP. No account credentials or redirects are used.
   try{var request=HttpRequest.newBuilder(URI.create("http://s.optifine.net/capes/"+name+".png")).timeout(Duration.ofSeconds(8)).GET().build();var response=HTTP.send(request,HttpResponse.BodyHandlers.ofInputStream());byte[] data;try(var in=response.body()){
     if(response.statusCode()==404){finish(entry,MISSING);return;}
     if(response.statusCode()!=200)throw new java.io.IOException("HTTP "+response.statusCode());
     // The request timeout ends at the response headers for an InputStream body.
     var close=CompletableFuture.runAsync(()->{try{in.close();}catch(java.io.IOException ignored){}},CompletableFuture.delayedExecutor(8,TimeUnit.SECONDS));
     try{data=in.readNBytes(262145);}finally{close.cancel(false);}
    }
    if(data.length<24||data.length>262144)throw new java.io.IOException("Invalid cape size");
    var header=java.nio.ByteBuffer.wrap(data);if(header.getLong()!=0x89504e470d0a1a0aL||header.getInt()!=13||header.getInt()!=0x49484452)throw new java.io.IOException("Invalid PNG header");
    int w=header.getInt(),h=header.getInt();if(w<1||h<1||w>1024||h>512)throw new java.io.IOException("Invalid cape dimensions");
    Minecraft.getInstance().execute(()->{
     if(CACHE.get(name)!=entry){entry.texture.complete(null);return;}
     NativeImage image=null;
     try{image=NativeImage.read(data);int width=64,height=32;while(width<image.getWidth()||height<image.getHeight()){width*=2;height*=2;}
      if(width!=image.getWidth()||height!=image.getHeight()){var padded=new NativeImage(width,height,true);try{padded.fillRect(0,0,width,height,0);image.copyRect(padded,0,0,0,0,image.getWidth(),image.getHeight(),false,false);}catch(Exception e){padded.close();throw e;}image.close();image=padded;}
      // Preserve case: the service path is case-sensitive and entries must not share a GPU texture.
      var encoded=java.util.HexFormat.of().formatHex(name.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      var id=Identifier.fromNamespaceAndPath("unbox","cape/"+encoded);Minecraft.getInstance().getTextureManager().register(id,new DynamicTexture(()->"Unbox OptiFine cape",image));image=null;entry.texture.complete(id);
     }catch(Exception e){failed(name,entry,e);}finally{if(image!=null)image.close();}
    });
   }catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();failed(name,entry,e);}
  });return null;
 }
 private static void finish(Entry entry,long delay){entry.retryAt=System.nanoTime()+delay;entry.texture.complete(null);}
 private static void failed(String name,Entry entry,Exception e){LOG.log(System.Logger.Level.WARNING,"Cape download for "+name+" failed; retry available in 30 seconds: "+e.getClass().getSimpleName()+": "+e.getMessage());finish(entry,RETRY);}
}
