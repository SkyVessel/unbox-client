package dev.unbox.unpvp;
import com.google.gson.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.Minecraft;
import java.util.*;
/** Live friend rail within the existing vector layout; rebuild only when row content changes. */
final class SocialPanel {
 private final HomeScreen screen;boolean worlds;private int page;private String code="",signature="";private boolean managing;private List<JsonObject> rows=new ArrayList<>();private int start,count;private long checked;
 SocialPanel(HomeScreen s){screen=s;}
 void build(boolean manage){managing=manage;rows.clear();if(worlds)rows.addAll(SocialBridge.worlds());else for(JsonObject f:SocialBridge.rows("friends"))if(manage||SocialBridge.text(f,"accepted").equals("1"))rows.add(f);
  count=manage?2:screen.viewport.friendRows();page=Math.min(page,Math.max(0,(rows.size()-1)/count));start=manage?454:263;
  if(manage){screen.hit(1090,185,43,45,"Back to friends",()->screen.visit("quick"));screen.solidField(1110,280,246,"Friend code",code,v->code=v);screen.solidButton(1096,325,134,42,"Add friend",SolidTheme.card(0),()->{String value=code.trim().replace(" ","").toUpperCase(Locale.ROOT);if(value.matches("[A-F0-9]{12}"))SocialBridge.command("request",value);else SocialBridge.message="Enter a 12-character friend code";});screen.solidButton(1240,325,136,42,"Copy code",SolidTheme.card(0),()->{if(SocialBridge.view.has("self")){GuiScreen.setClipboardString(SocialBridge.text(SocialBridge.view.getAsJsonObject("self"),"code"));SocialBridge.message="Friend code copied";}});}
  for(int j=page*count;j<Math.min(rows.size(),(page+1)*count);j++){final JsonObject f=rows.get(j);final String id=SocialBridge.text(f,"id");int y=start+(j-page*count)*80;
   if(worlds){action(1285,y+8,78,"Join",()->SocialBridge.join(id)).active=SocialBridge.connected()&&!SocialBridge.joining();action(1285,y+39,78,"Dismiss",()->SocialBridge.command("dismiss",id));}
   else if(manage&&!SocialBridge.text(f,"accepted").equals("1")){if(SocialBridge.text(f,"direction").equals("incoming")){action(1270,y+5,90,"Accept",()->SocialBridge.command("accept",id));action(1270,y+36,90,"Decline",()->SocialBridge.command("remove",id));}else action(1270,y+20,90,"Cancel",()->SocialBridge.command("remove",id));}
   else{action(1285,y+8,78,"Invite",()->SocialBridge.invite(id)).active=SocialBridge.connected()&&Minecraft.getMinecraft().getIntegratedServer()!=null&&SocialBridge.flag(f,"online");if(manage)action(1285,y+39,78,"Remove",()->confirmRemove(f));}
  }
  if(rows.size()>count){action(1260,660,40,"‹",()->{page=Math.max(0,page-1);screen.rebuild();});action(1320,660,40,"›",()->{page=Math.min((rows.size()-1)/count,page+1);screen.rebuild();});}
  if(SocialBridge.hosting())action(1090,709,150,"Stop sharing",()->{SocialBridge.stopSharing();screen.rebuild();});if(SocialBridge.relayFailed())action(1250,709,130,"Retry relay",SocialBridge::retryRelay);
  signature=signature();
 }
 private SolidScreen.SolidButton action(int x,int y,int w,String label,Runnable r){screen.center(label,x,y+2,w,18,SolidTheme.text());return screen.hit(x,y,w,28,label,r);}
 private void confirmRemove(JsonObject f){Minecraft mc=Minecraft.getMinecraft();mc.displayGuiScreen(new GuiYesNo((yes,n)->{mc.displayGuiScreen(screen);if(yes)SocialBridge.command("remove",SocialBridge.text(f,"id"));},"Remove friend?",SocialBridge.text(f,"name"),0));}
 private String signature(){return SocialBridge.rows("friends").toString()+SocialBridge.worlds().stream().map(i->SocialBridge.text(i,"id")+SocialBridge.text(i,"senderName")+SocialBridge.text(i.getAsJsonObject("room"),"name")).collect(java.util.stream.Collectors.joining())+SocialBridge.connected()+SocialBridge.hosting()+SocialBridge.relayFailed()+SocialBridge.joining()+(Minecraft.getMinecraft().getIntegratedServer()!=null);}
 void tick(){long now=System.currentTimeMillis();if(now-checked<500)return;checked=now;String next=signature();if(!next.equals(signature)&&!screen.textFocused())screen.rebuild();}
 private void label(String text,int x,int y,int size,int color){SolidText.draw(text,screen.ux(x,y),screen.uy(x,y),size,color,false);}
 private static String clip(String s,int max){return s.length()>max?s.substring(0,max-1)+"…":s;}
 void draw(){if(managing){String self=SocialBridge.view.has("self")?SocialBridge.text(SocialBridge.view.getAsJsonObject("self"),"code"):"";label("Your code  ·  "+(self.isEmpty()?"Offline":self),1096,385,18,SolidTheme.muted());}
  if(rows.isEmpty())label(SocialBridge.connected()?(worlds?"No world invitations":"Add a friend using their code"):"Friends offline · keep launcher open",1092,start+15,18,SolidTheme.muted());
  for(int j=page*count;j<Math.min(rows.size(),(page+1)*count);j++){JsonObject f=rows.get(j);int y=start+(j-page*count)*80;boolean accepted=SocialBridge.text(f,"accepted").equals("1");String name=worlds?SocialBridge.text(f,"senderName"):SocialBridge.text(f,"name");String status=worlds?SocialBridge.text(f.getAsJsonObject("room"),"name"):accepted?(SocialBridge.flag(f,"online")?SocialBridge.text(f,"state"):"Offline"):SocialBridge.text(f,"direction").equals("incoming")?"Friend request":"Request sent";
   Face.draw(f,screen.ux(1094,y),screen.uy(1094,y),34);String badge=!worlds&&SocialBridge.text(f,"kind").equals("microsoft")&&f.has("logo")&&f.get("logo").isJsonObject()?NameBadges.bundled(f.getAsJsonObject("logo")):"badge";BadgeTextures.icon(badge,screen.ux(1138,y+5),screen.uy(1138,y+5),16);label(clip(name,12),1160,y+3,20,SolidTheme.text());label(clip(status,17),1138,y+29,17,SolidTheme.muted());if(!worlds)Painter.round(screen.ux(1120,y+25),screen.uy(1120,y+25),8,8,4,SocialBridge.flag(f,"online")?0xff65c58b:0xff888888);
  }
  if(rows.size()>count)label((page+1)+" / "+((rows.size()+count-1)/count),1096,665,18,SolidTheme.muted());
  if(!SocialBridge.message.isEmpty())label(clip(SocialBridge.message,45),1090,747,16,SolidTheme.muted());
 }
 static final class Face {
  private static final Map<String,net.minecraft.util.ResourceLocation> textures=new LinkedHashMap<>();
  static void draw(JsonObject row,int x,int y,int size){String data=SocialBridge.text(row,"face");try{if(!data.startsWith("data:image/png;base64,")||data.length()>6000)throw new IllegalArgumentException();net.minecraft.util.ResourceLocation t=textures.get(data);Minecraft mc=Minecraft.getMinecraft();if(t==null){byte[] bytes=Base64.getDecoder().decode(data.substring(22));if(bytes.length<24||java.nio.ByteBuffer.wrap(bytes).getInt(16)!=8||java.nio.ByteBuffer.wrap(bytes).getInt(20)!=8)throw new IllegalArgumentException();java.awt.image.BufferedImage image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes));if(image==null||image.getWidth()!=8||image.getHeight()!=8)throw new IllegalArgumentException();if(textures.size()>=128){Iterator<net.minecraft.util.ResourceLocation> it=textures.values().iterator();mc.getTextureManager().deleteTexture(it.next());it.remove();}t=mc.getTextureManager().getDynamicTextureLocation("unbox-friend",new net.minecraft.client.renderer.texture.DynamicTexture(image));textures.put(data,t);}mc.getTextureManager().bindTexture(t);net.minecraft.client.renderer.GlStateManager.color(1,1,1,1);Gui.drawModalRectWithCustomSizedTexture(x,y,0,0,size,size,size,size);}catch(Exception e){Painter.icon("logo",x,y,size,SolidTheme.muted());}}
 }
}
