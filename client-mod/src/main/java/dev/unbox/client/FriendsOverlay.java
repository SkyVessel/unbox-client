package dev.unbox.client;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Borderless right-hand friends rail; follows the quick menu's scale and entrance. */
public final class FriendsOverlay {
 private static final Map<String,String> FACES=new LinkedHashMap<>();
 private final List<JsonObject> friends=new ArrayList<>(),invites=new ArrayList<>();
 private int x,y,page;
 public void build(int width,int height,Consumer<AbstractWidget> add){x=width-246;y=Math.max(90,height/2-106);friends.clear();invites.clear();
  for(var f:SocialBridge.rows("friends"))if(f.get("accepted").getAsInt()==1)friends.add(f);
  String me=SocialBridge.view.has("self")?SocialBridge.text(SocialBridge.view.getAsJsonObject("self"),"id"):"";
  for(var i:SocialBridge.rows("invites"))if(SocialBridge.text(i,"receiver").equals(me))invites.add(i);
  int pages=Math.max(1,(friends.size()+4)/5);page=Math.min(page,pages-1);
  for(int i=page*5;i<Math.min(friends.size(),page*5+5);i++){JsonObject f=friends.get(i);var button=new Action(x+167,y+(i-page*5)*40,47,27,"Invite",()->SocialBridge.invite(SocialBridge.text(f,"id")));button.active=SocialBridge.flag(f,"online")&&SocialBridge.flag(SocialBridge.view,"connected");add.accept(button);}
  if(pages>1){add.accept(new Action(x+161,y+203,23,18,"<",()->page=Math.max(0,page-1)));add.accept(new Action(x+193,y+203,23,18,">",()->page=Math.min(pages-1,page+1)));}
  for(int i=0;i<Math.min(2,invites.size());i++){var v=invites.get(i);add.accept(new Action(x+170,y+246+i*36,46,24,"Join",()->SocialBridge.join(SocialBridge.text(v,"id"))));}
  if(SocialBridge.relayFailed())add.accept(new Action(x+133,y+328,83,22,"Retry relay",SocialBridge::retryRelay));
  if(SocialBridge.hosting())add.accept(new Action(x,y+328,130,22,"Stop sharing",SocialBridge::stopSharing));
 }
 public int page(){return page;}
 public void draw(GuiGraphicsExtractor g){PanelStyle.label(g,"FRIENDS",x,y-25,PanelStyle.TEXT);
  if(friends.isEmpty()){PanelStyle.label(g,SocialBridge.flag(SocialBridge.view,"connected")?"Add a friend in the launcher":"Friends offline",x,y+10,PanelStyle.MUTED);}
  for(int i=page*5;i<Math.min(friends.size(),page*5+5);i++){var f=friends.get(i);int row=y+(i-page*5)*40;drawFace(g,f,x,row);g.fill(x+19,row+19,x+26,row+26,0xff25272c);g.fill(x+21,row+21,x+25,row+25,SocialBridge.flag(f,"online")?0xff65c58b:0xff7f858f);PanelStyle.icon(g,"badge",x+34,row-1,12,PanelStyle.TEXT);PanelStyle.label(g,SocialBridge.text(f,"name"),x+50,row+1,PanelStyle.TEXT);PanelStyle.label(g,SocialBridge.flag(f,"online")?SocialBridge.text(f,"state"):"Offline",x+34,row+16,PanelStyle.MUTED);}
  if(friends.size()>5)PanelStyle.label(g,(page+1)+" / "+((friends.size()+4)/5),x,y+208,PanelStyle.MUTED);
  if(!invites.isEmpty())PanelStyle.label(g,"INVITATIONS",x,y+226,PanelStyle.MUTED);
  for(int i=0;i<Math.min(2,invites.size());i++){var v=invites.get(i);PanelStyle.label(g,SocialBridge.text(v,"senderName"),x,y+250+i*36,PanelStyle.TEXT);}
  String message=SocialBridge.message;if(!message.isEmpty()){String[] words=message.split(" ");String line="";int row=y+366;for(String word:words){if((line+word).length()>34){PanelStyle.label(g,line,x,row,PanelStyle.MUTED);row+=12;line="";}line+=word+" ";}PanelStyle.label(g,line,x,row,PanelStyle.MUTED);}
 }
 private void drawFace(GuiGraphicsExtractor g,JsonObject f,int x,int y){String face=SocialBridge.text(f,"face"),id=SocialBridge.text(f,"id");if(!id.matches("[a-f0-9-]{36}")||!face.startsWith("data:image/png;base64,")){PanelStyle.icon(g,"logo",x,y,24,PanelStyle.MUTED);return;}
  try{Identifier texture=Identifier.fromNamespaceAndPath("unbox","friends/"+id);if(!face.equals(FACES.get(id))){byte[] bytes=Base64.getDecoder().decode(face.substring(22));if(bytes.length>4096||bytes.length<24)return;var header=java.nio.ByteBuffer.wrap(bytes);if(header.getInt(16)!=8||header.getInt(20)!=8)return;if(FACES.size()>=128&&!FACES.containsKey(id)){String oldest=FACES.keySet().iterator().next();FACES.remove(oldest);Minecraft.getInstance().getTextureManager().release(Identifier.fromNamespaceAndPath("unbox","friends/"+oldest));}NativeImage image=NativeImage.read(bytes);Minecraft.getInstance().getTextureManager().register(texture,new DynamicTexture(()->"Friend face",image));FACES.put(id,face);}g.blit(texture,x,y,24,24,0,1,0,1);}catch(Exception ignored){PanelStyle.icon(g,"logo",x,y,24,PanelStyle.MUTED);}
 }
 private static final class Action extends AbstractWidget {
  private final Runnable action;
  Action(int x,int y,int w,int h,String text,Runnable action){super(x,y,w,h,PanelStyle.text(text));this.action=action;setTooltip(Tooltip.create(Component.literal(text.equals("+")?"Add friend in launcher":text)));}
  @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){PanelStyle.center(g,getMessage().getString(),getX()+width/2,getY()+8,active?(isHoveredOrFocused()?0xffedf3ff:PanelStyle.ACCENT):0xff737982);}
  @Override public void onClick(MouseButtonEvent e,boolean doubled){action.run();}
  @Override public boolean keyPressed(KeyEvent e){if(e.isConfirmation()&&active){action.run();return true;}return super.keyPressed(e);}
  @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
 }
}
