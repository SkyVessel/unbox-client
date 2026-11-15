package dev.unbox.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.*;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
public final class LegacyGui {
 private static final PanoramaRenderer PANORAMA=new PanoramaRenderer(new CubeMap(new ResourceLocation("textures/gui/title/background/panorama")));
 private static PostChain blur;private static Object resources;private static int width,height;
 public static void panorama(float delta){PANORAMA.render(delta,1);}
 public static void blur(){var mc=Minecraft.getInstance();try{if(blur==null||resources!=mc.getResourceManager()){if(blur!=null)blur.close();blur=new PostChain(mc.getTextureManager(),mc.getResourceManager(),mc.getMainRenderTarget(),new ResourceLocation("shaders/post/blur.json"));resources=mc.getResourceManager();width=height=0;}int w=mc.getWindow().getWidth(),h=mc.getWindow().getHeight();if(w!=width||h!=height){blur.resize(w,h);width=w;height=h;}blur.process(mc.getFrameTime());mc.getMainRenderTarget().bindWrite(false);}catch(java.io.IOException e){throw new IllegalStateException("Could not load native GUI blur",e);}}
 public static void blit(GuiGraphics g,ResourceLocation id,int x,int y,float u,float v,int w,int h,int rw,int rh,int tw,int th){blit(g,id,x,y,u,v,w,h,rw,rh,tw,th,0xffffffff);}
 public static int abgr(int c){return(c&0xff00ff00)|((c>>>16)&255)|((c&255)<<16);}
 /** 1.20's scissor API accepts screen coordinates, unlike the shared renderer. */
 public static void enableScissor(GuiGraphics g,int left,int top,int right,int bottom){
  var m=g.pose().last().pose();float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
  for(int x:new int[]{left,right})for(int y:new int[]{top,bottom}){var p=m.transformPosition(x,y,0,new org.joml.Vector3f());minX=Math.min(minX,p.x);minY=Math.min(minY,p.y);maxX=Math.max(maxX,p.x);maxY=Math.max(maxY,p.y);}
  g.flush();g.enableScissor((int)Math.floor(minX),(int)Math.floor(minY),(int)Math.ceil(maxX),(int)Math.ceil(maxY));
 }
 public static void disableScissor(GuiGraphics g){g.flush();g.disableScissor();}
 public static void blit(GuiGraphics g,ResourceLocation id,int x,int y,float u,float v,int w,int h,int rw,int rh,int tw,int th,int color){quad(g,id,x,y,w,h,u/tw,(u+rw)/tw,v/th,(v+rh)/th,color);}
 public static void quad(GuiGraphics g,ResourceLocation id,float x,float y,float w,float h,float u0,float u1,float v0,float v1,int color){
  g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(GameRenderer::getPositionTexColorShader);RenderSystem.setShaderTexture(0,id);RenderSystem.setShaderColor(1,1,1,1);
  var b=Tesselator.getInstance().getBuilder();b.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);var m=g.pose().last().pose();
  b.vertex(m,x,y,0).uv(u0,v0).color(color).endVertex();b.vertex(m,x,y+h,0).uv(u0,v1).color(color).endVertex();b.vertex(m,x+w,y+h,0).uv(u1,v1).color(color).endVertex();b.vertex(m,x+w,y,0).uv(u1,v0).color(color).endVertex();BufferUploader.drawWithShader(b.end());
 }
}
