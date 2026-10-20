package dev.unbox.client;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;

/** Small notifications above both HUD and screens. Never grabs the mouse during play. */
public final class SocialNotifications {
 public record Notice(String key,String id,String name,String detail,boolean world,JsonObject player){}
 private static final Set<String> hidden=new HashSet<>();
 private static final Map<String,Long> arrivals=new HashMap<>();
 private static List<Notice> notices=List.of();private static String account="",pending="";private static JsonObject lastView;
 private static long checked;private static float unit=1;private static int left,top;
 public static void refresh(){lastView=null;checked=0;}
 public static int popupSeconds(){return UnboxClient.number("social.popupSeconds",15,5,60);}
 public static List<Notice> pending(){return notices;}
 public static void tick(){
  var view=SocialBridge.view;long now=System.currentTimeMillis();if(view==lastView&&now-checked<1000)return;lastView=view;checked=now;
  String me=view.has("self")?SocialBridge.text(view.getAsJsonObject("self"),"id"):"";
  if(!me.isEmpty()&&!me.equals(account)){account=me;hidden.clear();arrivals.clear();pending="";}
  if(!pending.isEmpty()&&!SocialBridge.commandPending()){if(SocialBridge.commandSucceeded())hidden.add(pending);pending="";}
  var all=new ArrayList<Notice>();
  for(var f:SocialBridge.rows("friends"))if(f.has("accepted")&&f.get("accepted").getAsInt()==0&&SocialBridge.text(f,"direction").equals("incoming"))all.add(new Notice("friend:"+SocialBridge.text(f,"id"),SocialBridge.text(f,"id"),SocialBridge.text(f,"name"),"Friend request",false,f));
  for(var i:SocialBridge.rows("invites"))if(SocialBridge.text(i,"receiver").equals(me)&&i.get("expires").getAsLong()>now)all.add(new Notice("world:"+SocialBridge.text(i,"id"),SocialBridge.text(i,"id"),SocialBridge.text(i,"senderName"),SocialBridge.text(i.getAsJsonObject("room"),"name"),true,SocialBridge.rows("friends").stream().filter(f->SocialBridge.text(f,"id").equals(SocialBridge.text(i,"sender"))).findFirst().orElse(new JsonObject())));
  for(var n:all)arrivals.putIfAbsent(n.key(),now);
  notices=all.stream().filter(n->!hidden.contains(n.key())&&(!n.world()||!SocialBridge.notificationSeen(n.id())&&now-arrivals.get(n.key())<popupSeconds()*1000L)).toList();
  arrivals.keySet().retainAll(all.stream().map(Notice::key).toList());
  // Bound session memory without resurfacing still-pending, dismissed invitations.
  if(hidden.size()>512){hidden.retainAll(all.stream().map(Notice::key).toList());arrivals.keySet().retainAll(hidden);}
 }
 public static void hide(Notice n){hidden.add(n.key());lastView=null;tick();}
 public static void respond(Notice n,boolean accept){if(!SocialBridge.flag(SocialBridge.view,"connected")||SocialBridge.commandPending())return;
  if(n.world()){if(accept)SocialBridge.join(n.id());else SocialBridge.seen(n.id());hide(n);}else if(SocialBridge.command(n.world()?"dismiss":accept?"accept":"remove",n.id()))pending=n.key();
 }
 public static float entrance(float elapsed){float t=Math.clamp(elapsed/480f,0,1),s=t-1;return 1+2.3f*s*s*s+1.3f*s*s;}
 private static String shortText(String s,int max){return s.length()>max?s.substring(0,max-1)+"…":s;}
 public static void draw(GuiGraphicsExtractor g){
  if(notices.isEmpty())return;var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
  int w=mc.getWindow().getGuiScaledWidth(),h=mc.getWindow().getGuiScaledHeight();unit=.56f*Math.min(1.15f,Math.min(w/640f,h/360f));left=Math.round(w/unit)-296;top=Math.round(h/unit)-76;
  g.pose().pushMatrix();g.pose().scale(unit);
  try{for(int i=0;i<Math.min(2,notices.size());i++){var n=notices.get(i);int y=top-i*68;float e=mc.options.screenEffectScale().get()==0?1:entrance(System.currentTimeMillis()-arrivals.getOrDefault(n.key(),System.currentTimeMillis()));float scale=.86f+.14f*e;
   g.pose().pushMatrix();g.pose().translate(left+280,y+60);g.pose().scale(scale);g.pose().translate(-280,-60+(1-e)*20);
   PanelStyle.round(g,0,0,280,60,7,0xef24262d);FriendsOverlay.drawFace(g,n.player(),12,18);
   PanelStyle.label(g,shortText(n.name(),17),44,15,PanelStyle.TEXT);PanelStyle.label(g,shortText(n.detail(),22),44,31,PanelStyle.MUTED);
   boolean ready=SocialBridge.flag(SocialBridge.view,"connected")&&!SocialBridge.commandPending();
   PanelStyle.round(g,174,19,42,23,4,ready?0xff4b6595:0xff34373f);PanelStyle.center(g,pending.equals(n.key())?"…":n.world()?"Join":"Accept",195,26,PanelStyle.TEXT);
   PanelStyle.round(g,222,19,50,23,4,0xff34373f);PanelStyle.center(g,n.world()?"Hide":"Decline",247,26,ready?PanelStyle.TEXT:PanelStyle.MUTED);
   g.pose().popMatrix();}
  }finally{g.pose().popMatrix();}
 }
 public static boolean click(double mx,double my,int button,int action){if(Minecraft.getInstance().screen==null||notices.isEmpty())return false;double x=mx/unit-left,y=my/unit-top;
  for(int i=0;i<Math.min(2,notices.size());i++){double row=y+i*68;if(x<0||x>280||row<0||row>60)continue;var n=notices.get(i);
   if(button==0&&action==1){if(row>=19&&row<=42){if(x>=222&&x<=272)respond(n,false);if(x>=174&&x<=216)respond(n,true);}}return true;
  }return false;
 }
}
