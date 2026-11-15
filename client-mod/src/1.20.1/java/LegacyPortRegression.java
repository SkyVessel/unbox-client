package dev.unbox.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix4f;
import java.util.List;

/** Explicit startup regression: offscreen pixels and key states, never a world or camera. */
final class LegacyPortRegression {
 static void run(Minecraft mc,List<String> checks)throws Exception{
  render(mc,checks);zoomKeys(mc,checks);
 }
 private static void render(Minecraft mc,List<String> checks)throws Exception{
  var window=mc.getWindow();int gw=window.getGuiScaledWidth(),gh=window.getGuiScaledHeight();
  var target=new TextureTarget(window.getWidth(),window.getHeight(),true,Minecraft.ON_OSX);
  var projection=new Matrix4f(RenderSystem.getProjectionMatrix());var sorting=RenderSystem.getVertexSorting();
  var model=RenderSystem.getModelViewStack();model.pushPose();model.setIdentity();model.translate(0,0,-11000);RenderSystem.applyModelViewMatrix();
  var tiles=new MapTileRenderer();
  try{
   target.setClearColor(0,0,0,1);target.clear(Minecraft.ON_OSX);target.bindWrite(true);
   RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,gw,gh,0,1000,21000),VertexSorting.ORTHOGRAPHIC_Z);
   var g=new GuiGraphics(mc,mc.renderBuffers().bufferSource());
   // The actual terrain upload/draw path, translated to the upper-right and HUD-scaled.
   var terrain=new MapTerrainStore(mc.gameDirectory.toPath().resolve("regression-map"),"fixture","fixture");
   for(int x=0;x<128;x++)for(int z=0;z<128;z++)terrain.capture(x,z,0xff38b46a);
   tiles.update(terrain,0,0,127,127);
   g.pose().pushPose();g.pose().translate(gw-90,20,0);g.pose().scale(.5f,.5f,1);
   g.fill(0,0,128,128,WorldMap.EMPTY);LegacyGui.enableScissor(g,0,0,128,128);
   tiles.draw(g,0,0,128,128,0,0,128,128);LegacyGui.disableScissor(g);g.pose().popPose();
   // Nested preview clipping must intersect in screen coordinates and restore its parent.
   g.pose().pushPose();g.pose().translate(40,30,0);g.pose().scale(.5f,.5f,1);
   LegacyGui.enableScissor(g,0,0,100,100);g.fill(-20,-20,120,120,0xff3366cc);
   g.pose().translate(40,40,0);LegacyGui.enableScissor(g,0,0,100,100);g.fill(-50,-50,150,150,0xffcc6633);
   LegacyGui.disableScissor(g);g.pose().popPose();LegacyGui.disableScissor(g);g.flush();
   try(var image=Screenshot.takeScreenshot(target)){
    double scale=window.getGuiScale();
    pixel(image,(int)((gw-58)*scale),(int)(52*scale),0xff38b46a,"translated minimap terrain");
    pixel(image,(int)(50*scale),(int)(40*scale),0xff3366cc,"parent clip");
    pixel(image,(int)(75*scale),(int)(65*scale),0xffcc6633,"nested clip");
    pixel(image,(int)(95*scale),(int)(85*scale),0xff000000,"outside clip");
    image.writeToFile(mc.gameDirectory.toPath().resolve("screenshots/legacy-clip-regression.png"));
   }
   checks.add("Native map texture visible at translated/scaled HUD coordinates");checks.add("Nested scaled UI clipping confines pixels and preserves parent bounds");
  }finally{tiles.clear();target.destroyBuffers();mc.getMainRenderTarget().bindWrite(true);RenderSystem.setProjectionMatrix(projection,sorting);model.popPose();RenderSystem.applyModelViewMatrix();}
 }
 private static void pixel(com.mojang.blaze3d.platform.NativeImage image,int x,int y,int argb,String label){
  int actual=image.getPixelRGBA(x,y);if(actual!=LegacyGui.abgr(argb))throw new IllegalStateException(label+": expected "+Integer.toHexString(LegacyGui.abgr(argb))+" got "+Integer.toHexString(actual));
 }
 private static void zoomKeys(Minecraft mc,List<String> checks){
  var zoom=UnboxClient.zoomKey;var save=mc.options.keySaveHotbarActivator;
  var original=InputConstants.getKey(zoom.saveString());boolean zoomDown=zoom.isDown(),saveDown=save.isDown();
  var c=InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_C);
  var f8=InputConstants.Type.KEYSYM.getOrCreate(org.lwjgl.glfw.GLFW.GLFW_KEY_F8);
  try{
   zoom.setKey(c);KeyMapping.resetMapping();KeyMapping.set(c,true);
   if(!zoom.isDown()||!save.isDown())throw new IllegalStateException("C must reach both Zoom and vanilla save-toolbar binding");
   KeyMapping.set(c,false);if(zoom.isDown())throw new IllegalStateException("Zoom key stuck after release");
   zoom.setKey(f8);KeyMapping.resetMapping();KeyMapping.set(c,true);if(zoom.isDown())throw new IllegalStateException("Previous Zoom key remains active after rebinding");
   KeyMapping.set(f8,true);if(!zoom.isDown())throw new IllegalStateException("Rebound Zoom key not delivered");
   KeyMapping.releaseAll();if(zoom.isDown())throw new IllegalStateException("Zoom key stuck after focus release");
   checks.add("Zoom default C, release, rebinding and focus release preserve vanilla key state");
  }finally{zoom.setKey(original);KeyMapping.resetMapping();zoom.setDown(zoomDown);save.setDown(saveDown);}
 }
}
