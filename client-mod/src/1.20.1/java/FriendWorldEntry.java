package dev.unbox.client;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.screens.multiplayer.*;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
/** Inherits vanilla selection, keyboard joining, double-click and list narration. Never persisted to servers.dat. */
public final class FriendWorldEntry extends ServerSelectionList.NetworkServerEntry {
 private final String inviteId,worldName,ownerId,ownerName;
 public FriendWorldEntry(JoinMultiplayerScreen screen,JsonObject invite){super(screen,new LanServer(SocialBridge.text(invite.getAsJsonObject("room"),"name"),"unbox-invite:"+SocialBridge.text(invite,"id")));inviteId=SocialBridge.text(invite,"id");worldName=SocialBridge.text(invite.getAsJsonObject("room"),"name");ownerId=SocialBridge.text(invite,"sender");ownerName=SocialBridge.text(invite,"senderName");}
 @Override public void render(GuiGraphicsExtractor g,int index,int y,int x,int rowWidth,int rowHeight,int mx,int my,boolean hover,float delta){JsonObject friend=SocialBridge.rows("friends").stream().filter(f->SocialBridge.text(f,"id").equals(ownerId)).findFirst().orElse(new JsonObject());FriendsOverlay.drawFace(g,friend,x+7,y+7);g.text(minecraft.font,minecraft.font.plainSubstrByWidth(worldName,rowWidth-44),x+40,y+3,0xffffffff);g.text(minecraft.font,minecraft.font.plainSubstrByWidth(ownerName,rowWidth-44),x+40,y+17,0xffaaaaaa);}
 public void join(){if(SocialBridge.flag(SocialBridge.view,"connected")&&!SocialBridge.busy())SocialBridge.join(inviteId);}
 @Override public Component getNarration(){return Component.literal(worldName+", "+ownerName);}
 @Override public Component getServerNarration(){return getNarration();}
}
