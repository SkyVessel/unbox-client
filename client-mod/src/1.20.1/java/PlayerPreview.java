package dev.unbox.client;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import org.joml.Quaternionf;
public final class PlayerPreview {
 private float rotation,followYaw,followPitch;
 public void rotate(float delta){rotation=(rotation-delta)%360f;}
 public void draw(GuiGraphics g,int left,int top,int right,int bottom,int mouseX,int mouseY,boolean dragging){
  var mc=Minecraft.getInstance();if(!dragging){followYaw=(float)Math.atan(((left+right)/2f-mouseX)/40f)*20;followPitch=(float)Math.atan(((top+bottom)/2f-mouseY)/40f)*20;}
  int x=(left+right)/2,y=bottom-8,size=Math.round((bottom-top)*.43f);var camera=new Quaternionf().rotateX((float)Math.toRadians(followPitch));var pose=new Quaternionf().rotateZ((float)Math.PI).mul(camera);
  var p=mc.player;if(p!=null){float body=p.yBodyRot,yaw=p.getYRot(),pitch=p.getXRot(),head=p.yHeadRot,old=p.yHeadRotO;try{p.yBodyRot=180+rotation+followYaw;p.setYRot(180+rotation+followYaw*2);p.setXRot(-followPitch);p.yHeadRot=p.getYRot();p.yHeadRotO=p.yHeadRot;InventoryScreen.renderEntityInInventory(g,x,y,size,pose,camera,p);}finally{p.yBodyRot=body;p.setYRot(yaw);p.setXRot(pitch);p.yHeadRot=head;p.yHeadRotO=old;}return;}
  var local=LocalSkin.current();var profile=mc.getUser().getGameProfile();var texture=local!=null?local.texture():mc.getSkinManager().getInsecureSkinLocation(profile);boolean slim=local!=null?local.model().equals("slim"):DefaultPlayerSkin.getSkinModelName(profile.getId()).equals("slim");
  var model=new PlayerModel<net.minecraft.world.entity.LivingEntity>(mc.getEntityModels().bakeLayer(slim?ModelLayers.PLAYER_SLIM:ModelLayers.PLAYER),slim);model.setAllVisible(true);model.head.yRot=(float)Math.toRadians(followYaw);model.head.xRot=(float)Math.toRadians(-followPitch);model.hat.copyFrom(model.head);
  g.flush();g.pose().pushPose();try{g.pose().translate(x,y-size*1.5f,100);g.pose().scale(size,size,size);g.pose().mulPose(new Quaternionf().rotateX((float)Math.toRadians(-followPitch)).rotateY((float)Math.toRadians(rotation+followYaw)));com.mojang.blaze3d.platform.Lighting.setupForEntityInInventory();var buffers=mc.renderBuffers().bufferSource();model.renderToBuffer(g.pose(),buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),0xf000f0,OverlayTexture.NO_OVERLAY,1,1,1,1);buffers.endBatch();}finally{g.pose().popPose();com.mojang.blaze3d.platform.Lighting.setupFor3DItems();}
 }
}
