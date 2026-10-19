package dev.unbox.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.animal.happyghast.HappyGhast;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import java.util.*;

public final class HappyGhastRecall {
 public static KeyMapping key;
 private record Call(UUID player,long deadline,int waitTicks){}
 private static final Map<HappyGhast,Call> calls=new WeakHashMap<>(); // server thread only
 public record Request() implements CustomPacketPayload {
  static final Type<Request> TYPE=new Type<>(Identifier.fromNamespaceAndPath("unbox","call_happy_ghast"));
  static final StreamCodec<RegistryFriendlyByteBuf,Request> CODEC=CustomPacketPayload.codec((p,b)->{},b->new Request());
  public Type<Request> type(){return TYPE;}
 }
 public static void init(){key=Platform.key(new KeyMapping("key.unbox.ghast_call",InputConstants.UNKNOWN.getValue(),KeyMapping.Category.MISC));}
 public static void tick(){while(key.consumeClick())if(Minecraft.getInstance().screen==null)request();}
 public static boolean canCall(){return Minecraft.getInstance().player!=null&&SurvivalHostMods.hostEnabled("ghast")&&MapTransport.canCallGhast();}
 public static String request(){if(!canCall())return "Enable Happy Ghast in an Unbox-hosted world first";MapTransport.callGhast();return "";}
 private static boolean eligible(HappyGhast ghast){return ghast.isAlive()&&!ghast.isBaby()&&!ghast.isVehicle()&&!ghast.isLeashed()&&!ghast.isNoAi();}
 public static void receive(ServerPlayer player){
  if(!WorldFeatures.serverEnabled("ghast")||!player.isAlive()||player.isSpectator())return;
  double half=UnboxClient.number("ghast.range",15,1,15)/2.0;var pos=player.position();
  var ghast=player.level().getEntitiesOfClass(HappyGhast.class,new AABB(pos.x-half,pos.y-half,pos.z-half,pos.x+half,pos.y+half,pos.z+half),g->eligible(g)&&!calls.containsKey(g)&&Math.abs(g.getX()-pos.x)<=half&&Math.abs(g.getY()-pos.y)<=half&&Math.abs(g.getZ()-pos.z)<=half).stream().min(Comparator.comparingDouble(g->g.distanceToSqr(player))).orElse(null);
  if(ghast==null){player.sendSystemMessage(Component.literal("No available Happy Ghast in the call area"),true);return;}
  calls.put(ghast,new Call(player.getUUID(),player.level().getGameTime()+200,UnboxClient.number("ghast.wait",4,1,30)*20));
  player.sendSystemMessage(Component.literal("Calling Happy Ghast…"),true);
 }
 public static void interacted(HappyGhast ghast,Player player){Call call=calls.get(ghast);if(call!=null&&call.player.equals(player.getUUID()))calls.remove(ghast);}
 static boolean called(HappyGhast ghast){return calls.containsKey(ghast);}
 public static Goal goal(HappyGhast ghast){return new Goal(){
  private long arrived=-1;
  {setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
  private ServerPlayer owner(){Call call=calls.get(ghast);return call==null?null:((net.minecraft.server.level.ServerLevel)ghast.level()).getServer().getPlayerList().getPlayer(call.player);}
  @Override public boolean canUse(){
   if(!calls.containsKey(ghast))return false;
   ServerPlayer player=owner();Call call=calls.get(ghast);long now=ghast.level().getGameTime();
   boolean valid=WorldFeatures.serverEnabled("ghast")&&eligible(ghast)&&player!=null&&player.isAlive()&&!player.isSpectator()&&player.level()==ghast.level()&&ghast.distanceToSqr(player)<32*32&&(arrived<0?now<call.deadline:now<arrived+call.waitTicks);
   if(!valid)calls.remove(ghast);return valid;
  }
  @Override public boolean canContinueToUse(){return canUse();}
  @Override public boolean requiresUpdateEveryTick(){return true;}
  @Override public void start(){arrived=-1;ghast.getNavigation().stop();}
  @Override public void tick(){
   ServerPlayer player=owner();if(player==null)return;
   if(arrived<0&&ghast.distanceToSqr(player)<12.25)arrived=ghast.level().getGameTime();
   if(arrived>=0){ghast.getMoveControl().setWantedPosition(ghast.getX(),ghast.getY(),ghast.getZ(),0);ghast.setDeltaMovement(ghast.getDeltaMovement().scale(.5));}
   else ghast.getMoveControl().setWantedPosition(player.getX(),player.getY()+1,player.getZ(),1);
   ghast.getLookControl().setLookAt(player,20,20);
  }
  @Override public void stop(){calls.remove(ghast);arrived=-1;ghast.getNavigation().stop();ghast.getMoveControl().setWantedPosition(ghast.getX(),ghast.getY(),ghast.getZ(),0);}
 };}
}
