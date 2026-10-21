package dev.unbox.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import com.google.gson.JsonObject;

/** Friend management remains available in-world and on the title screen. */
public final class FriendsScreen extends Screen {
 private final Screen parent;private final PanelEntrance entrance=new PanelEntrance();private EditBox code;
 private List<JsonObject> rows=List.of();private int page,x,y;private float scale;private String signature="";
 public float menuScale(){return scale;}public float menuOffsetY(){return entrance.offset();}
 public FriendsScreen(Screen parent){super(Component.literal("Friends"));this.parent=parent;}
 @Override protected void init(){String text=code==null?"":code.getValue();boolean typing=code!=null&&code.isFocused();clearWidgets();scale=Math.min(width/720f,height/480f);x=(int)(width/scale/2)-260;y=(int)(height/scale/2)-190;
  rows=SocialBridge.rows("friends");page=Math.min(page,Math.max(0,(rows.size()-1)/6));
  code=new EditBox(font,x+18,y+73,176,23,Component.literal("Friend code"));code.setMaxLength(12);code.setHint(Component.literal("Friend code"));code.setValue(text);addRenderableWidget(code);if(typing)setFocused(code);
  add(x+203,y+73,83,23,"Add friend",()->{if(code.getValue().matches("[a-fA-F0-9]{12}"))SocialBridge.command("request",code.getValue().toUpperCase(Locale.ROOT));else SocialBridge.message="Enter a 12-character friend code";});
  add(x+299,y+73,104,23,"Copy my code",()->{if(SocialBridge.view.has("self")){minecraft.keyboardHandler.setClipboard(SocialBridge.text(SocialBridge.view.getAsJsonObject("self"),"code"));SocialBridge.message="Friend code copied";}});
  add(x+449,y+15,53,24,"Back",this::onClose);
  for(int i=page*6;i<Math.min(rows.size(),page*6+6);i++){var f=rows.get(i);int yy=y+117+(i-page*6)*33;String id=SocialBridge.text(f,"id");if(f.get("accepted").getAsInt()==0&&SocialBridge.text(f,"direction").equals("incoming")){add(x+361,yy,64,24,"Accept",()->SocialBridge.command("accept",id));add(x+434,yy,68,24,"Decline",()->SocialBridge.command("remove",id));}else if(f.get("accepted").getAsInt()==1){var b=add(x+426,yy,76,24,"Invite",()->SocialBridge.invite(id));b.active=SocialBridge.flag(f,"online")&&minecraft.getSingleplayerServer()!=null;}}
  add(x+18,y+321,132,24,"Popup: "+SocialNotifications.popupSeconds()+"s",()->{int s=SocialNotifications.popupSeconds();UnboxClient.set("social.popupSeconds",s<15?15:s<30?30:s<60?60:15);UnboxClient.save();init();});
  add(x+370,y+321,60,24,"Previous",()->{page=Math.max(0,page-1);init();});add(x+439,y+321,63,24,"Next",()->{page=Math.min(Math.max(0,(rows.size()-1)/6),page+1);init();});
 }
 @Override public void tick(){String next=SocialBridge.rows("friends").toString()+SocialBridge.view.get("self")+(minecraft.getSingleplayerServer()!=null);if(!next.equals(signature)){signature=next;init();}}
 private Action add(int x,int y,int w,int h,String label,Runnable run){return addRenderableWidget(new Action(x,y,w,h,label,run));}
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){if(minecraft.level==null)extractPanorama(g,delta);extractBlurredBackground(g);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){float opacity=entrance.frame();g.pose().pushMatrix();g.pose().scale(scale);g.pose().translate(0,entrance.offset());PanelStyle.opacity(opacity);
  try{PanelStyle.icon(g,"logo",x+18,y+15,25,PanelStyle.TEXT);PanelStyle.label(g,"FRIENDS",x+57,y+22,PanelStyle.TEXT);
   String self=SocialBridge.view.has("self")?SocialBridge.text(SocialBridge.view.getAsJsonObject("self"),"code"):"Offline";PanelStyle.label(g,"Your code  ·  "+self,x+18,y+53,PanelStyle.MUTED);
   for(int i=page*6;i<Math.min(rows.size(),page*6+6);i++){var f=rows.get(i);int yy=y+117+(i-page*6)*33;FriendsOverlay.drawFace(g,f,x+18,yy);FriendsOverlay.drawLogo(g,f,x+53,yy-1);PanelStyle.label(g,SocialBridge.text(f,"name"),x+70,yy,PanelStyle.TEXT);PanelStyle.label(g,f.get("accepted").getAsInt()==1?(SocialBridge.flag(f,"online")?SocialBridge.text(f,"state"):"Offline"):SocialBridge.text(f,"direction").equals("incoming")?"Friend request":"Request sent",x+53,yy+14,PanelStyle.MUTED);}
   if(rows.isEmpty())PanelStyle.label(g,"Add a friend using their code",x+18,y+136,PanelStyle.MUTED);
   PanelStyle.label(g,(page+1)+" / "+Math.max(1,(rows.size()+5)/6),x+18,y+327,PanelStyle.MUTED);
   String message=SocialBridge.message;PanelStyle.label(g,message.length()>73?message.substring(0,72)+"…":message,x+18,y+359,PanelStyle.MUTED);
   for(var child:children())if(child instanceof AbstractWidget widget)widget.extractRenderState(g,(int)(mx/scale),(int)(my/scale-entrance.offset()),delta);
  }finally{PanelStyle.opacity(1);g.pose().popMatrix();}
 }
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){return super.mouseClicked(new MouseButtonEvent(e.x()/scale,e.y()/scale-entrance.offset(),e.buttonInfo()),doubled);}
 @Override public boolean mouseReleased(MouseButtonEvent e){return super.mouseReleased(new MouseButtonEvent(e.x()/scale,e.y()/scale-entrance.offset(),e.buttonInfo()));}
 @Override public boolean keyPressed(KeyEvent e){if(e.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT){onClose();return true;}return super.keyPressed(e);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){minecraft.setScreen(parent);}
 private static final class Action extends AbstractWidget{
  private final Runnable run;Action(int x,int y,int w,int h,String text,Runnable run){super(x,y,w,h,Component.literal(text));this.run=run;}
  @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){PanelStyle.round(g,getX(),getY(),width,height,4,isHoveredOrFocused()?0x665e5e64:0x00333337);PanelStyle.center(g,getMessage().getString(),getX()+width/2,getY()+8,active?PanelStyle.TEXT:PanelStyle.MUTED);}
  @Override public void onClick(MouseButtonEvent e,boolean doubled){run.run();}
  @Override public boolean keyPressed(KeyEvent e){if(e.isConfirmation()&&active){run.run();return true;}return super.keyPressed(e);}
  @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
 }
}
