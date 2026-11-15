package dev.unbox.unpvp;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.*;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.*;
import org.lwjgl.opengl.*;

/** Conservative model-only occlusion. Query/name-preservation approach adapted from Patcher (CC BY-NC-SA 4.0).
 * Names use the original renderer; unready, stale or moving-camera results always render the model.
 */
public final class ModelOcclusion {
 public interface Names {void unbox$renderName(EntityLivingBase entity,double x,double y,double z);}
 public static final ModelOcclusion INSTANCE=new ModelOcclusion();
 public static boolean disabled=Boolean.getBoolean("unpvp.culling.disabled");
 public static long tested,skipped,names;public static boolean active;
 private static final Map<EntityLivingBase,Query> queries=new IdentityHashMap<>();
 private static World world;private static Entity camera;private static long generation,frame;private static int budget;
 private static double cx,cy,cz;private static float yaw,pitch,fov;private static int perspective,w,h;
 private static final java.nio.FloatBuffer matrix=org.lwjgl.BufferUtils.createFloatBuffer(16);private static final float[] viewMatrix=new float[16],projectionMatrix=new float[16];
 private static boolean matrixChanged(int type,float[] previous){matrix.clear();GL11.glGetFloat(type,matrix);boolean changed=false;for(int i=0;i<16;i++){float value=matrix.get(i);if(value!=previous[i])changed=true;previous[i]=value;}return changed;}
 private static Method shaders;private static boolean searchedShaders;
 private static final class Query {int id;long generation,issued,seen;boolean hidden;AxisAlignedBB box;}
 private ModelOcclusion(){}
 public static void register(){MinecraftForge.EVENT_BUS.register(INSTANCE);}
 private static boolean shaders(){if(!searchedShaders){searchedShaders=true;try{shaders=Class.forName("Config").getMethod("isShaders");}catch(ReflectiveOperationException ignored){}}try{return shaders!=null&&(Boolean)shaders.invoke(null);}catch(ReflectiveOperationException e){return true;}}
 public static boolean handles(Entity e){return !disabled&&e instanceof EntityLivingBase&&!(e instanceof EntityArmorStand)&&e!=Minecraft.getMinecraft().getRenderViewEntity();}
 public static void begin(float partial){Minecraft mc=Minecraft.getMinecraft();active=false;frame++;budget=64;
  if(world!=mc.theWorld){clear();world=mc.theWorld;}
  if(disabled||world==null||mc.thePlayer==null||mc.thePlayer.isSpectator()||shaders()||!GLContext.getCapabilities().OpenGL15){if(!queries.isEmpty())clear();return;}
  Entity view=mc.getRenderViewEntity();if(view==null)return;
  double x=view.lastTickPosX+(view.posX-view.lastTickPosX)*partial,y=view.lastTickPosY+(view.posY-view.lastTickPosY)*partial,z=view.lastTickPosZ+(view.posZ-view.lastTickPosZ)*partial;
  float ry=view.prevRotationYaw+(view.rotationYaw-view.prevRotationYaw)*partial,rp=view.prevRotationPitch+(view.rotationPitch-view.prevRotationPitch)*partial;
  // Include the actual camera matrices: zoom easing, view bobbing and portal effects can change them while position/FOV settings stay still.
  boolean transformed=matrixChanged(GL11.GL_MODELVIEW_MATRIX,viewMatrix)|matrixChanged(GL11.GL_PROJECTION_MATRIX,projectionMatrix);
  boolean moving=transformed||view!=camera||Math.abs(x-cx)+Math.abs(y-cy)+Math.abs(z-cz)>.0001||ry!=yaw||rp!=pitch||fov!=mc.gameSettings.fovSetting||perspective!=mc.gameSettings.thirdPersonView||w!=mc.displayWidth||h!=mc.displayHeight;if(moving){generation++;camera=view;cx=x;cy=y;cz=z;yaw=ry;pitch=rp;fov=mc.gameSettings.fovSetting;perspective=mc.gameSettings.thirdPersonView;w=mc.displayWidth;h=mc.displayHeight;}
  long now=System.nanoTime();Iterator<Map.Entry<EntityLivingBase,Query>> it=queries.entrySet().iterator();while(it.hasNext()){Map.Entry<EntityLivingBase,Query> entry=it.next();Query q=entry.getValue();if(entry.getKey().isDead||entry.getKey().worldObj!=world||frame-q.seen>120){release(q);it.remove();continue;}if(q.id!=0&&GL15.glGetQueryObjecti(q.id,GL15.GL_QUERY_RESULT_AVAILABLE)!=0){q.hidden=GL15.glGetQueryObjecti(q.id,GL15.GL_QUERY_RESULT)==0&&q.generation==generation;release(q);}if(q.generation!=generation||now-q.issued>100_000_000L)q.hidden=false;}
  active=!moving&&mc.thePlayer.hurtTime==0;
 }
 public static void end(){active=false;}
 public static void idle(){if(Minecraft.getMinecraft().theWorld==null&&!queries.isEmpty()){clear();world=null;}}
 public static void invalidate(){generation++;}
 public static void clear(){for(Query q:queries.values())release(q);queries.clear();generation++;camera=null;active=false;}
 private static void release(Query q){if(q.id!=0){GL15.glDeleteQueries(q.id);q.id=0;}}
 public static int pending(){return queries.size();}
 public static boolean hidden(EntityLivingBase entity){Query q=queries.get(entity);return q!=null&&q.hidden&&q.generation==generation;}
 private static boolean same(AxisAlignedBB a,AxisAlignedBB b){return a!=null&&a.minX==b.minX&&a.minY==b.minY&&a.minZ==b.minZ&&a.maxX==b.maxX&&a.maxY==b.maxY&&a.maxZ==b.maxZ;}
 @SubscribeEvent(priority=EventPriority.LOWEST) public void render(RenderLivingEvent.Pre<EntityLivingBase> event){
  Minecraft mc=Minecraft.getMinecraft();EntityLivingBase entity=event.entity;
  if(!active||!handles(entity)||entity.worldObj!=world||entity.isInvisibleToPlayer(mc.thePlayer)||entity.isRiding()||entity.riddenByEntity!=null||entity==mc.thePlayer||!(event.renderer instanceof Names))return;
  // Do not create queries for GUI previews, mirrors or another mod's framebuffer.
  if(GL11.glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)!=mc.getFramebuffer().framebufferObject)return;
  AxisAlignedBB box=entity.getEntityBoundingBox();Query q=queries.get(entity);if(q==null){if(queries.size()>=512)return;q=new Query();queries.put(entity,q);}q.seen=frame;
  boolean moved=!same(q.box,box);if(moved){q.hidden=false;release(q);q.box=box;}
  boolean hidden=q.hidden&&q.generation==generation;long now=System.nanoTime();
  if(q.id==0&&budget>0&&(moved||q.generation!=generation||now-q.issued>=(q.hidden?25_000_000L:150_000_000L))){budget--;q.id=GL15.glGenQueries();if(q.id==0)return;q.generation=generation;q.issued=now;tested++;drawQuery(q.id,box.expand(.2,.2,.2).offset(event.x-entity.posX,event.y-entity.posY,event.z-entity.posZ));}
  if(hidden){event.setCanceled(true);skipped++;((Names)event.renderer).unbox$renderName(entity,event.x,event.y,event.z);names++;}
 }
 private static void drawQuery(int id,AxisAlignedBB b){GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);try{
  GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glDisable(GL11.GL_CULL_FACE);GL11.glDisable(GL11.GL_LIGHTING);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthFunc(GL11.GL_LEQUAL);GL11.glDepthMask(false);GL11.glColorMask(false,false,false,false);
  GL15.glBeginQuery(GL15.GL_SAMPLES_PASSED,id);try{WorldRenderer r=Tessellator.getInstance().getWorldRenderer();r.begin(GL11.GL_QUAD_STRIP,DefaultVertexFormats.POSITION);
   r.pos(b.maxX,b.maxY,b.maxZ).endVertex();r.pos(b.maxX,b.maxY,b.minZ).endVertex();r.pos(b.minX,b.maxY,b.maxZ).endVertex();r.pos(b.minX,b.maxY,b.minZ).endVertex();r.pos(b.minX,b.minY,b.maxZ).endVertex();r.pos(b.minX,b.minY,b.minZ).endVertex();r.pos(b.minX,b.maxY,b.minZ).endVertex();r.pos(b.maxX,b.maxY,b.minZ).endVertex();r.pos(b.maxX,b.minY,b.minZ).endVertex();r.pos(b.maxX,b.maxY,b.maxZ).endVertex();r.pos(b.maxX,b.minY,b.maxZ).endVertex();r.pos(b.minX,b.maxY,b.maxZ).endVertex();r.pos(b.minX,b.minY,b.maxZ).endVertex();r.pos(b.minX,b.minY,b.minZ).endVertex();r.pos(b.maxX,b.minY,b.maxZ).endVertex();r.pos(b.maxX,b.minY,b.minZ).endVertex();Tessellator.getInstance().draw();}finally{GL15.glEndQuery(GL15.GL_SAMPLES_PASSED);}
 }finally{GL11.glPopAttrib();}}
}
