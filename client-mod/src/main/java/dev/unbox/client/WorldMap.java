package dev.unbox.client;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.server.permissions.Permissions;
import java.util.*;

/** Bounded loaded-terrain sampler. No chunk requests, disk or network work in rendering. */
public final class WorldMap {
 static final int RES=128;
 static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("unbox","world_map");
 static DynamicTexture texture;static NativeImage image;
 static Object level;static String dimension="";static int originX,originZ,step=1,cursor,ticks;
 static int targetX,targetZ;static boolean full;static long nextScan;
 static final LinkedHashMap<String,LinkedHashMap<Long,int[]>> TERRAIN=new LinkedHashMap<>();
 static final Map<String,Death> DEATHS=new LinkedHashMap<>();
 public record Death(UUID player,String name,String dimension,int x,int y,int z,long time){}
 static void death(Death d){String key=d.player()+":"+d.time();DEATHS.put(key,d);while(DEATHS.size()>64)DEATHS.remove(DEATHS.keySet().iterator().next());}
 static int color(UUID id){int[] colors={0xff86aaff,0xffffc46b,0xffc999ef,0xff74d9d0,0xffff95c6};return colors[Math.floorMod(id.hashCode(),colors.length)];}
 public static void tick(Minecraft mc){
  if(mc.level==null){if(level!=null){level=null;DEATHS.clear();TERRAIN.clear();if(texture!=null)mc.getTextureManager().release(TEXTURE);texture=null;image=null;}return;}
  if(mc.level!=level){level=mc.level;dimension=mc.level.dimension().identifier().toString();cursor=0;nextScan=0;if(image!=null)image.fillRect(0,0,RES,RES,0xff16191e);}
  if(mc.player==null)return;
  mc.player.getLastDeathLocation().ifPresent(p->{boolean known=DEATHS.values().stream().anyMatch(d->d.player.equals(mc.player.getUUID())&&d.dimension.equals(p.dimension().identifier().toString())&&d.x==p.pos().getX()&&d.y==p.pos().getY()&&d.z==p.pos().getZ());if(!known)death(new Death(mc.player.getUUID(),mc.player.getName().getString(),p.dimension().identifier().toString(),p.pos().getX(),p.pos().getY(),p.pos().getZ(),System.currentTimeMillis()));});
  if(!UnboxClient.enabled("minimap")&&!(mc.screen instanceof MapScreen))return;
  if(image==null){image=new NativeImage(RES,RES,false);image.fillRect(0,0,RES,RES,0xff16191e);texture=new DynamicTexture(()->"Unbox terrain map",image);mc.getTextureManager().register(TEXTURE,texture);}
  full=mc.screen instanceof MapScreen;
  if(!full){targetX=mc.player.getBlockX();targetZ=mc.player.getBlockZ();step=UnboxClient.number("minimap.zoom",2,1,8);}
  if(cursor==0){int nx=targetX-RES*step/2,nz=targetZ-RES*step/2;if(nx==originX&&nz==originZ&&System.nanoTime()<nextScan)return;originX=nx;originZ=nz;}
  int budget=UnboxClient.number("minimap.budget",512,128,1024);long until=System.nanoTime()+250_000;
  var terrain=TERRAIN.computeIfAbsent(dimension,k->new LinkedHashMap<>(256,.75f,true));while(TERRAIN.size()>4)TERRAIN.remove(TERRAIN.keySet().iterator().next());
  BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();long previousTile=Long.MIN_VALUE;int[] tile=null;net.minecraft.world.level.chunk.LevelChunk chunk=null;
  for(int count=0;count<budget;count++){
   int px=cursor%RES,pz=cursor/RES,wx=originX+px*step,wz=originZ+pz*step;
   long tileKey=((long)(wx>>4)<<32)|((wz>>4)&0xffffffffL);if(tileKey!=previousTile){previousTile=tileKey;tile=terrain.get(tileKey);chunk=mc.level.getChunkSource().getChunk(wx>>4,wz>>4,ChunkStatus.FULL,false);}int cell=(wz&15)*16+(wx&15);
   int color=tile!=null&&tile[cell]!=0?tile[cell]:0xff16191e;
   if(chunk!=null){int y=chunk.getHeight(Heightmap.Types.WORLD_SURFACE,wx&15,wz&15);pos.set(wx,y,wz);var state=chunk.getBlockState(pos);
    // Ceiling dimensions use the visible player's layer, never scan or reveal caves.
    if(mc.level.dimensionType().hasCeiling()){y=mc.player.getBlockY()-1;pos.set(wx,y,wz);state=chunk.getBlockState(pos);}
    color=0xff000000|state.getMapColor(mc.level,pos).col;
    if(tile==null){tile=new int[256];terrain.put(tileKey,tile);while(terrain.size()>1024)terrain.remove(terrain.keySet().iterator().next());}tile[cell]=color;
   }
   image.setPixel(px,pz,color);cursor=(cursor+1)%(RES*RES);
   if(cursor==0||count%32==31&&System.nanoTime()>=until)break;
  }
  if(cursor==0){nextScan=System.nanoTime()+1_000_000_000L;texture.upload();}else if(++ticks%4==0)texture.upload();
 }
 static void draw(GuiGraphicsExtractor g,int x,int y,int size,boolean labels){
  if(texture==null){g.fill(x,y,x+size,y+size,0xdd16191e);PanelStyle.hudCenter(g,"Map",x+size/2,y+size/2,PanelStyle.MUTED,false);return;}
  g.blit(TEXTURE,x,y,x+size,y+size,0,1,0,1);g.outline(x,y,size,size,0x88ffffff);
  var mc=Minecraft.getInstance();
  if(ClientHud.flag("minimap.deaths",true))for(Death d:DEATHS.values())if(d.dimension.equals(dimension)){
   int dx=x+Math.round((d.x-originX)*size/(float)(RES*step)),dy=y+Math.round((d.z-originZ)*size/(float)(RES*step));if(dx<x+3||dy<y+3||dx>x+size-3||dy>y+size-3)continue;
   int c=d.player.equals(mc.getUser().getProfileId())?0xffef7078:color(d.player);g.fill(dx-3,dy-1,dx+4,dy+1,c);g.fill(dx-1,dy-3,dx+1,dy+4,c);if(labels)PanelStyle.hudLabel(g,d.name,dx+5,dy-5,c,true);
  }
  if(mc.player!=null){int px=x+(int)Math.round((mc.player.getX()-originX)*size/(float)(RES*step)),py=y+(int)Math.round((mc.player.getZ()-originZ)*size/(float)(RES*step));if(px>=x&&px<x+size&&py>=y&&py<y+size){g.fill(px-2,py-2,px+3,py+3,0xffffffff);}}
  PanelStyle.hudLabel(g,"N",x+size/2-3,y+3,0xffffffff,true);
 }
 public static void open(){var mc=Minecraft.getInstance();if(mc.player==null)return;targetX=mc.player.getBlockX();targetZ=mc.player.getBlockZ();cursor=0;mc.setScreen(new MapScreen());}
 static boolean canTeleport(){var p=Minecraft.getInstance().player;return p!=null&&p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);}
 static final class MapScreen extends Screen {
  int x,y,size;String notice="";
  MapScreen(){super(PanelStyle.text("World map"));}
  protected void init(){size=Math.max(64,Math.min(width-40,height-90));x=(width-size)/2;y=42;}
  @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){g.fill(0,0,width,height,0xcf141519);}
  @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){PanelStyle.center(g,"WORLD MAP",width/2,17,PanelStyle.TEXT);draw(g,x,y,size,true);PanelStyle.center(g,notice.isEmpty()?"Drag to pan  ·  Scroll to zoom  ·  M / Esc to close":notice,width/2,y+size+12,PanelStyle.MUTED);PanelStyle.center(g,canTeleport()?"Double-click a death marker to teleport":"Death markers · teleport requires cheats / operator permission",width/2,y+size+26,PanelStyle.MUTED);}
  @Override public boolean keyPressed(KeyEvent e){if(UnboxClient.mapKey!=null&&UnboxClient.mapKey.matches(e)){onClose();return true;}return super.keyPressed(e);}
  @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(e.button()==0){targetX-=(int)Math.round(dx*RES*step/size);targetZ-=(int)Math.round(dy*RES*step/size);cursor=0;return true;}return false;}
  @Override public boolean mouseScrolled(double mx,double my,double h,double v){step=Math.clamp(step+(v>0?-1:1),1,8);cursor=0;return true;}
  @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){if(!doubled||e.button()!=0)return super.mouseClicked(e,doubled);for(Death d:DEATHS.values())if(d.dimension.equals(dimension)){
   double px=x+(d.x-originX)*size/(double)(RES*step),py=y+(d.z-originZ)*size/(double)(RES*step);if(Math.hypot(e.x()-px,e.y()-py)<=8){if(!canTeleport()){notice="Teleport requires cheats / operator permission";return true;}minecraft.player.connection.sendCommand("tp @s "+(d.x+.5)+" "+d.y+" "+(d.z+.5));onClose();return true;}}
   return true;}
  @Override public boolean isPauseScreen(){return false;}
 }
}
