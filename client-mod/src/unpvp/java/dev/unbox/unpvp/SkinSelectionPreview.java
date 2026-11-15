package dev.unbox.unpvp;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import java.nio.file.*;
/** Decode bounded launcher previews off the render thread; retry transient missing files. */
final class SkinSelectionPreview implements AutoCloseable {
 private String key="",wanted="";private ResourceLocation texture;private boolean loading,closed;private long next;
 ResourceLocation get(String id){closed=false;
  if(!id.matches("[a-f0-9]{64}"))return null;
  wanted=id;if(id.equals(key))return texture;if(loading||System.currentTimeMillis()<next)return null;
  loading=true;next=System.currentTimeMillis()+500;
  SocialBridge.background(()->{java.awt.image.BufferedImage image=null;try{
   Path file=SocialBridge.root().resolve("unbox-skin-previews").resolve(id+".png");
   if(Files.isRegularFile(file)&&Files.size(file)<=1048576){byte[] bytes=Files.readAllBytes(file);if(bytes.length>=24&&java.nio.ByteBuffer.wrap(bytes).getInt(16)==64&&java.nio.ByteBuffer.wrap(bytes).getInt(20)==64)image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));}
  }catch(Exception ignored){}final java.awt.image.BufferedImage decoded=image;SocialBridge.main(()->{loading=false;if(closed||!wanted.equals(id)||decoded==null)return;if(texture!=null)Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);texture=Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("unbox-selected-skin",new net.minecraft.client.renderer.texture.DynamicTexture(decoded));key=id;});});return null;
 }
 public void close(){closed=true;if(texture!=null)Minecraft.getMinecraft().getTextureManager().deleteTexture(texture);texture=null;}
}
