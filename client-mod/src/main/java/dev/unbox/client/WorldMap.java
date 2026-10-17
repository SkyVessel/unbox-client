package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import java.util.*;

/** Incremental loaded-chunk capture and persistent explored terrain. Rendering never reads the world or disk. */
public final class WorldMap {
 static final int RES=128,EMPTY=0xff16191e;
 static final MapTileRenderer tiles=new MapTileRenderer();
 static Object level,connection;static String dimension="",world="",remoteWorld="";
 static double originX,originZ,targetX,targetZ;
 static int spanX=RES*2,spanZ=RES*2,step=2,ticks;
 static int captureChunk,captureCell,captureCenterX,captureCenterZ,captureRadius,nearChunk,nearCell,nearCenterX,nearCenterZ;
 static final LinkedHashSet<Long> changedColumns=new LinkedHashSet<>();
 static final int[][] CAPTURE_OFFSETS=captureOffsets();
 private static int[][] captureOffsets(){
  int[][] result=new int[65*65][2];int i=1;
  for(int r=1;r<=32;r++)for(int z=-r;z<=r;z++)for(int x=-r;x<=r;x++)if(Math.abs(x)==r||Math.abs(z)==r){result[i][0]=x;result[i++][1]=z;}
  return result;
 }
 static MapTerrainStore terrain;static String vanillaDeath="";
 static final Map<UUID,Death> DEATHS=new LinkedHashMap<>();
 public record Death(UUID player,String name,String dimension,int x,int y,int z,long time){}
 static boolean validDeath(Death d){return d!=null&&d.player()!=null&&d.name()!=null&&d.name().length()<=64&&d.dimension()!=null&&d.dimension().length()<=256&&Identifier.tryParse(d.dimension())!=null&&Math.abs((long)d.x())<=30_000_000&&Math.abs((long)d.z())<=30_000_000&&d.time()>=0;}
 static void death(Death d){
  if(!validDeath(d))return;var mc=Minecraft.getInstance();bindConnection(mc);
  if(mc.getSingleplayerServer()==null&&!recognizedLan(mc)&&!SocialLogin.trustedClientSession()&&!d.player().equals(mc.getUser().getProfileId())){
   // Ordinary servers may share only the player's current scoreboard teammates.
   var peer=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(d.player());
   if(mc.player==null||peer==null||mc.player.getTeam()==null||!mc.player.isAlliedTo(peer.getTeam()))return;
  }
  Death old=DEATHS.get(d.player());if(old!=null&&old.time()>d.time())return;
  DEATHS.remove(d.player());DEATHS.put(d.player(),d);while(DEATHS.size()>64)DEATHS.remove(DEATHS.keySet().iterator().next());
 }
 static void receiveWorld(MapSharing.WorldPacket packet){
  var mc=Minecraft.getInstance();if(mc.getSingleplayerServer()==null&&!recognizedLan(mc)&&!SocialLogin.trustedClientSession())return;
  try{String id=UUID.fromString(packet.id()).toString();bindConnection(mc);if(!remoteWorld.equals(id)&&!world.isEmpty())DEATHS.clear();remoteWorld=id;}catch(IllegalArgumentException ignored){}
 }
 static int color(UUID id){int[] colors={0xff86aaff,0xffffc46b,0xffc999ef,0xff74d9d0,0xffff95c6};return colors[Math.floorMod(id.hashCode(),colors.length)];}
 private static void resetTexture(){tiles.clear();changedColumns.clear();}
 public static void blockChanged(BlockPos pos){
  // Bounded queue: live building edits take priority over the exploration sweep.
  if(terrain==null||changedColumns.size()>=4096)return;
  changedColumns.add(MapTerrainStore.key(pos.getX(),pos.getZ()));
  changedColumns.add(MapTerrainStore.key(pos.getX()+1,pos.getZ()));
  changedColumns.add(MapTerrainStore.key(pos.getX(),pos.getZ()+1));
 }
 private static void bindConnection(Minecraft mc){
  if(connection==mc.getConnection())return;
  if(terrain!=null)terrain.close();terrain=null;world="";level=null;DEATHS.clear();vanillaDeath="";connection=mc.getConnection();remoteWorld="";resetTexture();
 }
 public static void disconnected(){
  if(terrain!=null)terrain.close();terrain=null;level=null;connection=null;world="";remoteWorld="";dimension="";DEATHS.clear();vanillaDeath="";
  resetTexture();
 }
 public static void shutdown(){if(terrain!=null)terrain.close();MapTerrainStore.finishWrites();}
 public static void tick(Minecraft mc){
  if(mc.level==null){if(level!=null)disconnected();return;}if(mc.player==null)return;
  bindConnection(mc);
  String stable=mc.getSingleplayerServer()!=null?MapSharing.worldId(mc.getSingleplayerServer()):remoteWorld;
  // Vanilla servers have no world UUID. Their saved address is the best available namespace.
  if(stable.isEmpty()&&mc.getSingleplayerServer()==null&&!SocialLogin.trustedClientSession()&&mc.getCurrentServer()!=null)stable="server:"+mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
  String dim=mc.level.dimension().identifier().toString();
  if(mc.level!=level||!stable.equals(world)){
   if(terrain!=null)terrain.close();terrain=stable.isEmpty()?null:new MapTerrainStore(stable,dim);
   // A world packet clears old markers before subsequent death packets arrive; do not erase those new packets here.
   world=stable;level=mc.level;dimension=dim;captureChunk=0;captureCell=0;resetTexture();
  }
  mc.player.getLastDeathLocation().ifPresent(p->{
   String key=p.dimension().identifier()+":"+p.pos();Death old=DEATHS.get(mc.player.getUUID());
   boolean changed=!vanillaDeath.isEmpty()&&!vanillaDeath.equals(key);vanillaDeath=key;
   if((old==null||changed)&&(old==null||!old.dimension().equals(p.dimension().identifier().toString())||old.x()!=p.pos().getX()||old.y()!=p.pos().getY()||old.z()!=p.pos().getZ()))death(new Death(mc.player.getUUID(),mc.player.getName().getString(),p.dimension().identifier().toString(),p.pos().getX(),p.pos().getY(),p.pos().getZ(),0));
  });
  if(terrain==null)return;terrain.tick();if(++ticks%20==0)terrain.flush(32);
  if(!UnboxClient.enabled("minimap")&&!(mc.screen instanceof MapScreen))return;
  capture(mc);
  updateView(mc);
  tiles.update(terrain,originX,originZ,spanX,spanZ);
 }
 static void updateView(Minecraft mc){
  if(mc.player==null)return;
  boolean full=mc.screen instanceof MapScreen;
  if(!full){
   // Interpolate in render space so the map follows the camera at frame rate, not tick rate.
   var position=mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));targetX=position.x;targetZ=position.z;
   step=UnboxClient.number("minimap.zoom",2,1,8);
  }
  double aspect=full?((MapScreen)mc.screen).aspect():1;
  spanZ=RES*step;spanX=Math.min(2048,(int)Math.round(spanZ*aspect));
  originX=targetX-spanX/2.0;originZ=targetZ-spanZ/2.0;
 }
 private static void capture(Minecraft mc){
  int cx=mc.player.getBlockX()>>4,cz=mc.player.getBlockZ()>>4;
  captureRadius=Math.clamp(mc.options.renderDistance().get(),2,32);
  if(cx!=nearCenterX||cz!=nearCenterZ){nearCenterX=cx;nearCenterZ=cz;nearChunk=0;nearCell=0;}
  if(captureChunk==0&&captureCell==0||Math.abs(cx-captureCenterX)>captureRadius||Math.abs(cz-captureCenterZ)>captureRadius){captureCenterX=cx;captureCenterZ=cz;captureChunk=0;captureCell=0;}
  int total=(captureRadius*2+1)*(captureRadius*2+1);captureChunk%=total;
  int budget=UnboxClient.number("minimap.budget",2048,512,8192);
  long until=System.nanoTime()+2_000_000;BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
  int count=0;
  while(!changedColumns.isEmpty()&&count<budget/4){
   var it=changedColumns.iterator();long key=it.next();it.remove();sample(mc,(int)(key>>32),(int)key,pos);count++;
   if((count&15)==0&&System.nanoTime()>=until)return;
  }
  // Revisit the nearest nine chunks continually; exploring the render-distance ring cannot starve them.
  int localEnd=Math.min(budget/2,count+budget/4);
  for(;count<budget;count++){
   boolean nearby=count<localEnd&&System.nanoTime()<until-1_500_000;int index=nearby?nearChunk:captureChunk;
   int wx=(((nearby?cx:captureCenterX)+CAPTURE_OFFSETS[index][0])<<4)+((nearby?nearCell:captureCell)&15);
   int wz=(((nearby?cz:captureCenterZ)+CAPTURE_OFFSETS[index][1])<<4)+((nearby?nearCell:captureCell)>>4);
   boolean loaded=sample(mc,wx,wz,pos);
   if(nearby){nearCell=loaded?(nearCell+1)%256:0;if(nearCell==0)nearChunk=(nearChunk+1)%9;}
   else{captureCell=loaded?(captureCell+1)%256:0;if(captureCell==0)captureChunk=(captureChunk+1)%total;}
   if((count&15)==15&&System.nanoTime()>=until)break;
  }
 }
 static boolean sample(Minecraft mc,int wx,int wz,BlockPos.MutableBlockPos pos){
  var chunk=mc.level.getChunkSource().getChunk(wx>>4,wz>>4,ChunkStatus.FULL,false);if(chunk==null)return false;
  boolean ceiling=mc.level.dimensionType().hasCeiling();
  int y=ceiling?mc.player.getBlockY()-1:chunk.getHeight(Heightmap.Types.MOTION_BLOCKING,wx&15,wz&15);
  var state=chunk.getBlockState(pos.set(wx,y,wz));
  // Ignore air and invisible map colors (glass, torches, etc.) instead of painting black holes.
  while(y>mc.level.getMinY()&&(state.isAir()||state.getMapColor(mc.level,pos)==net.minecraft.world.level.material.MapColor.NONE))state=chunk.getBlockState(pos.set(wx,--y,wz));
  var mapColor=state.getMapColor(mc.level,pos);int rgb=mapColor.col;
  var biome=mc.level.getBiome(pos).value();
  if(mapColor==net.minecraft.world.level.material.MapColor.GRASS||state.is(net.minecraft.world.level.block.Blocks.SHORT_GRASS)||state.is(net.minecraft.world.level.block.Blocks.TALL_GRASS))rgb=biome.getGrassColor(wx,wz);
  else if(state.is(net.minecraft.tags.BlockTags.LEAVES)){
   rgb=state.is(net.minecraft.world.level.block.Blocks.SPRUCE_LEAVES)?0x619961:state.is(net.minecraft.world.level.block.Blocks.BIRCH_LEAVES)?0x80a755:biome.getFoliageColor();
  }else if(state.getFluidState().is(net.minecraft.tags.FluidTags.WATER))rgb=biome.getWaterColor();
  int north=y,west=y;
  if(!ceiling){
   var n=mc.level.getChunkSource().getChunk(wx>>4,(wz-1)>>4,ChunkStatus.FULL,false);
   var w=mc.level.getChunkSource().getChunk((wx-1)>>4,wz>>4,ChunkStatus.FULL,false);
   if(n!=null)north=n.getHeight(Heightmap.Types.MOTION_BLOCKING,wx&15,(wz-1)&15);
   if(w!=null)west=w.getHeight(Heightmap.Types.MOTION_BLOCKING,(wx-1)&15,wz&15);
  }
  float shade=ClientHud.flag("minimap.relief",true)?Math.clamp(0.88f+(north+west-2*y)*.055f+(y-64)*.0012f,.56f,1.16f):1;
  terrain.capture(wx,wz,shade(rgb,shade));return true;
 }
 static int shade(int rgb,float factor){return 0xff000000|(Math.min(255,Math.round(((rgb>>16)&255)*factor))<<16)|(Math.min(255,Math.round(((rgb>>8)&255)*factor))<<8)|Math.min(255,Math.round((rgb&255)*factor));}
 static void draw(GuiGraphicsExtractor g,int x,int y,int size,boolean labels){
  drawMap(g,x,y,size,size,labels);
  var player=Minecraft.getInstance().player;
  if(!labels&&player!=null)PanelStyle.hudCenter(g,player.getBlockX()+" / "+player.getBlockY()+" / "+player.getBlockZ(),x+size/2,y+size+4,PanelStyle.TEXT,true);
 }
 private static void drawMap(GuiGraphicsExtractor g,int x,int y,int width,int height,boolean labels){
  var mc=Minecraft.getInstance();updateView(mc);
  g.fill(x,y,x+width,y+height,EMPTY);g.enableScissor(x,y,x+width,y+height);
  tiles.draw(g,x,y,width,height,originX,originZ,spanX,spanZ);
  if(ClientHud.flag("minimap.deaths",true))for(Death d:DEATHS.values())if(d.dimension().equals(dimension)&&visibleDeath(d)){
   int dx=mapX(d.x(),x,width),dy=mapZ(d.z(),y,height);if(dx<x+5||dy<y+5||dx>x+width-5||dy>y+height-5)continue;
   int c=mc.player!=null&&d.player().equals(mc.player.getUUID())?0xffef7078:color(d.player());
   if(labels){face(g,d.player(),dx-6,dy-6,12);g.outline(dx-7,dy-7,14,14,c);PanelStyle.hudLabel(g,d.name(),dx+10,dy-5,c,true);PanelStyle.hudLabel(g,"Last death",dx+10,dy+6,PanelStyle.MUTED,true);}
   else{g.fill(dx-3,dy-1,dx+4,dy+1,c);g.fill(dx-1,dy-3,dx+1,dy+4,c);}
  }
  if(mc.player!=null){
   var position=mc.player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
   int px=mapX(position.x,x,width),py=mapZ(position.z,y,height);
   arrow(g,px,py,mc.player.getViewYRot(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false)),0xffffffff);
   if(labels){face(g,mc.player.getUUID(),px+10,py-7,12);PanelStyle.hudLabel(g,mc.player.getName().getString(),px+25,py-5,PanelStyle.TEXT,true);}
  }
  g.disableScissor();g.outline(x,y,width,height,0x806c7682);
  PanelStyle.hudCenter(g,"N",x+width/2,y+4,PanelStyle.TEXT,true);
  if(!labels){PanelStyle.hudCenter(g,"S",x+width/2,y+height-11,PanelStyle.MUTED,true);PanelStyle.hudLabel(g,"W",x+4,y+height/2-4,PanelStyle.MUTED,true);PanelStyle.hudLabel(g,"E",x+width-10,y+height/2-4,PanelStyle.MUTED,true);}
 }
 private static boolean recognizedLan(Minecraft mc){return mc.getCurrentServer()!=null&&mc.getCurrentServer().isLan();}
 private static boolean visibleDeath(Death d){
  var mc=Minecraft.getInstance();if(mc.player==null)return false;if(d.player().equals(mc.player.getUUID()))return true;
  if(mc.getSingleplayerServer()!=null&&SocialBridge.guarded())return SocialBridge.allowed(d.player());
  if(SocialLogin.trustedClientSession())return true;
  var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(d.player());
  return info!=null&&(mc.getSingleplayerServer()!=null||recognizedLan(mc)||mc.player.getTeam()!=null&&mc.player.isAlliedTo(info.getTeam()));
 }
 private static int mapX(double value,int x,int width){return x+(int)Math.round((value-originX)*width/spanX);}
 private static int mapZ(double value,int y,int height){return y+(int)Math.round((value-originZ)*height/spanZ);}
 private static void face(GuiGraphicsExtractor g,UUID id,int x,int y,int size){
  var mc=Minecraft.getInstance();var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(id);
  var skin=mc.player!=null&&mc.player.getUUID().equals(id)?mc.player.getSkin():info==null?DefaultPlayerSkin.get(id):info.getSkin();
  var texture=skin.body().texturePath();g.blit(RenderPipelines.GUI_TEXTURED,texture,x,y,8f,8f,size,size,8,8,64,64);g.blit(RenderPipelines.GUI_TEXTURED,texture,x,y,40f,8f,size,size,8,8,64,64);
 }
 private static void arrow(GuiGraphicsExtractor g,int x,int y,float yaw,int color){
  double a=Math.toRadians(yaw),dx=-Math.sin(a),dy=Math.cos(a),sideX=dy,sideY=-dx;
  int tx=x+(int)Math.round(dx*7),ty=y+(int)Math.round(dy*7),bx=x-(int)Math.round(dx*4),by=y-(int)Math.round(dy*4);
  line(g,bx+(int)Math.round(sideX*4),by+(int)Math.round(sideY*4),tx,ty,color);
  line(g,bx-(int)Math.round(sideX*4),by-(int)Math.round(sideY*4),tx,ty,color);line(g,x,y,tx,ty,color);
 }
 private static void line(GuiGraphicsExtractor g,int x,int y,int tx,int ty,int color){int steps=Math.max(Math.abs(tx-x),Math.abs(ty-y));for(int i=0;i<=steps;i++){int px=x+(tx-x)*i/Math.max(1,steps),py=y+(ty-y)*i/Math.max(1,steps);g.fill(px,py,px+1,py+1,color);}}
 public static void open(){var mc=Minecraft.getInstance();if(mc.player==null)return;targetX=mc.player.getBlockX();targetZ=mc.player.getBlockZ();step=UnboxClient.number("minimap.zoom",2,1,8);mc.setScreen(new MapScreen());updateView(mc);}
 static boolean canTeleport(){var p=Minecraft.getInstance().player;return p!=null&&p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);}
 static final class MapScreen extends Screen {
  int x,y,mapWidth,mapHeight;boolean dragging;String notice="";
  MapScreen(){super(PanelStyle.text("World map"));}
  protected void init(){
   x=12;y=35;mapWidth=Math.max(32,width-24);mapHeight=Math.max(32,height-89);
   addRenderableWidget(net.minecraft.client.gui.components.Button.builder(PanelStyle.text("Center"),button->{targetX=minecraft.player.getX();targetZ=minecraft.player.getZ();updateView(minecraft);}).bounds(width-109,8,58,20).build());
   addRenderableWidget(net.minecraft.client.gui.components.Button.builder(PanelStyle.text("Close"),button->onClose()).bounds(width-47,8,35,20).build());
  }
  double aspect(){return mapWidth/(double)mapHeight;}
  @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xff141519);}
  @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
   PanelStyle.label(g,"WORLD MAP",12,13,PanelStyle.TEXT);drawMap(g,x,y,mapWidth,mapHeight,true);
   int ruler=Math.max(8,(int)(32.0*mapWidth/spanX));g.fill(x+8,y+mapHeight-26,x+8+ruler,y+mapHeight-25,0xffeef2f4);g.fill(x+8,y+mapHeight-29,x+9,y+mapHeight-25,0xffeef2f4);g.fill(x+7+ruler,y+mapHeight-29,x+8+ruler,y+mapHeight-25,0xffeef2f4);PanelStyle.hudLabel(g,"32 blocks",x+8,y+mapHeight-39,PanelStyle.TEXT,true);
   super.extractRenderState(g,mx,my,delta);
   if(mx>=x&&mx<x+mapWidth&&my>=y&&my<y+mapHeight){g.fill(mx,y,mx+1,y+mapHeight,0x6686aaff);g.fill(x,my,x+mapWidth,my+1,0x6686aaff);int wx=(int)Math.floor(originX)+(int)Math.floor((mx-x)*spanX/(double)mapWidth),wz=(int)Math.floor(originZ)+(int)Math.floor((my-y)*spanZ/(double)mapHeight);PanelStyle.hudLabel(g,"X "+wx+"  Z "+wz,x+5,y+mapHeight-14,PanelStyle.TEXT,true);}
   String key=UnboxClient.mapKey==null?"M":UnboxClient.mapKey.getTranslatedKeyMessage().getString();
   PanelStyle.center(g,notice.isEmpty()?"Drag to pan  ·  Scroll to zoom  ·  "+key+" / Esc to close":notice,width/2,y+mapHeight+10,PanelStyle.MUTED);
   PanelStyle.center(g,canTeleport()?"Double-click a death marker to teleport":"Death markers · teleport requires cheats / operator permission",width/2,y+mapHeight+24,PanelStyle.MUTED);
  }
  @Override public boolean keyPressed(KeyEvent e){if(e.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE){targetX=minecraft.player.getX();targetZ=minecraft.player.getZ();updateView(minecraft);return true;}if(UnboxClient.mapKey!=null&&UnboxClient.mapKey.matches(e)){onClose();return true;}return super.keyPressed(e);}
  private boolean inside(double mx,double my){return mx>=x&&mx<x+mapWidth&&my>=y&&my<y+mapHeight;}
  @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(e.button()==0&&(dragging||inside(e.x(),e.y()))){targetX=Math.clamp(targetX-dx*spanX/mapWidth,-30_000_000,30_000_000);targetZ=Math.clamp(targetZ-dy*spanZ/mapHeight,-30_000_000,30_000_000);updateView(minecraft);return true;}return false;}
  @Override public boolean mouseReleased(MouseButtonEvent e){dragging=false;return super.mouseReleased(e);}
  @Override public boolean mouseScrolled(double mx,double my,double h,double v){
   if(v==0||!inside(mx,my))return false;
   double wx=originX+(mx-x)*spanX/mapWidth,wz=originZ+(my-y)*spanZ/mapHeight;
   step=Math.clamp(step+(v>0?-1:1),1,8);updateView(minecraft);
   targetX=Math.clamp(wx-((mx-x)/mapWidth-.5)*spanX,-30_000_000,30_000_000);targetZ=Math.clamp(wz-((my-y)/mapHeight-.5)*spanZ,-30_000_000,30_000_000);updateView(minecraft);return true;
  }
  @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){dragging=e.button()==0&&inside(e.x(),e.y());if(!inside(e.x(),e.y()))return super.mouseClicked(e,doubled);if(!doubled||e.button()!=0||!ClientHud.flag("minimap.deaths",true))return super.mouseClicked(e,doubled);for(Death d:DEATHS.values())if(d.dimension().equals(dimension)&&visibleDeath(d)){
   int px=mapX(d.x(),x,mapWidth),py=mapZ(d.z(),y,mapHeight);if(px<x||px>x+mapWidth||py<y||py>y+mapHeight)continue;
   if(Math.hypot(e.x()-px,e.y()-py)<=9){if(!canTeleport()){notice="Teleport requires cheats / operator permission";return true;}minecraft.player.connection.sendCommand("tp @s "+(d.x()+.5)+" "+d.y()+" "+(d.z()+.5));onClose();return true;}}
   return true;}
  @Override public void onClose(){super.onClose();updateView(minecraft);}
  @Override public boolean isPauseScreen(){return false;}
 }
}
