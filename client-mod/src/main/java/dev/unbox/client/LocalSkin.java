package dev.unbox.client;

import com.google.gson.*;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.*;
import java.nio.file.*;
import java.security.MessageDigest;

/** Verified launcher-published skin pixels for this local player only. */
public final class LocalSkin {
 private static final Path ROOT=Platform.gameDir(),PNG=ROOT.resolve("unbox-local-skin.png"),MANIFEST=ROOT.resolve("unbox-skin-library.json");
 private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("unbox","local_skin");private static long checked,sequence=-1;private static String owner="";private static PlayerSkin skin;
 public static PlayerSkin replace(PlayerSkin original,net.minecraft.client.player.AbstractClientPlayer player){
  var mc=Minecraft.getInstance();if(player!=mc.player)return original;poll(mc);return skin==null?original:new PlayerSkin(skin.body(),original.cape(),original.elytra(),skin.model(),false);
 }
 public static PlayerSkin preview(PlayerSkin original){poll(Minecraft.getInstance());return skin==null?original:new PlayerSkin(skin.body(),original.cape(),original.elytra(),skin.model(),false);}
 private static void poll(Minecraft mc){String current=mc.getUser().getProfileId().toString();if(!owner.equals(current)){clear(mc);owner=current;sequence=-1;}long now=System.currentTimeMillis();if(now-checked<500)return;checked=now;try{
  if(!Files.isRegularFile(MANIFEST)||!Files.isRegularFile(PNG)||Files.size(MANIFEST)>256000||Files.size(PNG)>1048576){clear(mc);sequence=-1;return;}JsonObject m=JsonParser.parseString(Files.readString(MANIFEST)).getAsJsonObject();long next=m.get("sequence").getAsLong();
  String uuid=text(m,"accountUuid").replace("-","");String name=text(m,"accountName");if(!uuid.isEmpty()&&!uuid.equals(current.replace("-",""))){reject(mc,next);return;}if(uuid.isEmpty()&&!name.equals(mc.getUser().getName())){reject(mc,next);return;}if(next==sequence)return;
  JsonObject active=m.getAsJsonObject("active");if(active==null){reject(mc,next);return;}byte[] bytes=Files.readAllBytes(PNG);if(!hex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(text(active,"sha256"))){reject(mc,next);return;}NativeImage image=NativeImage.read(bytes);if(image.getWidth()!=64||image.getHeight()!=64){image.close();reject(mc,next);return;}
  mc.getTextureManager().register(TEXTURE,new DynamicTexture(()->"Unbox local skin",image));var model=text(active,"model").equals("slim")?PlayerModelType.SLIM:PlayerModelType.WIDE;skin=new PlayerSkin(new ClientAsset.ResourceTexture(TEXTURE,TEXTURE),null,null,model,false);sequence=next;
  }catch(Exception ignored){clear(mc);}}
 private static void reject(Minecraft mc,long next){clear(mc);sequence=next;}private static void clear(Minecraft mc){if(skin!=null)mc.getTextureManager().release(TEXTURE);skin=null;}
 private static String text(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}private static String hex(byte[] b){var s=new StringBuilder();for(byte v:b)s.append(String.format("%02x",v));return s.toString();}
}
