package dev.unbox.client;

import java.util.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A cached card strip scrolls smoothly between fixed start and end positions. */
public final class VectorModsScreen extends SolidScreen {
    private String query="",notice="",signature="",catalogue="";
    private List<ModOptions.Mod> matches=List.of();
    private final List<CardControls> cards=new ArrayList<>();
    private record CardControls(int row,SolidButton settings,SolidButton toggle,SolidButton favorite) {}
    private boolean searchOpen,pendingReset;
    private double scroll,targetScroll,oldScroll;
    private long frame;
    private float category;
    private SolidButton survival,pvp;
    public VectorModsScreen(Screen parent){super("Client Mods","mods",parent);}
    public VectorModsScreen(){this(null);}
    @Override protected String scene(){return SolidTheme.scene("mods");}
    public void editHud(){var editor=new UnboxScreen(this);minecraft.gui.setScreen(editor);editor.editHud();}
    @Override protected void build(){
        themePicker();
        hit(150,119,52,52,"Back",this::onClose).feedback=false;hit(1180,190,55,50,"Close",this::onClose).feedback=false;
        survival=hit(298,108,196,58,"Survival",()->visit("mods")).geometry("tab");if(PvpModules.available())pvp=hit(494,84,196,82,"PvP",()->visit("pvp")).geometry("tab");
        hit(1120,188,48,50,"Search mods",()->{searchOpen=!searchOpen;rebuild();}).feedback=false;
        if(searchOpen)field(670,202,390,"Search mods",query,s->query=s);
        button(1005,121,124,30,"Edit HUD",SolidTheme.card(1),this::editHud).active=minecraft.player!=null;
        List<String> first=List.of("minimap","zoom","armor","dynamiclight","sprint","coordinates");
        matches=java.util.stream.Stream.concat(ModOptions.MODS.stream(),page.equals("pvp")?PvpModules.entries().stream():java.util.stream.Stream.empty()).filter(m->page.equals("mods")||PvpModules.shared(m.id())||PvpModules.contains(m.id()))
            .filter(m->(m.title()+" "+m.hint()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
            .sorted(Comparator.<ModOptions.Mod>comparingInt(m->ClientHud.flag("favorite."+m.id(),false)?-1:first.contains(m.id())?first.indexOf(m.id()):100)).toList();
        String next=matches.stream().map(ModOptions.Mod::id).toList().toString();if(!next.equals(catalogue)){pendingReset=true;catalogue=next;}
        int columns=viewport.columns(),cardWidth=(viewport.gridWidth()-18-(columns-1)*17)/columns,step=cardWidth+17;
        atlasWidth=viewport.gridWidth();atlasHeight=Math.max(1,((matches.size()+columns-1)/columns)*222-18);cards.clear();
        for(int i=0;i<matches.size();i++){
            var mod=matches.get(i);int row=i/columns,col=i%columns,x=col*step,y=row*222;
            atlasPaint.add(new SolidSurface.Op("round","",x,y,cardWidth,204,SolidTheme.card(0),30));
            atlasPaint.add(new SolidSurface.Op("icon",iconFor(mod.id()),x+(cardWidth-60)/2,y+42,60,60,SolidTheme.text(),0));
            atlasPaint.add(new SolidSurface.Op("text",mod.title(),x+20,y+164,cardWidth-100,0,SolidTheme.text(),22));
            atlasPaint.add(new SolidSurface.Op("icon","favorite",x+cardWidth-33,y+14,19,19,ClientHud.flag("favorite."+mod.id(),false)?SolidTheme.text():SolidTheme.muted(),0));
            if(PvpModules.contains(mod.id()))atlasPaint.add(new SolidSurface.Op("text",PvpModules.status(mod.id()),x+20,y+124,cardWidth-42,0,SolidTheme.muted(),16));
            if(!ClientFeaturePolicy.allowed(mod.id()))atlasPaint.add(new SolidSurface.Op("text","Host / availability settings",x+20,y+124,cardWidth-42,0,SolidTheme.muted(),16));
            var settings=absoluteHit(280+x,258+y,cardWidth,204,"Settings: "+mod.title(),()->open(mod.id())).shape(30,15);
            var toggle=absoluteHit(280+x+cardWidth-72,258+y+151,64,48,"Toggle "+mod.title(),()->toggle(mod.id()));toggle.toggle=()->PvpModules.contains(mod.id())?PvpModules.enabled(mod.id()):UnboxClient.enabled(mod.id());
            var favorite=absoluteHit(280+x+cardWidth-39,258+y+8,32,32,"Favorite "+mod.title(),()->{UnboxClient.set("favorite."+mod.id(),!ClientHud.flag("favorite."+mod.id(),false));UnboxClient.save();rebuild();});favorite.feedback=false;
            settings.inList=toggle.inList=favorite.inList=true;cards.add(new CardControls(row,settings,toggle,favorite));
        }
        if(matches.isEmpty())center("No matching mods",300,390,900,26,SolidTheme.muted());
        text(notice,280,724,950,18,SolidTheme.muted());
        hit(185,204,48,34,"Increase background opacity",()->opacity(10)).feedback=false;hit(185,640,48,34,"Decrease background opacity",()->opacity(-10)).feedback=false;
        addRenderableWidget(new OpacityControl(null));addRenderableWidget(new ThemeControl());
    }
    static String iconFor(String id){return switch(id){
        case "crosshairaddons","combat-hitboxes","betterhitreg","hitcolor"->"crosshair";
        case "shieldfixes","shieldreturn","bettertotemcounter"->"armor";
        case "statuseffecttimer","daytime"->"fullbright";
        case "naturalmotionblur","lookbehind"->"freelook";
        case "bactromod"->"fullbright";
        case "eatingpack","shulkerboxtooltip","appleskin","elytraswapperplus"->"inventory";
        case "handtweaks","viewmodel"->"move";
        case "elytrapitch"->"coordinates";default->id;
    };}
    private void opacity(int change){UnboxClient.set("ui.opacity",Math.clamp(Math.round(SolidTheme.opacity()*100)+change,0,100));UnboxClient.save();}
    private void open(String id){if(PvpModules.contains(id)){PvpModules.open(id,this);return;}minecraft.gui.setScreen(new UnboxScreen(this,id));}
    private void toggle(String id){if(PvpModules.contains(id)){PvpModules.toggle(id);rebuild();return;}if(id.equals("performance")){open(id);return;}if(!ClientFeaturePolicy.allowed(id)){notice=ClientFeaturePolicy.reason(id);rebuild();return;}UnboxClient.set(id,!UnboxClient.configured(id));UnboxClient.save();}
    @Override public void tick(){String next=query+ClientFeaturePolicy.reason("veinminer");if(!next.equals(signature)){signature=next;rebuild();}}
    @Override protected void atlasChanged(){oldScroll=scroll;if(pendingReset){scroll=targetScroll=0;pendingReset=false;}else{scroll=Math.clamp(scroll,0,maximumScroll());targetScroll=Math.clamp(targetScroll,0,maximumScroll());}}
    @Override protected void content(GuiGraphicsExtractor g,int mx,int my,float delta){
        long now=System.nanoTime();double dt=frame==0?0:Math.min(.05,(now-frame)/1e9);frame=now;
        double factor=SolidTheme.reduced()?1:1-Math.exp(-dt*16);scroll+=(targetScroll-scroll)*factor;if(Math.abs(targetScroll-scroll)<.01)scroll=targetScroll;
        float target=page.equals("pvp")?1:0;category+=(target-category)*(float)factor;
        int h1=Math.round(58+24*category),h2=Math.round(82-24*category);
        tab(g,survival,298,h1,1-category,"Survival");if(pvp!=null)tab(g,pvp,494,h2,category,"PvP");
        PanelStyle.fill(g,209,236,211,(int)viewport.height-173,SolidTheme.muted());
        int ticks=Math.max(8,(int)((viewport.height-196-246)/46));for(int i=0;i<=ticks;i++){int y=246+(int)Math.round(i*(viewport.height-196-246)/ticks);PanelStyle.fill(g,i%2==0?193:201,y,220,y+2,SolidTheme.muted());}
        float fade=atlasProgress();if(previousAtlas!=null)drawAtlas(g,previousAtlas,oldScroll,1-fade);drawAtlas(g,atlas,scroll,previousAtlas==null?1:fade);
        double shift=Math.clamp(scroll,0,maximumScroll());
        for(var card:cards){double y=258+card.row()*222-shift;
            card.settings().listAlpha=card.toggle().listAlpha=card.favorite().listAlpha=previousAtlas==null?1:fade;
            position(card.settings(),y,0);position(card.toggle(),y,151);position(card.favorite(),y,8);
        }
    }
    private void tab(GuiGraphicsExtractor g,SolidButton button,int x,int height,float selected,String label){
        int y=166-height;button.setY(y);button.setHeight(height);SolidGeometry.draw(g,"tab",x,y,196,height,PanelStyle.mix(SolidTheme.index()==2?0xff44474a:SolidTheme.card(1),SolidTheme.accent(),selected));
        int color=PanelStyle.mix(SolidTheme.text(),SolidTheme.index()==1?0xfff5f3ed:0xff292b2e,selected);SolidGeometry.centeredLabel(g,"label-"+label.toLowerCase(Locale.ROOT),x+98,y+height/2,color);
    }
    private void position(SolidButton button,double y,int offset){double value=y+offset;button.setY((int)Math.floor(value));button.fractionY=(float)(value-Math.floor(value));button.visible=y<listBottom()&&y+204>258;}
    boolean contentSettled(){return Math.abs(category-(page.equals("pvp")?1:0))<.01&&atlasProgress()>=.99;}
    float categoryProgress(){return category;}
    double scrollPosition(){return scroll;}
    double scrollTarget(){return targetScroll;}
    int cardPeriod(){return atlasHeight;}
    double maximumScroll(){return Math.max(0,atlasHeight-viewport.gridHeight());}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){
        double ux=(x-ox)/scale,uy=(y-oy)/scaleY;if(v==0||atlasHeight<=viewport.gridHeight()||ux<244||ux>viewport.width-184||uy<166||uy>viewport.height-90)return false;
        targetScroll=Math.clamp(targetScroll-v*112,0,maximumScroll());return true;
    }
}
