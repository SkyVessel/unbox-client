package dev.unbox.client;

import com.google.gson.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import java.nio.file.*;
import java.util.*;

/** Appearance uses the existing authenticated launcher protocol and acknowledgement IDs. */
public final class SkinLibraryScreen extends SolidScreen {
    private static final Path ROOT=Platform.gameDir();
    private final PlayerPreview preview=new PlayerPreview();
    private final SkinSelectionPreview selectedPreview=new SkinSelectionPreview();
    private List<JsonObject> skins=List.of();
    private String accountId="",selected="",active="",model="classic",asset="",equipped="",message="",pending="",signature="";
    private int skinPage;private long pendingAt,lastPoll;private boolean dragging,assetPending;
    public SkinLibraryScreen(Screen parent){super("Appearance","appearance",parent);}
    @Override protected void init(){load();equipment();asset=equipped;super.init();}
    private void load(){try{Path p=ROOT.resolve("unbox-skin-library.json");if(!Files.isRegularFile(p)||Files.size(p)>256000)return;JsonObject v=JsonParser.parseString(Files.readString(p)).getAsJsonObject();String uuid=text(v,"accountUuid").replace("-","");if(!uuid.equals(minecraft.getUser().getProfileId().toString().replace("-","")))return;accountId=text(v,"accountId");List<JsonObject> list=new ArrayList<>();if(v.has("skins"))for(var e:v.getAsJsonArray("skins"))list.add(e.getAsJsonObject());skins=List.copyOf(list);if(v.has("active")&&v.get("active").isJsonObject())active=text(v.getAsJsonObject("active"),"id");if(selected.isEmpty()){selected=active;model=skins.stream().filter(s->text(s,"id").equals(selected)).map(s->text(s,"model")).findFirst().orElse("classic");}}catch(Exception e){message="Could not load the skin library";}}
    @Override protected void build(){
        themePicker();hit(125,100,45,45,"Back",this::onClose);hit(1275,100,45,45,"Close",this::onClose);
        hit(1190,177,110,45,"Redeem",()->minecraft.setScreen(new RedeemScreen(this))).active=RedeemScreen.eligible();
        text(minecraft.getUser().getName(),399,180,220,26,SolidTheme.text());icon(equipped.isEmpty()?"logo":equipped,358,178,28,SolidTheme.text());
        skinPage=Math.clamp(skinPage,0,Math.max(0,skins.size()-3));
        for(int i=skinPage;i<Math.min(skins.size(),skinPage+3);i++){var skin=skins.get(i);int y=238+(i-skinPage)*88;boolean isSelected=text(skin,"id").equals(selected);round(138,y,76,76,24,isSelected?SolidTheme.accent():SolidTheme.card(1));hit(138,y,76,76,text(skin,"name"),()->{selected=text(skin,"id");model=text(skin,"model");message="";rebuild();}).active=pending.isEmpty();}
        if(skins.size()>3){button(138,510,36,34,"↑",SolidTheme.card(1),()->{skinPage--;rebuild();});button(178,510,36,34,"↓",SolidTheme.card(1),()->{skinPage++;rebuild();});}
        button(268,600,138,42,"Classic",model.equals("classic")?SolidTheme.card(2):SolidTheme.card(1),()->{model="classic";rebuild();});
        button(410,600,138,42,"Slim",model.equals("slim")?SolidTheme.card(2):SolidTheme.card(1),()->{model="slim";rebuild();});
        button(268,658,280,52,pending.isEmpty()?"Use skin":"Applying…",SolidTheme.accent(),this::apply).active=!selected.isEmpty()&&pending.isEmpty();
        hit(385,550,44,36,"Rotate player",()->preview.rotate(45));
        String[] ids={"","unbox-creator-crown","unbox-fluttering-cash"};
        for(int i=0;i<3;i++){String id=ids[i];int x=688+i*205;hit(x,246,190,228,i==0?"Unbox":i==1?"Crown":"Cash",()->{asset=id;rebuild();});if(asset.equals(id))round(x+14,481,162,4,2,SolidTheme.accent());if(!owned(id))text("Locked",x+20,503,150,18,SolidTheme.muted());}
        boolean same=asset.equals(equipped),owned=owned(asset);
        button(1068,658,220,52,assetPending?"Updating…":same?"Equipped":owned?"Equip":"Locked",same?SolidTheme.card(1):SolidTheme.accent(),()->{if(owned(asset)&&SocialBridge.command("equip-logo",asset)){assetPending=true;message="Updating asset…";rebuild();}}).active=owned&&RedeemScreen.eligible()&&!SocialBridge.commandPending()&&!same;
        String chosen=skins.stream().filter(s->text(s,"id").equals(selected)).map(s->text(s,"name")).findFirst().orElse("Import skins in the launcher");center(chosen,250,564,320,19,SolidTheme.muted());
        text(message.isEmpty()&&!RedeemScreen.eligible()?"Microsoft sign-in is required to equip client assets.":message,688,567,600,20,SolidTheme.muted());
    }
    private boolean owned(String id){if(id.isEmpty())return true;var a=SocialBridge.view.get("assets");if(a==null||!a.isJsonObject()||!a.getAsJsonObject().has("items"))return false;for(var item:a.getAsJsonObject().getAsJsonArray("items"))if(id.equals(text(item.getAsJsonObject(),"id")))return true;return false;}
    private void equipment(){equipped="";var a=SocialBridge.view.get("assets");if(a!=null&&a.isJsonObject()&&a.getAsJsonObject().has("equipment"))for(var e:a.getAsJsonObject().getAsJsonArray("equipment"))if(text(e.getAsJsonObject(),"slot").equals("logo"))equipped=text(e.getAsJsonObject(),"item");}
    private void apply(){if(accountId.isEmpty()){message="Open the launcher and select this account";rebuild();return;}try{String request=UUID.randomUUID().toString();JsonObject c=new JsonObject();c.addProperty("requestId",request);c.addProperty("accountId",accountId);c.addProperty("id",selected);c.addProperty("model",model);c.addProperty("at",System.currentTimeMillis());Path tmp=ROOT.resolve(".unbox-"+request+".tmp");Files.writeString(tmp,c.toString(),StandardOpenOption.CREATE_NEW);Files.move(tmp,ROOT.resolve("unbox-skin-command.json"),StandardCopyOption.REPLACE_EXISTING);pending=request;pendingAt=System.currentTimeMillis();message="Applying through the launcher…";rebuild();}catch(Exception e){message="Could not contact the launcher";rebuild();}}
    @Override public void tick(){
        equipment();
        if(assetPending&&!SocialBridge.commandPending()){assetPending=false;message=SocialBridge.commandSucceeded()?"Asset equipped":SocialBridge.message;}
        if(!pending.isEmpty())try{Path p=ROOT.resolve("unbox-skin-ack.json");if(Files.isRegularFile(p)&&Files.size(p)<=8192){var a=JsonParser.parseString(Files.readString(p)).getAsJsonObject();if(pending.equals(text(a,"requestId"))){pending="";message=a.has("ok")&&a.get("ok").getAsBoolean()?"Skin applied":text(a,"error");load();}}if(!pending.isEmpty()&&System.currentTimeMillis()-pendingAt>20000){pending="";message="The launcher did not respond. Keep it open and retry.";}}catch(Exception ignored){}
        if(System.currentTimeMillis()-lastPoll>1000){lastPoll=System.currentTimeMillis();load();}
        String next=skins.toString()+SocialBridge.view.get("assets")+equipped+pending+assetPending+message+RedeemScreen.eligible();if(!next.equals(signature)){signature=next;rebuild();}
    }
    @Override protected void live(GuiGraphicsExtractor g,int mx,int my,float delta){preview.previewSkin(selectedPreview.get(selected,model));preview.draw(g,ux(258,219),uy(258,238)-19,ux(558,219),uy(558,550),mx,my,dragging);for(int i=skinPage;i<Math.min(skins.size(),skinPage+3);i++){var skin=skins.get(i).deepCopy();skin.addProperty("id",UUID.nameUUIDFromBytes(text(skin,"id").getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());g.pose().pushMatrix();g.pose().translate(ux(150,250),uy(150,250+(i-skinPage)*88));g.pose().scale(52f/24);FriendsOverlay.drawFace(g,skin,0,0);g.pose().popMatrix();}}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean d){double x=(e.x()-ox)/scale,y=(e.y()-oy)/scaleY;if(e.button()==0&&x>=ux(258,219)&&x<ux(558,219)&&y>=uy(258,238)-19&&y<uy(558,550)){dragging=true;return true;}return super.mouseClicked(e,d);}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(dragging){preview.rotate((float)(dx/scale));return true;}return super.mouseDragged(e,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent e){if(dragging){dragging=false;return true;}return super.mouseReleased(e);}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){double px=(x-ox)/scale;if(px>=130&&px<220){skinPage-=Math.signum(v);rebuild();return true;}return false;}
    @Override public void removed(){super.removed();selectedPreview.close();}
    private static String text(JsonObject o,String key){return SocialBridge.text(o,key);}
}
