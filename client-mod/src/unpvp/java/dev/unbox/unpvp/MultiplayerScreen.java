package dev.unbox.unpvp;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.*;
import net.minecraftforge.fml.relauncher.ReflectionHelper;
import java.util.*;
/** Vanilla server browser, with invitation entries and the modern account restriction. */
public final class MultiplayerScreen extends GuiMultiplayer {
 private final List<FriendEntry> worlds=new ArrayList<>();private String signature="";
 private static final java.lang.reflect.Field LIST=ReflectionHelper.findField(GuiMultiplayer.class,"serverListSelector","field_146803_h");
 MultiplayerScreen(GuiScreen parent){super(parent);}
 private ServerSelectionList list(){try{return (ServerSelectionList)LIST.get(this);}catch(Exception e){throw new IllegalStateException(e);}}
 public int invitedCount(){return worlds.size();}public GuiListExtended.IGuiListEntry invitation(int n){return worlds.get(n);}
 private boolean publicAllowed(){return !System.getProperty("unbox.accountKind","local").equals("local");}
 @Override public void initGui(){super.initGui();int w=Math.min(72,Math.max(45,(width-128)/2));buttonList.add(new GuiButton(810,6,6,w,20,"Friends"));buttonList.add(new GuiButton(811,width-w-6,6,w,20,"Connection"));refresh();restrict();}
 private void refresh(){String next="";for(JsonObject i:SocialBridge.worlds())next+=SocialBridge.text(i,"id")+SocialBridge.text(i,"senderName")+SocialBridge.text(i.getAsJsonObject("room"),"name");if(next.equals(signature))return;signature=next;worlds.clear();for(JsonObject i:SocialBridge.worlds())worlds.add(new FriendEntry(i));selectServer(-1);}
 private FriendEntry selected(){int n=list().func_148193_k();if(n<0)return null;GuiListExtended.IGuiListEntry e=list().getListEntry(n);return e instanceof FriendEntry?(FriendEntry)e:null;}
 private void restrict(){FriendEntry entry=selected();for(GuiButton b:buttonList){if(!publicAllowed()&&(b.id==4||b.id==7))b.enabled=false;if(entry!=null&&(b.id==2||b.id==3))b.enabled=false;if(b.id==1&&(!publicAllowed()||entry!=null))b.enabled=entry!=null&&SocialBridge.connected()&&!SocialBridge.joining();}}
 @Override public void updateScreen(){super.updateScreen();refresh();restrict();}
 @Override public void selectServer(int slot){super.selectServer(slot);restrict();}
 @Override public void connectToSelected(){FriendEntry e=selected();if(e!=null)SocialBridge.join(SocialBridge.text(e.invite,"id"));else if(publicAllowed())super.connectToSelected();}
 @Override protected void actionPerformed(GuiButton b){if(b.id==810)mc.displayGuiScreen(new FriendsScreen(this));else if(b.id==811)mc.displayGuiScreen(new ConnectionScreen(this));else super.actionPerformed(b);}
 private final class FriendEntry implements GuiListExtended.IGuiListEntry {
  final JsonObject invite;long lastClick;FriendEntry(JsonObject i){invite=i;}
  public void setSelected(int slot,int x,int y){}
  public void drawEntry(int slot,int x,int y,int width,int height,int mx,int my,boolean selected){fontRendererObj.drawString("Friend · "+SocialBridge.text(invite,"senderName"),x+3,y+2,0xffffff);fontRendererObj.drawString(SocialBridge.text(invite.getAsJsonObject("room"),"name"),x+3,y+15,0xbcbcbc);fontRendererObj.drawString("Private invitation · "+SocialBridge.text(invite.getAsJsonObject("room"),"minecraft"),x+3,y+27,0x8faaf1);}
  public boolean mousePressed(int slot,int mx,int my,int button,int rx,int ry){selectServer(slot);long now=System.currentTimeMillis();if(button==0&&now-lastClick<250)connectToSelected();lastClick=now;return false;}
  public void mouseReleased(int slot,int x,int y,int button,int rx,int ry){}
 }
}
