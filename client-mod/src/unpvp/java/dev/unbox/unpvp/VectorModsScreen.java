package dev.unbox.unpvp;
import java.util.*;
import net.minecraft.client.gui.GuiScreen;

/** Native vector mod directory; one cached strip, bounded subpixel scrolling. */
final class VectorModsScreen extends SolidScreen {
    String query="";private String signature="",catalogue="";private boolean searchOpen,pendingReset;
    private final List<Card> cards=new ArrayList<>();private double scroll,targetScroll,oldScroll;private long frame;

    private static final class Card {final int row;final SolidButton settings,toggle,favorite;Card(int row,SolidButton s,SolidButton t,SolidButton f){this.row=row;settings=s;toggle=t;favorite=f;}}
    VectorModsScreen(GuiScreen parent){super("mods",parent);}
    @Override String scene(){return SolidTheme.scene("mods");}
    @Override void build(){
        hit(150,119,52,52,"Back",this::close).feedback=false;hit(1180,190,55,50,"Close",this::close).feedback=false;
        hit(1120,188,48,50,"Search mods",()->{searchOpen=!searchOpen;rebuild();}).feedback=false;
        if(searchOpen)solidField(670,202,390,"Search mods",query,s->query=s);
        solidButton(1005,121,124,30,"Edit HUD",SolidTheme.card(1),this::editHud).active=mc.theWorld!=null;
        List<UiState.Module> matches=new ArrayList<>();for(UiState.Module m:UiState.MODULES)if((m.title+" "+m.hint).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))matches.add(m);
        Collections.sort(matches,Comparator.comparingInt(m->UiState.flag("favorite."+m.id,false)?0:1));StringBuilder ids=new StringBuilder();for(UiState.Module m:matches)ids.append(m.id).append(';');if(!ids.toString().equals(catalogue)){pendingReset=true;catalogue=ids.toString();}
        int columns=viewport.columns(),cw=(viewport.gridWidth()-18-(columns-1)*17)/columns;atlasWidth=viewport.gridWidth();atlasHeight=Math.max(1,((matches.size()+columns-1)/columns)*222-18);cards.clear();
        for(int i=0;i<matches.size();i++){UiState.Module m=matches.get(i);int row=i/columns,x=i%columns*(cw+17),y=row*222;
            atlasPaint.add(new SolidSurface.Op("round","",x,y,cw,204,SolidTheme.card(0),30));atlasPaint.add(new SolidSurface.Op("icon",m.id,x+(cw-60)/2,y+42,60,60,SolidTheme.text(),0));atlasPaint.add(new SolidSurface.Op("text",m.title,x+20,y+164,cw-84,0,SolidTheme.text(),22));atlasPaint.add(new SolidSurface.Op("icon","favorite",x+cw-33,y+14,19,19,UiState.flag("favorite."+m.id,false)?SolidTheme.accent():SolidTheme.muted(),0));
            SolidButton settings=absoluteHit(280+x,258+y,cw,204,"Settings: "+m.title,()->open(m.id)).shape(30,15);
            SolidButton toggle=absoluteHit(280+x+cw-72,258+y+151,64,48,"Toggle "+m.title,()->{if(m.id.equals("performance")||m.id.equals("resources"))open(m.id);else{UiState.set(m.id,!UiState.on(m.id));UiState.save();}});if(m.id.equals("performance")||m.id.equals("resources")){toggle.feedback=false;atlasPaint.add(new SolidSurface.Op("text","Open",x+cw-66,y+164,60,0,SolidTheme.muted(),20));}else toggle.toggle=()->UiState.on(m.id);
            SolidButton favorite=absoluteHit(280+x+cw-39,258+y+8,32,32,"Favorite "+m.title,()->{UiState.set("favorite."+m.id,!UiState.flag("favorite."+m.id,false));UiState.save();rebuild();});favorite.feedback=false;settings.clip=toggle.clip=favorite.clip=true;cards.add(new Card(row,settings,toggle,favorite));
        }
        if(matches.isEmpty())center("No matching mods",300,390,900,26,SolidTheme.muted());
        hit(185,204,48,34,"Increase background opacity",()->opacity(10)).feedback=false;hit(185,640,48,34,"Decrease background opacity",()->opacity(-10)).feedback=false;controls.add(new OpacityControl(null));controls.add(new ThemeControl(SolidGeometry.THEME));
    }
    void editHud(){ModsScreen s=new ModsScreen(this);navigate(s);s.editHud();}
    private void open(String id){if(id.equals("resources")){navigate(new PackBrowser(this));return;}navigate(new ModsScreen(this,id));}
    private void opacity(int amount){UiState.set("ui.opacity",UiState.clamp(Math.round(SolidTheme.opacity()*100)+amount,0,100));UiState.save();}
    @Override public void updateScreen(){if(!signature.equals(query)){signature=query;rebuild();}}
    @Override void atlasChanged(){oldScroll=scroll;if(pendingReset){scroll=targetScroll=0;pendingReset=false;}else{scroll=SolidGeometry.clamp(scroll,0,maximumScroll());targetScroll=SolidGeometry.clamp(targetScroll,0,maximumScroll());}}
    double maximumScroll(){return Math.max(0,atlasHeight-viewport.gridHeight());}
    @Override void content(float mx,float my,float partial){long now=System.nanoTime();double dt=frame==0?0:Math.min(.05,(now-frame)/1e9);frame=now;scroll+=(targetScroll-scroll)*(SolidTheme.reduced()?1:1-Math.exp(-dt*16));if(Math.abs(scroll-targetScroll)<.01)scroll=targetScroll;
        SolidGeometry.draw("tab",298,84,196,82,SolidTheme.accent());SolidText.draw("PvP",396,111,28,SolidTheme.index()==1?0xfff5f3ed:0xff292b2e,true);
        Painter.fill(209,236,2,(float)viewport.height-409,SolidTheme.muted());int ticks=Math.max(8,(int)((viewport.height-442)/46));for(int i=0;i<=ticks;i++){int y=246+(int)Math.round(i*(viewport.height-442)/ticks);Painter.fill(i%2==0?193:201,y,i%2==0?27:19,2,SolidTheme.muted());}
        float fade=atlasProgress();if(previousAtlas!=null)drawAtlas(previousAtlas,oldScroll,1-fade);drawAtlas(atlas,scroll,previousAtlas==null?1:fade);
        for(Card c:cards){float y=(float)(258+c.row*222-scroll);c.settings.y=y;c.toggle.y=y+151;c.favorite.y=y+8;c.settings.visible=c.toggle.visible=c.favorite.visible=y<viewport.gridBottom()&&y+204>258&&surfaceReady()&&(previousAtlas==null||fade>.5);}
    }
    @Override void scroll(float x,float y,int direction){if(x>=244&&x<=viewport.width-184&&y>=166&&y<=viewport.height-90)targetScroll=SolidGeometry.clamp(targetScroll-direction*112,0,maximumScroll());}
}
