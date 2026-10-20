package dev.unbox.client;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.*;
/** Private grants remain discoverable after their notification disappears. */
public final class MultiplayerHubScreen extends Screen {
 private final Screen parent;private List<JsonObject> worlds=List.of();private String signature="";private int page;
 public MultiplayerHubScreen(Screen parent){super(Component.literal("Multiplayer"));this.parent=parent;}
 private int capacity(){return Math.max(1,(height-132)/48);}
 @Override protected void init(){clearWidgets();worlds=SocialBridge.availableWorlds();page=Math.min(page,Math.max(0,(worlds.size()-1)/capacity()));int x=Math.max(12,(width-520)/2),w=Math.min(520,width-24);
  addRenderableWidget(new SocialScreenButton(x,36,90,"Friends",()->minecraft.setScreen(new FriendsScreen(this))));
  var servers=addRenderableWidget(new SocialScreenButton(x+94,36,110,"Public servers",()->minecraft.setScreen(minecraft.options.skipMultiplayerWarning?new JoinMultiplayerScreen(this):new SafetyScreen(this))));servers.active=minecraft.allowsMultiplayer()&&!System.getProperty("unbox.accountKind","microsoft").equals("local");
  addRenderableWidget(new SocialScreenButton(x+208,36,92,"Connection",()->minecraft.setScreen(new NetworkDiagnosticsScreen(this))));
  for(int i=page*capacity();i<Math.min(worlds.size(),(page+1)*capacity());i++){JsonObject v=worlds.get(i);var join=addRenderableWidget(new SocialScreenButton(x+w-68,74+(i-page*capacity())*48,64,"Join",()->SocialBridge.join(SocialBridge.text(v,"id"))));join.active=SocialBridge.flag(SocialBridge.view,"connected")&&!SocialBridge.busy();}
  addRenderableWidget(new SocialScreenButton(x,height-30,50,"Back",this::onClose));
  addRenderableWidget(new SocialScreenButton(x+54,height-30,132,"Popup: "+SocialNotifications.popupSeconds()+"s",()->{int s=SocialNotifications.popupSeconds();UnboxClient.set("social.popupSeconds",s<15?15:s<30?30:s<60?60:15);UnboxClient.save();init();}));
  addRenderableWidget(new SocialScreenButton(x+w-106,height-30,50,"Prev",()->{if(page>0){page--;init();}})).active=page>0;
  addRenderableWidget(new SocialScreenButton(x+w-52,height-30,50,"Next",()->{page++;init();})).active=(page+1)*capacity()<worlds.size();
 }
 @Override public void tick(){String next=SocialBridge.availableWorlds().toString()+SocialBridge.flag(SocialBridge.view,"connected")+SocialBridge.busy();if(!next.equals(signature)){signature=next;init();}}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){if(minecraft.level==null)extractPanorama(g,d);extractBlurredBackground(g);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float d){int x=Math.max(12,(width-520)/2),w=Math.min(520,width-24);PanelStyle.center(g,"MULTIPLAYER",width/2,16,PanelStyle.TEXT);
  if(worlds.isEmpty()){g.centeredText(font,SocialBridge.flag(SocialBridge.view,"connected")?"No invited worlds yet":"Friends are reconnecting…",width/2,85,PanelStyle.TEXT);g.centeredText(font,"Ask a friend in a world to invite you.",width/2,102,PanelStyle.MUTED);}
  for(int i=page*capacity();i<Math.min(worlds.size(),(page+1)*capacity());i++){var v=worlds.get(i);int y=68+(i-page*capacity())*48;PanelStyle.round(g,x,y,w,42,6,0x80303339);var f=SocialBridge.rows("friends").stream().filter(a->SocialBridge.text(a,"id").equals(SocialBridge.text(v,"sender"))).findFirst().orElse(new JsonObject());FriendsOverlay.drawFace(g,f,x+10,y+12);g.text(font,font.plainSubstrByWidth(SocialBridge.text(v.getAsJsonObject("room"),"name"),w-120),x+40,y+9,PanelStyle.TEXT);g.text(font,font.plainSubstrByWidth(SocialBridge.text(v,"senderName"),w-120),x+40,y+23,PanelStyle.MUTED);}
  g.centeredText(font,"Hidden invitations remain here until access expires or the host closes the world.",width/2,height-47,PanelStyle.MUTED);super.extractRenderState(g,mx,my,d);
 }
}
