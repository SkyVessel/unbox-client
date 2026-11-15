package dev.unbox.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import com.google.gson.JsonObject;
import java.util.*;

/** Title and Right Shift share the approved vector layout and the real service bridge. */
public class UnboxHomeScreen extends SolidScreen {
    private final boolean inWorld;
    private final PlayerPreview playerPreview=new PlayerPreview();
    private java.util.function.Supplier<net.minecraft.world.entity.player.PlayerSkin> titleSkin;
    private List<JsonObject> friends=List.of();
    private String friendCode="",search="",signature="",notice="";
    private boolean searching,waiting,rotating;
    private int friendPage;
    private EditBox code;
    public UnboxHomeScreen(boolean inWorld){this(inWorld,inWorld?"quick":"title",null);}
    protected UnboxHomeScreen(boolean inWorld,String page,Screen parent){super(inWorld?"Unbox quick menu":"Unbox Client",page,parent);this.inWorld=inWorld;}
    public boolean inWorld(){return inWorld;}
    public float menuScale(){return scale;}
    public float menuOffsetY(){return 0;}
    @Override protected void build(){
        themePicker();round(104,750,104,32,16,SolidTheme.card(0));center("Feedback",104,755,104,17,SolidTheme.text());hit(104,750,104,32,"Feedback",()->minecraft.setScreen(new SyncFeedbackScreen(this)));
        if(page.equals("title")){buildTitle();return;}
        hit(598,372,144,70,"Edit HUD",()->{var s=new UnboxScreen(this);minecraft.setScreen(s);s.editHud();}).geometry("quick-598-372").active=minecraft.player!=null;
        icon("move",657,394,26,SolidTheme.text());
        hit(746,372,146,70,"Game settings",()->minecraft.setScreen(new OptionsScreen(this,minecraft.options,false))).geometry("quick-746-372");
        hit(598,446,294,82,"Client Mods",()->minecraft.setScreen(new VectorModsScreen(this))).geometry("quick-598-446");
        hit(144,596,212,62,"Change skin",()->minecraft.setScreen(new SkinLibraryScreen(this)));
        hit(1368,28,48,50,"Return to game",this::onClose);
        hit(984,250,50,138,"Worlds",()->minecraft.setScreen(new MultiplayerHubScreen(this))).geometry("quick-984-250");
        hit(984,396,50,138,"Add friend",()->visit("friend")).geometry("quick-984-396");
        if(page.equals("friend"))buildFriendForm();else buildFriends();
        String status=notice.isEmpty()?SocialBridge.message:notice;
        text(status,1092,164,300,16,SolidTheme.text());
        addRenderableWidget(new OpacityControl(SolidGeometry.QUICK));
    }
    private void buildTitle(){
        hit(576,314,380,85,"Singleplayer",()->minecraft.setScreen(new SelectWorldScreen(this))).geometry("title-576-314");
        hit(576,403,380,85,"Multiplayer",()->minecraft.setScreen(new MultiplayerHubScreen(this))).geometry("title-576-403");
        hit(576,504,124,78,"Game settings",()->minecraft.setScreen(new OptionsScreen(this,minecraft.options,false))).geometry("title-576-504");
        hit(704,504,124,78,"Appearance",()->minecraft.setScreen(new SkinLibraryScreen(this))).shape(0,0);
        hit(832,504,124,78,"Client Mods",()->minecraft.setScreen(new VectorModsScreen(this))).geometry("title-832-504");
        hit(26,32,62,62,"Account and friends",()->visit("quick"));
        hit(24,715,64,46,"Quit game",()->minecraft.setScreen(new ConfirmScreen(ok->{if(ok)minecraft.stop();else minecraft.setScreen(this);},Component.literal("Quit Unbox?"),Component.literal("Close Minecraft"))));
        var servers=new ServerList(minecraft);servers.load();
        for(int i=0;i<Math.min(3,servers.size());i++){var server=servers.get(i);int y=278+i*84;button(21,y,64,64,server.name.isBlank()?"?":server.name.substring(0,1).toUpperCase(Locale.ROOT),SolidTheme.card(i),()->{
            var hub=new MultiplayerHubScreen(this);minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(hub);if(ok)hub.join(server);},Component.literal("Join "+server.name+"?"),Component.literal(server.ip)));
        });}
        if(servers.size()==0)button(21,278,64,64,"+",SolidTheme.card(0),()->minecraft.setScreen(new MultiplayerHubScreen(this)));
        addRenderableWidget(new ThemeControl(SolidGeometry.TITLE));
    }
    private int friendRows(){return Math.max(4,viewport.friendRows()-(searching?1:0));}
    private void buildFriends(){
        hit(1350,190,42,40,"Search friends",()->{searching=!searching;rebuild();});
        if(searching)field(1094,236,270,"Search friends",search,s->{search=s;friendPage=0;});
        friends=SocialBridge.rows("friends").stream().filter(f->SocialBridge.text(f,"name").toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT))).toList();
        friendPage=Math.clamp(friendPage,0,Math.max(0,(friends.size()-1)/friendRows()));
        for(int i=friendPage*friendRows();i<Math.min(friends.size(),friendPage*friendRows()+friendRows());i++){
            var f=friends.get(i);int row=(searching?300:263)+(i-friendPage*friendRows())*80;String id=SocialBridge.text(f,"id");
            text(SocialBridge.text(f,"name"),1158,row-2,175,23,SolidTheme.text());
            boolean accepted=f.has("accepted")&&f.get("accepted").getAsInt()==1;
            text(accepted?(SocialBridge.flag(f,"online")?SocialBridge.text(f,"state"):"Offline"):SocialBridge.text(f,"direction").equals("incoming")?"Friend request":"Request sent",1158,row+28,175,16,SolidTheme.muted());
            if(accepted){var b=button(1342,row+3,54,42,"↗",SolidTheme.card(1),()->SocialBridge.invite(id));b.active=SocialBridge.flag(f,"online")&&minecraft.getSingleplayerServer()!=null;}
            else if(SocialBridge.text(f,"direction").equals("incoming")){button(1334,row,34,36,"✓",SolidTheme.card(1),()->SocialBridge.command("accept",id));button(1370,row,34,36,"×",SolidTheme.card(2),()->SocialBridge.command("remove",id));}
        }
        if(friends.isEmpty())text(SocialBridge.flag(SocialBridge.view,"connected")?"No friends yet. Use + to add one.":"Open the launcher to connect.",1092,290,300,20,SolidTheme.text());
        if(friends.size()>friendRows()){button(1100+(int)Math.min(40,viewport.extraHeight*.05),625+(friendRows()-4)*80,65,30,"←",SolidTheme.card(1),()->{friendPage--;rebuild();});button(1310,625+(friendRows()-4)*80,65,30,"→",SolidTheme.card(1),()->{friendPage++;rebuild();});center((friendPage+1)+" / "+((friends.size()+friendRows()-1)/friendRows()),1170,628+(friendRows()-4)*80,130,18,SolidTheme.text());}
        if(SocialBridge.hosting())button(1090,709,150,32,"Stop sharing",SolidTheme.card(2),SocialBridge::stopSharing);
        if(SocialBridge.relayFailed())button(1245,709,150,32,"Retry relay",SolidTheme.card(2),SocialBridge::retryRelay);
    }
    private void buildFriendForm(){
        hit(1090,185,43,45,"Back to friends",()->visit("quick"));
        text("Friend code",1092,266,280,20,SolidTheme.text());
        code=field(1110,322,246,"12-character code",friendCode,s->friendCode=s);code.setMaxLength(12);code.setEditable(!waiting);
        button(1092,379,286,58,waiting?"Sending…":"Send request",SolidTheme.card(1),this::requestFriend).active=!waiting&&SocialBridge.flag(SocialBridge.view,"connected");
        text("Your code",1092,510,260,18,SolidTheme.text());
        String own=SocialBridge.view.has("self")?SocialBridge.text(SocialBridge.view.getAsJsonObject("self"),"code"):"";
        button(1092,540,286,52,own.isEmpty()?"Launcher disconnected":own,SolidTheme.card(2),()->{if(!own.isEmpty()){minecraft.keyboardHandler.setClipboard(own);notice="Friend code copied";rebuild();}}).active=!own.isEmpty();
    }
    private void requestFriend(){if(!friendCode.matches("[a-fA-F0-9]{12}")){notice="Enter a 12-character friend code";rebuild();return;}if(SocialBridge.command("request",friendCode.toUpperCase(Locale.ROOT))){waiting=true;notice="Sending request…";rebuild();}}
    @Override public void tick(){if(waiting&&!SocialBridge.commandPending()){waiting=false;notice=SocialBridge.commandSucceeded()?"Friend request sent":SocialBridge.message;rebuild();}String next=SocialBridge.rows("friends").toString()+SocialBridge.view.get("self")+SocialBridge.flag(SocialBridge.view,"connected")+SocialBridge.message+search+SocialBridge.hosting()+SocialBridge.relayFailed();if(!signature.equals(next)){signature=next;if(!page.equals("title"))rebuild();}}
    @Override protected void live(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(page.equals("title")){if(titleSkin==null)titleSkin=minecraft.getSkinManager().createLookup(minecraft.getGameProfile(),false);var skin=LocalSkin.preview(titleSkin.get());g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,skin.body().texturePath(),32,32,8,8,50,50,8,8,64,64);g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,skin.body().texturePath(),32,32,40,8,50,50,8,8,64,64);return;}
        double cx=viewport.previewCenterX(),cy=viewport.previewCenterY(),h=viewport.previewHeight(),w=h*.8;
        playerPreview.draw(g,(int)(cx-w/2),(int)(cy-h/2),(int)(cx+w/2),(int)(cy+h/2),mx,my,rotating);
        if(page.equals("quick"))for(int i=friendPage*friendRows();i<Math.min(friends.size(),friendPage*friendRows()+friendRows());i++){int y=(searching?300:263)+(i-friendPage*friendRows())*80;g.pose().pushMatrix();g.pose().translate(ux(1092,y),uy(1092,y));g.pose().scale(2);FriendsOverlay.drawFace(g,friends.get(i),0,0);g.pose().popMatrix();}
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){double x=(e.x()-ox)/scale,y=(e.y()-oy)/scaleY;if(!page.equals("title")&&e.button()==0&&Math.abs(x-viewport.previewCenterX())<viewport.previewHeight()*.4&&Math.abs(y-viewport.previewCenterY())<viewport.previewHeight()/2){rotating=true;return true;}return super.mouseClicked(e,doubled);}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(rotating){playerPreview.rotate((float)(dx/scale));return true;}return super.mouseDragged(e,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent e){if(rotating){rotating=false;return true;}return super.mouseReleased(e);}
    @Override public void onClose(){if(parent!=null)minecraft.setScreen(parent);else if(inWorld)minecraft.setScreen(null);else if(!page.equals("title"))visit("title");}
    @Override public boolean shouldCloseOnEsc(){return inWorld||!page.equals("title");}
}
