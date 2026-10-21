package dev.unbox.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import dev.unbox.client.mixin.FriendServerListAccess;
/** The vanilla server browser, with invited worlds as native list entries. */
public final class MultiplayerHubScreen extends JoinMultiplayerScreen {
 private final Screen parent;private String signature="";
 public MultiplayerHubScreen(Screen parent){super(parent);this.parent=parent;}
 @Override public boolean isPauseScreen(){return false;}
 public boolean publicAllowed(){return minecraft.allowsMultiplayer()&&!System.getProperty("unbox.accountKind","microsoft").equals("local");}
 @Override protected void init(){super.init();int w=Math.min(72,Math.max(45,(width-128)/2));
  addRenderableWidget(Button.builder(Component.literal("Friends"),b->minecraft.setScreen(new FriendsScreen(this))).bounds(6,6,w,20).build());
  addRenderableWidget(Button.builder(Component.literal("Connection"),b->minecraft.setScreen(new NetworkDiagnosticsScreen(this))).bounds(width-w-6,6,w,20).build());
  restrictPublic();
 }
 @Override public void tick(){super.tick();String next=SocialBridge.availableWorlds().stream().map(v->SocialBridge.text(v,"id")+SocialBridge.text(v,"senderName")+SocialBridge.text(v.getAsJsonObject("room"),"name")).toList().toString()+SocialBridge.flag(SocialBridge.view,"connected")+SocialBridge.busy();if(!next.equals(signature)){signature=next;((FriendServerListAccess)(Object)serverSelectionList).unboxRefresh();onSelectedChange();}restrictPublic();}
 @Override public void join(ServerData server){if(publicAllowed())super.join(server);}
 @Override protected void onSelectedChange(){super.onSelectedChange();restrictPublic();}
 private void restrictPublic(){if(publicAllowed())return;for(var child:children())if(child instanceof Button b){String label=b.getMessage().getString();if(label.equals(Component.translatable("selectServer.direct").getString())||label.equals(Component.translatable("selectServer.add").getString()))b.active=false;}}
 public void refreshInvitedWorlds(){minecraft.setScreen(new MultiplayerHubScreen(parent));}
 public int invitedCount(){return (int)serverSelectionList.children().stream().filter(e->e instanceof FriendWorldEntry).count();}
}
