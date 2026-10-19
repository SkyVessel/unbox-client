package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import java.util.*;

/** Hold to preview all native ColourScroller shapes, release to commit once. */
public final class CreativeVariants {
 public static KeyMapping key;
 private static List<ItemStack> choices=List.of();
 private static ItemStack original=ItemStack.EMPTY;
 private static int selected,slot;
 private static Object level;
 private static boolean blocked;
 public static void init(){key=Platform.key(new KeyMapping("key.unbox.variants",org.lwjgl.glfw.GLFW.GLFW_KEY_V,KeyMapping.Category.MISC));}
 public static boolean selecting(){return !choices.isEmpty();}
 public static void cancel(){choices=List.of();original=ItemStack.EMPTY;level=null;}
 private static boolean valid(){var mc=Minecraft.getInstance();return mc.screen==null&&mc.player!=null&&mc.player.isCreative()&&UnboxClient.enabled("variants");}
 private static boolean unchanged(){var mc=Minecraft.getInstance();return valid()&&mc.level==level&&mc.player.getInventory().getSelectedSlot()==slot&&ItemStack.matches(original,mc.player.getMainHandItem());}
 private static void begin(){
  if(selecting()||!valid()||blocked)return;
  var mc=Minecraft.getInstance();ItemStack start=mc.player.getMainHandItem().copy();if(start.isEmpty())return;
  try{
   var cycle=Class.forName("net.anware.tmc.colourscroller.ColourScroller").getMethod("cycle",Player.class,ItemStack.class,int.class,boolean.class);
   List<ItemStack> list=new ArrayList<>();list.add(start);ItemStack current=start;
   for(int i=0;i<64;i++){
    current=(ItemStack)cycle.invoke(null,mc.player,current,1,ClientHud.flag("variants.stripped",true));
    if(current.isEmpty())break;
    var next=current;if(list.stream().anyMatch(s->s.is(next.getItem())))break;list.add(current);
   }
   if(list.size()<2)return;
   choices=List.copyOf(list);original=start;slot=mc.player.getInventory().getSelectedSlot();level=mc.level;selected=0;
  }catch(ReflectiveOperationException|RuntimeException e){System.err.println("[Unbox] Shape selector: "+e);}
 }
 public static void tick(){
  if(key==null)return;
  if(!valid()||selecting()&&!unchanged()){cancel();blocked=key.isDown();return;}
  if(!key.isDown()){
   if(selecting()&&unchanged()&&selected!=0){var mc=Minecraft.getInstance();ItemStack result=choices.get(selected).copy();mc.player.getInventory().setItem(slot,result);mc.player.connection.send(new ServerboundSetCreativeModeSlotPacket(slot+36,result));}
   cancel();blocked=false;return;
  }
  begin();
 }
 public static boolean scroll(long window,double amount){
  var mc=Minecraft.getInstance();if(amount==0||key==null||!key.isDown()||window!=mc.getWindow().handle()||!valid()||blocked)return false;
  begin();if(!selecting())return false;
  int direction=(amount>0?-1:1)*(ClientHud.flag("variants.reverse",false)?-1:1);
  selected=Math.floorMod(selected+direction,choices.size());return true;
 }
 public static void draw(GuiGraphicsExtractor g){
  if(!selecting()||!unchanged())return;
  var mc=Minecraft.getInstance();int columns=Math.min(8,Math.max(1,(g.guiWidth()-32)/28));columns=Math.min(columns,choices.size());
  int rows=(choices.size()+columns-1)/columns,w=columns*28,h=rows*28,x=(g.guiWidth()-w)/2,y=(g.guiHeight()-h)/2;
  g.fill(x-8,y-24,x+w+8,y+h+36,0xbb161616);
  g.centeredText(mc.font,choices.get(selected).getHoverName(),g.guiWidth()/2,y-16,0xffffffff);
  for(int i=0;i<choices.size();i++){
   int sx=x+i%columns*28,sy=y+i/columns*28;
   g.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.withDefaultNamespace("gamemode_switcher/slot"),sx,sy,26,26);
   g.item(choices.get(i),sx+5,sy+5);
   if(i==selected)g.blitSprite(RenderPipelines.GUI_TEXTURED,Identifier.withDefaultNamespace("gamemode_switcher/selection"),sx,sy,26,26);
  }
  g.centeredText(mc.font,"Scroll to choose",g.guiWidth()/2,y+h+5,0xffdddddd);
  g.centeredText(mc.font,"Release "+key.getTranslatedKeyMessage().getString()+" to select",g.guiWidth()/2,y+h+18,0xffffffff);
 }
}
