package dev.unbox.client;

import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.nio.file.*;
import java.util.*;

/** Local skin picker. The launcher performs authenticated uploads and returns an acknowledgement. */
public final class SkinLibraryScreen extends Screen {
 private static final Path ROOT=Platform.gameDir();private static final Gson JSON=new Gson();
 private final Screen parent;private final PlayerPreview preview=new PlayerPreview();private List<JsonObject> skins=List.of();
 private String accountId="",message="",selected="";private int page,x,y;private float scale=1;private boolean dragging;private double dragX;private String pending="";private long pendingAt;
 public SkinLibraryScreen(Screen parent){super(Component.literal("Skins"));this.parent=parent;}
 @Override protected void init(){clearWidgets();scale=Math.min(1,Math.min(width/620f,height/460f));x=(int)(width/scale/2)-280;y=(int)(height/scale/2)-205;load();for(int i=page*7;i<Math.min(skins.size(),page*7+7);i++){var skin=skins.get(i);int yy=y+63+(i-page*7)*38;String id=text(skin,"id");add(x+305,yy,176,29,text(skin,"name"),true,id.equals(selected),()->{selected=id;apply(id);});}
  add(x+492,y+15,52,25,"Back",false,false,this::onClose);add(x+305,y+342,82,25,"Previous",false,false,()->{page=Math.max(0,page-1);init();});add(x+396,y+342,85,25,"Next",false,false,()->{page=Math.min(Math.max(0,(skins.size()-1)/7),page+1);init();});}
 private void load(){try{Path p=ROOT.resolve("unbox-skin-library.json");if(!Files.isRegularFile(p)||Files.size(p)>256000)return;JsonObject v=JsonParser.parseString(Files.readString(p)).getAsJsonObject();accountId=text(v,"accountId");List<JsonObject> out=new ArrayList<>();if(v.has("skins"))for(var e:v.getAsJsonArray("skins"))out.add(e.getAsJsonObject());skins=out;}catch(Exception ignored){}}
 private void apply(String id){if(!pending.isEmpty())return;if(accountId.isEmpty()){message="Open the launcher and select a Microsoft account";return;}try{String request=UUID.randomUUID().toString();JsonObject c=new JsonObject();c.addProperty("requestId",request);c.addProperty("accountId",accountId);c.addProperty("id",id);c.addProperty("at",System.currentTimeMillis());write("unbox-skin-command.json",c);pending=request;pendingAt=System.currentTimeMillis();message="Applying through the launcher…";}catch(Exception e){message="Could not contact the launcher";}}
 @Override public void tick(){if(pending.isEmpty())return;if(System.currentTimeMillis()-pendingAt>20000){pending="";message="The launcher did not respond. Keep it open and retry.";return;}try{Path p=ROOT.resolve("unbox-skin-ack.json");if(!Files.isRegularFile(p)||Files.size(p)>8192)return;JsonObject a=JsonParser.parseString(Files.readString(p)).getAsJsonObject();if(!pending.equals(text(a,"requestId")))return;pending="";message=a.has("ok")&&a.get("ok").getAsBoolean()?"Skin applied. Rejoin to refresh it in this game.":text(a,"error");if(message.isEmpty())message="The launcher could not apply this skin.";}catch(Exception ignored){}}
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){g.pose().pushMatrix();g.pose().scale(scale);int ux=(int)(mx/scale),uy=(int)(my/scale);PanelStyle.icon(g,"logo",x+17,y+15,25,PanelStyle.TEXT);PanelStyle.label(g,"SKINS",x+55,y+22,PanelStyle.TEXT);PanelStyle.round(g,x+18,y+55,260,312,10,0xd91d2026);preview.draw(g,x+28,y+70,x+268,y+350,ux,uy,dragging);PanelStyle.label(g,"Drag horizontally to rotate",x+67,y+374,PanelStyle.MUTED);
  PanelStyle.label(g,skins.isEmpty()?"Import skins in the Unbox launcher":"Choose a saved skin",x+305,y+51,PanelStyle.MUTED);PanelStyle.label(g,(page+1)+" / "+Math.max(1,(skins.size()+6)/7),x+305,y+375,PanelStyle.MUTED);for(int from=0,line=0;from<message.length();from+=42,line++)PanelStyle.label(g,message.substring(from,Math.min(from+42,message.length())),x+305,y+392+line*12,PanelStyle.MUTED);for(var child:children())if(child instanceof AbstractWidget w)w.extractRenderState(g,ux,uy,delta);g.pose().popMatrix();}
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){double ux=e.x()/scale,uy=e.y()/scale;if(e.button()==0&&ux>=x+18&&ux<=x+278&&uy>=y+55&&uy<=y+367){dragging=true;dragX=ux;return true;}return super.mouseClicked(new MouseButtonEvent(ux,uy,e.buttonInfo()),doubled);}
 @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){double ux=e.x()/scale;if(dragging){preview.rotate((float)(ux-dragX));dragX=ux;return true;}return super.mouseDragged(new MouseButtonEvent(ux,e.y()/scale,e.buttonInfo()),dx/scale,dy/scale);}
 @Override public boolean mouseReleased(MouseButtonEvent e){if(dragging){dragging=false;return true;}return super.mouseReleased(new MouseButtonEvent(e.x()/scale,e.y()/scale,e.buttonInfo()));}
 @Override public boolean keyPressed(KeyEvent e){if(e.key()==org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT){onClose();return true;}return super.keyPressed(e);}
 @Override public void onClose(){minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
 private Action add(int x,int y,int w,int h,String label,boolean card,boolean selected,Runnable run){return addRenderableWidget(new Action(x,y,w,h,label,card,selected,run));}
 private static String text(JsonObject o,String k){return o.has(k)&&!o.get(k).isJsonNull()?o.get(k).getAsString():"";}
 private static void write(String name,JsonObject value)throws Exception{Path tmp=ROOT.resolve(".unbox-"+UUID.randomUUID()+".tmp");Files.writeString(tmp,JSON.toJson(value),StandardOpenOption.CREATE_NEW);Files.move(tmp,ROOT.resolve(name),StandardCopyOption.REPLACE_EXISTING);}
 private static final class Action extends AbstractWidget{private final Runnable run;private final boolean card,selected;Action(int x,int y,int w,int h,String text,boolean card,boolean selected,Runnable run){super(x,y,w,h,Component.literal(text));this.run=run;this.card=card;this.selected=selected;}@Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float d){if(card||isHoveredOrFocused())PanelStyle.round(g,getX(),getY(),width,height,5,selected?0xff43577e:isHoveredOrFocused()?0xff4b6088:0xc832353d);PanelStyle.center(g,getMessage().getString(),getX()+width/2,getY()+10,PanelStyle.TEXT);}@Override public void onClick(MouseButtonEvent e,boolean d){run.run();}@Override protected void updateWidgetNarration(NarrationElementOutput o){defaultButtonNarrationText(o);}}
}
