package dev.unbox.unpvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.util.ResourceLocation;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import org.lwjgl.opengl.GL11;
/** Bundled branding has priority over resource packs; three textures are cached with mipmaps. */
public final class BadgeTextures {
 private static final ResourceLocation[] TEXTURES=new ResourceLocation[3];
 private static ResourceLocation texture(int index){if(TEXTURES[index]!=null)return TEXTURES[index];String file=index==0?"ui/badge":"font/"+NameBadges.LOGOS[index];try(InputStream in=BadgeTextures.class.getResourceAsStream("/assets/unbox/textures/"+file+".png")){BufferedImage original=javax.imageio.ImageIO.read(in),image=new BufferedImage(128,128,BufferedImage.TYPE_INT_ARGB);java.awt.Graphics2D g=image.createGraphics();g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);g.drawImage(original,0,0,128,128,null);g.dispose();DynamicTexture texture=new DynamicTexture(image);ResourceLocation id=Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("unbox-badge-"+index,texture);TextureUtil.allocateTextureImpl(texture.getGlTextureId(),7,128,128);int[][] levels=new int[8][];levels[0]=image.getRGB(0,0,128,128,null,0,128);TextureUtil.uploadTextureMipmap(TextureUtil.generateMipmapData(7,128,levels),128,128,0,0,true,false);TEXTURES[index]=id;return id;}catch(Exception e){throw new IllegalStateException("Could not prepare Unbox badge",e);}}
 public static float glyph(int index,float x,float y,float alpha){float h=index==0?9:index==1?13:14;float top=index==0?0:index==1?-3:-5;draw(index,x,y+top,h,h,alpha);NameBadges.glyphs++;return h+1;}
 public static int width(int index){return index==0?10:index==1?14:15;}
 static void icon(String id,float x,float y,float size){int index=java.util.Arrays.asList(NameBadges.LOGOS).indexOf(id);if(index<0)index=0;float h=size*(index==0?1:index==1?1.45f:1.586f);draw(index,x+(size-h)/2,y+size-h,h,h,Painter.opacity);}
 private static void draw(int index,float x,float y,float w,float h,float alpha){Minecraft.getMinecraft().getTextureManager().bindTexture(texture(index));GlStateManager.color(1,1,1,alpha);GL11.glBegin(GL11.GL_QUADS);GL11.glTexCoord2f(0,0);GL11.glVertex3f(x,y,0);GL11.glTexCoord2f(0,1);GL11.glVertex3f(x,y+h,0);GL11.glTexCoord2f(1,1);GL11.glVertex3f(x+w,y+h,0);GL11.glTexCoord2f(1,0);GL11.glVertex3f(x+w,y,0);GL11.glEnd();}
}
