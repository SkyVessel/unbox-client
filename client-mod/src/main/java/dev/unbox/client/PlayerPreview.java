package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Uses Minecraft's own inventory-player renderer; no second skin renderer is bundled. */
public final class PlayerPreview {
 private float rotation,followYaw,followPitch;
 private java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> titleSkin;
 public void rotate(float delta){rotation=(rotation-delta)%360f;}
 public void draw(GuiGraphicsExtractor g,int left,int top,int right,int bottom,int mouseX,int mouseY,boolean dragging){
  var player=Minecraft.getInstance().player;
  LivingEntityRenderState state=extractState(player,(left+right)/2f,(top+bottom)/2f,mouseX,mouseY,dragging);
  Quaternionf camera=new Quaternionf().rotateX((float)Math.toRadians(followPitch));
  Quaternionf pose=new Quaternionf().rotateZ((float)Math.PI).mul(camera);
  // Picture-in-picture entities use absolute GUI bounds and ignore the extractor pose.
  var a=g.pose().transformPosition(left,top,new org.joml.Vector2f());
  var b=g.pose().transformPosition(right,bottom,new org.joml.Vector2f());
  g.entity(state,(b.y-a.y)*.43f,new Vector3f(0,state.boundingBoxHeight/2f,0),pose,camera,Math.round(a.x),Math.round(a.y),Math.round(b.x),Math.round(b.y));
 }
 LivingEntityRenderState extractState(LivingEntity player,float cx,float cy,float mouseX,float mouseY,boolean dragging){
  // Match InventoryScreen's body/head follow and whole-model pitch. Freeze follow
  // while dragging so cursor movement does not fight the user's chosen rotation.
  if(!dragging){followYaw=(float)Math.atan((cx-mouseX)/40f)*20f;followPitch=(float)Math.atan((cy-mouseY)/40f)*20f;}
  LivingEntityRenderState living=player==null?titleState():state(player);
  living.bodyRot=180f+rotation+followYaw;living.yRot=followYaw;living.xRot=living.pose==Pose.FALL_FLYING?0:-followPitch;
  living.boundingBoxWidth/=living.scale;living.boundingBoxHeight/=living.scale;living.scale=1;
  living.shadowPieces.clear();living.outlineColor=0;
  return living;
 }
 private AvatarRenderState titleState(){
  var mc=Minecraft.getInstance();if(titleSkin==null)titleSkin=mc.getSkinManager().createLookup(mc.getGameProfile(),false);
  var state=new AvatarRenderState();state.skin=LocalSkin.preview(titleSkin.get());state.entityType=net.minecraft.world.entity.EntityType.PLAYER;
  state.boundingBoxWidth=.6f;state.boundingBoxHeight=1.8f;state.eyeHeight=1.62f;state.scale=state.ageScale=1;state.pose=Pose.STANDING;state.lightCoords=0xf000f0;
  state.showHat=state.showJacket=state.showLeftPants=state.showRightPants=state.showLeftSleeve=state.showRightSleeve=true;
  return state;
 }
 @SuppressWarnings({"rawtypes","unchecked"})private static LivingEntityRenderState state(LivingEntity entity){EntityRenderer renderer=Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);return (LivingEntityRenderState)renderer.createRenderState(entity,1f);}
}
