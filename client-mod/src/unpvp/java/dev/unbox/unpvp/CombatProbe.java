package dev.unbox.unpvp;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

/** Isolated combat rendering/packet checks. Never joins a public server. */
final class CombatProbe {
 static void check(boolean ok,String what){if(!ok)throw new IllegalStateException(what);System.out.println("[unpvp-combat] PASS "+what);}
 static void run()throws Exception{
  Minecraft mc=Minecraft.getMinecraft();EntityOtherPlayerMP target=new EntityOtherPlayerMP(mc.theWorld,new GameProfile(new UUID(0,700),"CombatTarget"));mc.theWorld.addEntityToWorld(-700,target);
  try{
   mc.getNetHandler().handleEntityVelocity(new S12PacketEntityVelocity(target.getEntityId(),.5,.4,-.25));
   check(target.motionX==.5&&target.motionY==.4&&target.motionZ==-.25,"Remote velocity packet applies all axes without scaling");
   double x=mc.thePlayer.motionX,y=mc.thePlayer.motionY,z=mc.thePlayer.motionZ;
   try{mc.getNetHandler().handleEntityVelocity(new S12PacketEntityVelocity(mc.thePlayer.getEntityId(),-.25,.5,.125));check(mc.thePlayer.motionX==-.25&&mc.thePlayer.motionY==.5&&mc.thePlayer.motionZ==.125,"Local velocity packet applies all axes without scaling");}finally{mc.thePlayer.motionX=x;mc.thePlayer.motionY=y;mc.thePlayer.motionZ=z;}
   mc.getNetHandler().handleEntityStatus(new S19PacketEntityStatus(target,(byte)2));check(target.hurtTime>0,"Server hurt status reaches remote entity");
   UiState.set("hitcolor.fade",false);UiState.set("hitcolor.opacity",70);
   UiState.set("hitcolor",false);byte[] normal=render(target,"vanilla-red");
   UiState.set("hitcolor",true);UiState.set("hitcolor.color","#00FF00");byte[] green=render(target,"custom-green");
   int changed=0,greenPixels=0,redPixels=0;for(int i=0;i<normal.length;i+=4){int r=normal[i]&255,g=normal[i+1]&255;if(r>g+25)redPixels++;r=green[i]&255;g=green[i+1]&255;if(g>r+25)greenPixels++;if(normal[i]!=green[i]||normal[i+1]!=green[i+1])changed++;}
   System.out.println("[unpvp-combat] tint pixels changed="+changed+" red="+redPixels+" green="+greenPixels);
   check(changed>100&&greenPixels>redPixels,"Actual player pixels change from vanilla red to configured green");
   shaderCheck(target);
   inputCheck(target);
   latencyCheck();
   fullbrightCheck();
   ConnectionProbe.run();
   FeedbackProbe.run();
   check(GL11.glGetError()==0,"Combat render leaves no GL error");
  }finally{mc.theWorld.removeEntityFromWorld(-700);UiState.set("hitcolor",true);UiState.set("hitcolor.color","#00FF00");UiState.set("hitcolor.opacity",30);}
 }
 private static void fullbrightCheck()throws Exception{
  Minecraft mc=Minecraft.getMinecraft();net.minecraft.client.renderer.EntityRenderer renderer=mc.entityRenderer;java.lang.reflect.Method update=net.minecraftforge.fml.relauncher.ReflectionHelper.findMethod(net.minecraft.client.renderer.EntityRenderer.class,renderer,new String[]{"updateLightmap","func_78472_g"},new Class<?>[]{float.class});
  int[] colors=(int[])net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.renderer.EntityRenderer.class,"lightmapColors","field_78504_Q").get(renderer);float gamma=mc.gameSettings.gammaSetting;
  net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.renderer.EntityRenderer.class,"lightmapUpdateNeeded","field_78536_aa").setBoolean(renderer,true);
  UiState.set("fullbright",false);update.invoke(renderer,0f);int[] normal=colors.clone();
  UiState.set("fullbright",true);update.invoke(renderer,0f);check(java.util.Arrays.stream(colors).allMatch(c->c==0xffffffff),"Fullbright makes every client lightmap level fully bright");
  update.invoke(renderer,0f);check(java.util.Arrays.stream(colors).allMatch(c->c==0xffffffff),"Fullbright remains stable on repeated updates");
  UiState.set("fullbright",false);update.invoke(renderer,0f);check(java.util.Arrays.equals(normal,colors)&&gamma==mc.gameSettings.gammaSetting,"Disabling Fullbright restores original lightmap and leaves gamma unchanged");
  check(UiState.module("fullbright")!=null&&SolidSurface.iconShape("fullbright")!=null,"Fullbright is available in the existing panel with a vector icon");
 }
 private static void latencyCheck()throws Exception{
  Latency.Measurement m=new Latency.Measurement();long start=1_000_000_000L;
  net.minecraft.network.Packet request=m.request(start);m.sent(request,start);m.reply(start+189_000_000L);
  check(Integer.valueOf(189).equals(m.value(start+200_000_000L)),"Latency uses response duration, independent of TAB ping");
  check(m.request(start+2_000_000_000L)==null,"Latency requests are rate limited");
  m.reset();check(m.value(start)==null,"World/connection reset clears old latency");
  request=m.request(start);m.sent(request,start);m.sent(new net.minecraft.network.play.client.C16PacketClientStatus(net.minecraft.network.play.client.C16PacketClientStatus.EnumState.REQUEST_STATS),start+1);m.reply(start+1_000_000L);
  check(m.value(start+2_000_000L)==null,"Another statistics request invalidates ambiguous latency");
  m.reset();request=m.request(start);m.sent(request,start);m.reply(start+6_000_000_000L);
  check(m.value(start+6_000_000_000L)==null&&m.request(start+60_000_000_000L)==null,"Timeout rejects late responses and stops probing until reset");
  m.reset();request=m.request(start);m.sent(request,start);m.reply(start+200_000_000L);check(m.value(start+31_000_000_000L)==null,"Old latency expires instead of persisting across silence");
  // Exercise real send/receive mixins on the isolated integrated server's connection.
  java.lang.reflect.Field field=Latency.class.getDeclaredField("measurement");field.setAccessible(true);Latency.Measurement live=(Latency.Measurement)field.get(null);
  synchronized(live){live.reset();request=live.request(System.nanoTime());}
  Minecraft.getMinecraft().getNetHandler().getNetworkManager().sendPacket(request);
  long deadline=System.nanoTime()+2_000_000_000L;
  while(Latency.value()==null&&System.nanoTime()<deadline)Thread.sleep(5);
  check(Latency.value()!=null,"Actual statistics request/reply produces a measured connection sample");
  synchronized(live){live.reset();}
 }
 private static void inputCheck(EntityOtherPlayerMP target)throws Exception{
  Minecraft mc=Minecraft.getMinecraft();net.minecraft.network.NetworkManager network=mc.getNetHandler().getNetworkManager();java.lang.reflect.Field channel=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.network.NetworkManager.class,"channel","field_150746_k");io.netty.channel.Channel transport=(io.netty.channel.Channel)channel.get(network);java.util.List<Object> sent=new java.util.concurrent.CopyOnWriteArrayList<>();
  transport.eventLoop().submit(()->transport.pipeline().addLast("unbox-combat-test",new io.netty.channel.ChannelOutboundHandlerAdapter(){@Override public void write(io.netty.channel.ChannelHandlerContext ctx,Object msg,io.netty.channel.ChannelPromise promise)throws Exception{sent.add(msg);super.write(ctx,msg,promise);}})).get(5,java.util.concurrent.TimeUnit.SECONDS);
  try{mc.playerController.attackEntity(mc.thePlayer,target);transport.eventLoop().submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);check(sent.stream().anyMatch(p->p instanceof net.minecraft.network.play.client.C02PacketUseEntity&&((net.minecraft.network.play.client.C02PacketUseEntity)p).getAction()==net.minecraft.network.play.client.C02PacketUseEntity.Action.ATTACK),"Controller attack reaches outbound channel without cancellation");}
  finally{transport.eventLoop().submit(()->transport.pipeline().remove("unbox-combat-test")).get(5,java.util.concurrent.TimeUnit.SECONDS);}
  Object mod=net.minecraftforge.fml.common.Loader.instance().getIndexedModList().get("unpvp").getMod();java.lang.reflect.Field f=Unpvp.class.getDeclaredField("pvp");f.setAccessible(true);PvpControls controls=(PvpControls)f.get(mod);java.lang.reflect.Field intent=PvpControls.class.getDeclaredField("sprinting");intent.setAccessible(true);boolean old=intent.getBoolean(controls),focus=mc.inGameHasFocus;float forward=mc.thePlayer.movementInput.moveForward;
  try{UiState.set("sprint",true);intent.setBoolean(controls,false);mc.inGameHasFocus=true;mc.thePlayer.movementInput.moveForward=1;mc.thePlayer.setSprinting(true);controls.tick();check(mc.thePlayer.isSprinting(),"Inactive toggle does not cancel vanilla double-tap sprint");}
  finally{intent.setBoolean(controls,old);mc.inGameHasFocus=focus;mc.thePlayer.movementInput.moveForward=forward;mc.thePlayer.setSprinting(false);}
 }
 private static void shaderCheck(EntityOtherPlayerMP target)throws Exception{
  Class<?> shader=Class.forName("net.optifine.shaders.Shaders");String[] flags={"shaderPackLoaded","isRenderingWorld","isShadowPass"};boolean[] old=new boolean[3];for(int i=0;i<3;i++){old[i]=shader.getField(flags[i]).getBoolean(null);shader.getField(flags[i]).setBoolean(null,i!=2);}
  Object renderer=Minecraft.getMinecraft().getRenderManager().getEntityRenderObject(target);java.lang.reflect.Method set=null,unset=null;
  for(java.lang.reflect.Method m:net.minecraft.client.renderer.entity.RendererLivingEntity.class.getDeclaredMethods()){if(m.getName().equals("setBrightness")||m.getName().equals("func_177092_a"))set=m;if(m.getName().equals("unsetBrightness")||m.getName().equals("func_177091_f"))unset=m;}
  set.setAccessible(true);unset.setAccessible(true);
  try{set.invoke(renderer,target,0f,true);Object uniform=shader.getField("uniform_entityColor").get(null);float[] value=(float[])uniform.getClass().getMethod("getValue").invoke(uniform);System.out.println("[unpvp-combat] shader hurt color="+java.util.Arrays.toString(value));check(value[0]==0&&value[1]==1&&value[2]==0&&Math.abs(value[3]-.7f)<.001,"OptiFine shader hurt uniform matches configured color and opacity");}
  finally{unset.invoke(renderer);for(int i=0;i<3;i++)shader.getField(flags[i]).setBoolean(null,old[i]);}
 }
 private static byte[] render(EntityOtherPlayerMP target,String name){Minecraft mc=Minecraft.getMinecraft();Framebuffer f=new Framebuffer(256,256,true);int mode=GL11.glGetInteger(GL11.GL_MATRIX_MODE);GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.ortho(0,256,256,0,1000,3000);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.pushMatrix();GlStateManager.loadIdentity();GlStateManager.translate(0,0,-2000);
  try{f.setFramebufferColor(.12f,.12f,.12f,1);f.framebufferClear();f.bindFramebuffer(true);target.hurtTime=5;target.maxHurtTime=10;GuiInventory.drawEntityOnScreen(128,225,95,0,0,target);ByteBuffer bytes=BufferUtils.createByteBuffer(256*256*4);GL11.glReadPixels(0,0,256,256,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,bytes);byte[] result=new byte[bytes.remaining()];bytes.get(result);File dir=new File(mc.mcDataDir,"combat-probe");dir.mkdirs();net.minecraft.util.ScreenShotHelper.saveScreenshot(dir,name+".png",256,256,f);return result;
  }finally{f.deleteFramebuffer();mc.getFramebuffer().bindFramebuffer(true);GlStateManager.matrixMode(GL11.GL_MODELVIEW);GlStateManager.popMatrix();GlStateManager.matrixMode(GL11.GL_PROJECTION);GlStateManager.popMatrix();GlStateManager.matrixMode(mode);GL11.glPopAttrib();}
 }
}
