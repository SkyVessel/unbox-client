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
 private static final ConcurrentHashMap<String,CompletableFuture<Identifier>> CACHE=new ConcurrentHashMap<>();
 private static final ExecutorService WORK=Executors.newFixedThreadPool(2,Thread.ofVirtual().name("unbox-cape-",0).factory());
 private static final HttpClient HTTP=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
 public static Identifier texture(String name){
  if(!name.matches("[A-Za-z0-9_]{1,16}"))return null;
  var existing=CACHE.get(name);if(existing!=null)return existing.getNow(null);
  if(CACHE.size()>=64)return null;
  var future=new CompletableFuture<Identifier>();if(CACHE.putIfAbsent(name,future)!=null)return null;
  WORK.submit(()->{
   try{var request=HttpRequest.newBuilder(URI.create("https://s.optifine.net/capes/"+name+".png")).timeout(Duration.ofSeconds(8)).GET().build();var response=HTTP.send(request,HttpResponse.BodyHandlers.ofInputStream());byte[] data;try(var in=response.body()){if(response.statusCode()!=200){future.complete(null);return;}data=in.readNBytes(262145);}if(data.length>262144){future.complete(null);return;}
    if(data.length<24){future.complete(null);return;}var header=java.nio.ByteBuffer.wrap(data);int w=header.getInt(16),h=header.getInt(20);if(w<1||h<1||w>1024||h>512){future.complete(null);return;}
    Minecraft.getInstance().execute(()->{try{var image=NativeImage.read(data);int width=64,height=32;while(width<image.getWidth())width*=2;while(height<image.getHeight())height*=2;
      if(width!=image.getWidth()||height!=image.getHeight()){var padded=new NativeImage(width,height,true);padded.fillRect(0,0,width,height,0);image.copyRect(padded,0,0,0,0,image.getWidth(),image.getHeight(),false,false);image.close();image=padded;}
      var id=Identifier.fromNamespaceAndPath("unbox","cape/"+name.toLowerCase(java.util.Locale.ROOT));Minecraft.getInstance().getTextureManager().register(id,new DynamicTexture(()->"Unbox OptiFine cape",image));future.complete(id);
    }catch(Exception e){future.complete(null);}});
   }catch(Exception e){future.complete(null);}
  });return null;
 }
}
