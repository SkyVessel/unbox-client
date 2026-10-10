package dev.unbox.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.*;

/** Feather-style module cards with separate actions and live, typed settings. */
public class UnboxScreen extends Screen {
    private int left,top,pw,ph,scroll,maxScroll,bodyTop,bodyBottom;
    private float menuScale=1;
    private final PanelEntrance entrance=new PanelEntrance();
    public float menuOffsetY(){return editing?0:entrance.offset();}
    @Override public void added(){entrance.reset();}
    public float menuScale(){return menuScale;}
    private String settingQuery="";private EditBox settingSearch;private int previewScene;private String query="",category="All",detail="",tab="General",notice="";
    private boolean editing,dragging,resizing,snap=true;
    private String selected="fps";private int dragX,dragY;
    private final Properties hudBefore=new Properties();
    private final List<AbstractWidget> content=new ArrayList<>();
    private final List<Row> rows=new ArrayList<>();
    private final Map<String,String> resetUndo=new HashMap<>();
    private final Map<String,PanelStyle.Motion> toggleMotions=new HashMap<>();
    private EditBox search;private KeyMapping recording;
    private record Row(int y,String label,boolean active) {Row(int y,String label){this(y,label,true);}}
    private Choice expandedChoice;private int dropdownIndex;
    private final Screen parent;
    public UnboxScreen(){this(null);}
    public UnboxScreen(Screen parent){super(Component.literal("Unbox Client"));this.parent=parent;}
    public void editHud(){startEditor();}
    public boolean isEditingHud(){return editing;}
    @Override protected void init(){
        clearWidgets();content.clear();rows.clear();expandedChoice=null;
        int physicalWidth=minecraft.getWindow().getGuiScaledWidth(),physicalHeight=minecraft.getWindow().getGuiScaledHeight();
        menuScale=editing?1:.85f*Math.min(1f,Math.min(physicalWidth/920f,physicalHeight/570f));
        width=Math.round(physicalWidth/menuScale);height=Math.round(physicalHeight/menuScale);
        pw=Math.min(860,width-110);ph=Math.min(540,height-28);left=(width-pw)/2;top=(height-ph)/2;
        if(editing){initEditor();return;}
        button(left+pw-43,top+15,28,27,"Close",()->onClose()).icon="close";
        if(detail.isEmpty())initGrid();else initDetail();
    }
    private void initGrid(){
        Action edit=button(left+pw-155,top+15,103,27,"Edit HUD",this::startEditor);edit.accent=true;edit.active=minecraft.player!=null;
        int sy=top+62;
        search=addRenderableWidget(new EditBox(font,left+pw-207,sy+7,179,20,PanelStyle.text("Search mods")));
        search.addFormatter((v,i)->PanelStyle.text(v).getVisualOrderText());search.setBordered(false);search.setTextShadow(false);search.setHint(PanelStyle.text("Search mods..."));search.setValue(query);
        search.setResponder(s->{query=s;scroll=0;refreshGrid();});
        String[] cats={"All","HUD","Visuals","Controls","Favorites"};
        int x=left+18;for(String cat:cats){int w=cat.equals("Favorites")?68:cat.equals("Controls")?65:50;Action b=button(x,sy,w,29,cat,()->{category=cat;scroll=0;init();});b.selected=cat.equals(category);x+=w+4;}
        bodyTop=top+108;bodyBottom=top+ph-31;refreshGrid();
    }
    private void clearContent(){for(var w:content)removeWidget(w);content.clear();rows.clear();}
    private <T extends AbstractWidget>T body(T widget){content.add(addRenderableWidget(widget));return widget;}
    private void refreshGrid(){
        clearContent();List<ModOptions.Mod> matches=ModOptions.MODS.stream().filter(m->(m.title()+" "+m.hint()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
            .filter(m->category.equals("All")||category.equals(m.group())||category.equals("Favorites")&&ClientHud.flag("favorite."+m.id(),false))
            .sorted(Comparator.comparing(m->!ClientHud.flag("favorite."+m.id(),false))).toList();
        int cols=pw>=660?4:3,cw=(pw-36-(cols-1)*10)/cols,ch=Math.max(100,Math.min(110,(bodyBottom-bodyTop-20)/3));
        maxScroll=Math.max(0,((matches.size()+cols-1)/cols)*(ch+10)-10-(bodyBottom-bodyTop));scroll=Math.clamp(scroll,0,maxScroll);
        for(int i=0;i<matches.size();i++){
            var mod=matches.get(i);int x=left+18+(i%cols)*(cw+10),y=bodyTop+(i/cols)*(ch+10)-scroll;
            Card card=body(new Card(x,y,cw,ch,mod));
            Action favorite=body(new Action(x+cw-26,y+7,20,20,"Favorite "+mod.title(),()->{UnboxClient.set("favorite."+mod.id(),!ClientHud.flag("favorite."+mod.id(),false));UnboxClient.save();refreshGrid();}));favorite.icon="favorite";favorite.selected=ClientHud.flag("favorite."+mod.id(),false);
            Action gear=body(new Action(x+9,y+ch-29,25,22,mod.title()+" settings",()->openDetail(mod.id())));gear.icon="settings";
            Action toggle=body(new Action(x+40,y+ch-29,cw-49,22,mod.id().equals("performance")?"Manage engines":"Toggle "+mod.title(),()->{if(mod.id().equals("performance"))openDetail(mod.id());else {UnboxClient.set(mod.id(),!UnboxClient.enabled(mod.id()));UnboxClient.save();}}));toggle.module=mod.id();
        }
        updateVisibility();
    }
    private void openDetail(String id){settingQuery="";detail=id;resetUndo.clear();tab="General";scroll=0;notice="";init();}
    private void initDetail(){
        button(left+17,top+15,54,27,"Back",()->{detail="";scroll=0;recording=null;notice="";init();});
        if(!detail.equals("performance")){
            Action enabled=button(left+pw-155,top+15,103,27,"Toggle "+ModOptions.mod(detail).title(),()->{UnboxClient.set(detail,!UnboxClient.enabled(detail));UnboxClient.save();});enabled.module=detail;
            int x=left+18;for(String s:ModOptions.tabs(detail)){Action b=button(x,top+60,84,26,s,()->{tab=s;scroll=0;init();});b.selected=tab.equals(s);x+=90;}
            settingSearch=addRenderableWidget(new EditBox(font,left+pw-192,top+68,172,18,PanelStyle.text("Search settings")));settingSearch.addFormatter((v,i)->PanelStyle.text(v).getVisualOrderText());settingSearch.setBordered(false);settingSearch.setTextShadow(false);settingSearch.setHint(PanelStyle.text("Search settings..."));settingSearch.setValue(settingQuery);settingSearch.setResponder(v->{settingQuery=v;scroll=0;refreshSettings();});
            button(left+pw-117,top+ph-27,99,22,resetUndo.isEmpty()?"Reset settings":"Undo reset",this::resetDetail);
        }
        bodyTop=top+101;bodyBottom=top+ph-32;refreshSettings();
    }
    private void resetDetail(){
        KeyMapping key=detail.equals("zoom")?UnboxClient.zoomKey:detail.equals("freelook")?freelookKey():detail.equals("minimap")?UnboxClient.mapKey:null;
        var cfg=Freelook.config;
        if(resetUndo.isEmpty()){
            for(String section:ModOptions.tabs(detail))for(var o:ModOptions.options(detail,section))resetUndo.put(o.key(),UnboxClient.CONFIG.getProperty(o.key(),"\u0000"));
            UnboxClient.CONFIG.forEach((k,v)->{String name=k.toString();if(name.startsWith(detail+".")&&!name.equals(detail+".x")&&!name.equals(detail+".y"))resetUndo.put(name,v.toString());});
            if(key!=null){resetUndo.put("$key",key.saveString());key.setKey(key.getDefaultKey());KeyMapping.resetMapping();minecraft.options.save();}
            if(detail.equals("freelook")){resetUndo.put("$toggle",""+cfg.isToggle());resetUndo.put("$camera",cfg.getPerspective().name());cfg.setToggle(false);cfg.setPerspective(3);cfg.save();}
            ModOptions.reset(detail);notice="Settings reset. Positions preserved.";
        }else{
            ModOptions.reset(detail);
            resetUndo.forEach((k,v)->{if(k.startsWith("$"))return;if(v.equals("\u0000"))UnboxClient.CONFIG.remove(k);else UnboxClient.set(k,v);});
            if(key!=null&&resetUndo.containsKey("$key")){key.setKey(InputConstants.getKey(resetUndo.get("$key")));KeyMapping.resetMapping();minecraft.options.save();}
            if(detail.equals("freelook")){cfg.setToggle(Boolean.parseBoolean(resetUndo.get("$toggle")));cfg.setPerspective(switch(resetUndo.get("$camera")){case "FIRST_PERSON"->1;case "THIRD_PERSON_FRONT"->2;default->3;});cfg.save();}
            resetUndo.clear();UnboxClient.save();notice="Reset undone.";
        }
        init();
    }
    private void refreshSettings(){
        clearContent();if(detail.equals("performance")){initPerformance();return;}
        int rightWidth=Math.max(192,pw/3),x=left+18,w=pw-rightWidth-47,y=bodyTop-scroll;
        List<ModOptions.Option> settings=settingQuery.isBlank()?ModOptions.options(detail,tab):ModOptions.tabs(detail).stream().flatMap(t->ModOptions.options(detail,t).stream()).filter(o->(o.label()+" "+o.key()).toLowerCase(Locale.ROOT).contains(settingQuery.toLowerCase(Locale.ROOT))).distinct().toList();
        if(detail.equals("particles")&&tab.equals("General")&&settingQuery.isBlank()){initParticleOverview(x,y,w);return;}
        for(var o:settings){
            rows.add(new Row(y,o.label(),ModOptions.available(o)));int cx=x+w/2,cw=w/2-12;
            switch(o.kind()){
                case "toggle" -> {Action a=body(new Action(x+w-59,y+10,44,23,o.label(),()->{UnboxClient.set(o.key(),!Boolean.parseBoolean(ModOptions.value(o)));UnboxClient.save();refreshSettings();}));a.option=o;}
                case "slider" -> body(new Slider(cx,y+9,cw,27,o));
                case "choice" -> body(new Choice(cx,y+9,cw,27,o));
                case "color" -> {EditBox box=body(new EditBox(font,cx+27,y+15,cw-32,19,PanelStyle.text(o.label()+" hex RGB")));box.addFormatter((v,i)->PanelStyle.text(v).getVisualOrderText());box.setBordered(false);box.setTextShadow(false);box.setMaxLength(7);box.setValue(ModOptions.value(o));box.setResponder(v->{boolean valid=v.matches("#[0-9a-fA-F]{6}");box.setTextColor(valid?PanelStyle.TEXT:0xffffad85);if(valid){UnboxClient.set(o.key(),v.toUpperCase(Locale.ROOT));UnboxClient.save();}});
                    Action swatch=body(new Action(cx,y+10,21,24,"Choose "+o.label(),()->{colorKey=o;initColor();}));swatch.option=o;}
            }for(var widget:content)if(widget.getY()>=y&&widget.getY()<y+46){widget.active=ModOptions.available(o);if(widget instanceof EditBox box&&!widget.active)box.setTextColor(0xff66737c);}y+=46;
        }
        if(settingQuery.isBlank()&&detail.equals("crosshair")&&tab.equals("Presets")){
            int cell=(w-16)/4;for(int i=0;i<CrosshairPattern.PRESETS.length;i++){int index=i;body(new Preset(x+(i%4)*(cell+4),y+(i/4)*88,cell,80,index));}y+=180;
        }
        if(settingQuery.isBlank()&&detail.equals("crosshair")&&tab.equals("Canvas")){
            body(new Canvas(left+30,y+10));y+=217;
            body(new Action(left+30,y,82,26,"Clear canvas",()->{UnboxClient.set("crosshair.pixels","0".repeat(225));UnboxClient.save();}));
            body(new Action(left+120,y,82,26,"Copy design",()->{minecraft.keyboardHandler.setClipboard(CrosshairPattern.share());notice="Crosshair copied";}));
            body(new Action(left+210,y,82,26,"Paste design",()->{notice=CrosshairPattern.importCode(minecraft.keyboardHandler.getClipboard())?"Crosshair imported":"Invalid Unbox crosshair code";}));y+=40;
        }
        if(settingQuery.isBlank()&&(detail.equals("zoom")||detail.equals("freelook")||detail.equals("minimap"))){
            KeyMapping key=detail.equals("zoom")?UnboxClient.zoomKey:detail.equals("minimap")?UnboxClient.mapKey:freelookKey();
            if(key!=null){rows.add(new Row(y,"Keybind"));Action b=body(new Action(x+w/2,y+9,w/2-12,27,"Change keybind",()->{recording=key;notice="Press a key. Esc cancels. Delete clears.";}));b.key=key;y+=46;}
        }
        if(settingQuery.isBlank()&&detail.equals("freelook")){
            var cfg=Freelook.config;
            rows.add(new Row(y,"Activation"));Action mode=body(new Action(x+w/2,y+9,w/2-12,27,cfg.isToggle()?"Toggle":"Hold",()->{cfg.setToggle(!cfg.isToggle());cfg.save();UnboxClient.set("freelook.mode",cfg.isToggle()?"Toggle":"Hold");UnboxClient.save();refreshSettings();}));y+=46;
            rows.add(new Row(y,"Camera"));body(new Action(x+w/2,y+9,w/2-12,27,cfg.getPerspective().name().replace('_',' '),()->{cfg.nextPerspective();cfg.save();UnboxClient.set("freelook.camera",cfg.getPerspective().name());UnboxClient.save();refreshSettings();}));y+=46;
        }
        maxScroll=Math.max(0,y+scroll-bodyBottom);scroll=Math.clamp(scroll,0,maxScroll);updateVisibility();
    }
    private void initParticleOverview(int x,int y,int w){
        var density=ModOptions.slider("particles.density","Density %",100,0,100);rows.add(new Row(y,"Overall density"));body(new Slider(x+w/2,y+9,w/2-12,27,density));y+=56;
        String[] titles={"Critical","Smoke","Flames","Portal","Explosions","Block","Potion","Water","Enchant","Hearts","Lava","Snow","Clouds","Totem"};
        int cw=(w-10)/2;for(int i=0;i<titles.length;i++){String title=titles[i];String family=ModOptions.family(title);int px=x+(i%2)*(cw+10),py=y+(i/2)*44;
            Action name=body(new Action(px,py,cw-53,36,"Edit "+title,()->{UnboxClient.set("particles.family",title);tab="Types";scroll=0;init();}));name.display=title+"  ›";
            var o=ModOptions.toggle("particles."+family,title,true);Action toggle=body(new Action(px+cw-49,py+6,44,23,title,()->{UnboxClient.set(o.key(),!Boolean.parseBoolean(ModOptions.value(o)));UnboxClient.save();}));toggle.option=o;
        }
        y+=((titles.length+1)/2)*44;maxScroll=Math.max(0,y+scroll-bodyBottom);scroll=Math.clamp(scroll,0,maxScroll);updateVisibility();
    }
    private KeyMapping freelookKey(){for(KeyMapping key:minecraft.options.keyMappings)if(key.getName().equals("freelook.key.activate"))return key;return null;}
    private void initPerformance(){
        int y=bodyTop-scroll;
        for(String id:List.of("sodium","lithium","immediatelyfast","entityculling","ferritecore","dynamic_fps")){
            var mod=Platform.mod(id);
            rows.add(new Row(y,mod==null?id:mod.name()));
            body(new Action(left+pw/2,y+9,pw/2-33,27,mod==null?"Not installed":mod.version(),()->{})).active=false;y+=46;
        }
        body(new Action(left+18,y+6,180,28,"Open video settings",this::openVideoSettings));y+=46;
        maxScroll=Math.max(0,y+scroll-bodyBottom);updateVisibility();
    }
    private void openVideoSettings(){
        // Sodium 0.8.9 exposes this factory; use its own controls instead of duplicating engine options.
        if(Platform.loaded("sodium")){
            try{var factory=Class.forName("net.caffeinemc.mods.sodium.client.gui.VideoSettingsScreen").getMethod("createScreen",Screen.class);minecraft.setScreen((Screen)factory.invoke(null,this));return;}
            catch(ReflectiveOperationException e){System.err.println("[Unbox] Sodium settings unavailable: "+e.getMessage());}
        }
        minecraft.setScreen(new VideoSettingsScreen(this,minecraft,minecraft.options));
    }
    private ModOptions.Option colorKey;private String colorBefore;private int colorR,colorG,colorB;private float hue,saturation,brightness;private boolean colorSync;private EditBox hexField;private ModOptions.Option numberKey;private EditBox numberField;private String numberError="";
    private void initColor(){
        colorBefore=ModOptions.value(colorKey);syncColor(colorBefore);clearWidgets();content.clear();rows.clear();
        int x=left+pw/2-215,y=top+78;
        addRenderableWidget(new ColorPlane(x,y+48,300,190));
        var hueOption=ModOptions.slider("","Hue",0,0,360);Slider hs=new Slider(x,y+252,300,27,hueOption);hs.getter=()->Math.round(hue*360);hs.setter=v->{hue=v/360f;applyHsv();};addRenderableWidget(hs);
        hexField=addRenderableWidget(new EditBox(font,x+320,y+49,100,22,PanelStyle.text("Hex color")));hexField.addFormatter((v,i)->PanelStyle.text(v).getVisualOrderText());hexField.setMaxLength(7);hexField.setValue(colorBefore);hexField.setResponder(v->{if(colorSync)return;boolean valid=v.matches("#[0-9a-fA-F]{6}");hexField.setTextColor(valid?PanelStyle.TEXT:0xffffad85);if(valid){syncColor(v);UnboxClient.set(colorKey.key(),v.toUpperCase(Locale.ROOT));}});
        String[] swatches={"#FFFFFF","#78E4B0","#7DC8FF","#BCA2FF","#FFAD85","#F4768B","#191E24","#000000"};
        for(int i=0;i<swatches.length;i++){String c=swatches[i];Action a=button(x+320+(i%2)*54,y+88+(i/2)*34,46,26,c,()->{syncColor(c);UnboxClient.set(colorKey.key(),c);syncHex();});a.swatch=Integer.parseInt(c.substring(1),16)|0xff000000;}
        button(x,y+306,100,28,"Cancel",()->{UnboxClient.set(colorKey.key(),colorBefore);UnboxClient.save();colorKey=null;init();});
        button(x+326,y+306,100,28,"Done",()->{UnboxClient.save();colorKey=null;init();}).accent=true;
    }
    private void syncColor(String value){int rgb=Integer.parseInt(value.substring(1),16);colorR=rgb>>16;colorG=(rgb>>8)&255;colorB=rgb&255;float[] hsv=java.awt.Color.RGBtoHSB(colorR,colorG,colorB,null);hue=hsv[0];saturation=hsv[1];brightness=hsv[2];}
    private void syncHex(){if(hexField!=null){colorSync=true;hexField.setValue(ClientHud.value(colorKey.key(),"#FFFFFF"));hexField.setTextColor(PanelStyle.TEXT);colorSync=false;}}
    private void applyHsv(){int rgb=java.awt.Color.HSBtoRGB(hue,saturation,brightness);UnboxClient.set(colorKey.key(),String.format(Locale.ROOT,"#%06X",rgb&0xffffff));syncHex();}
    private void initNumber(ModOptions.Option o){numberKey=o;numberError="";clearWidgets();content.clear();rows.clear();int x=left+pw/2-150,y=top+ph/2-50;
        numberField=addRenderableWidget(new EditBox(font,x,y,300,28,PanelStyle.text("Value")));numberField.addFormatter((v,i)->PanelStyle.text(v).getVisualOrderText());numberField.setMaxLength(8);numberField.setValue(ModOptions.value(o));setInitialFocus(numberField);
        button(x,y+65,100,28,"Cancel",()->{numberKey=null;init();});button(x+200,y+65,100,28,"Apply value",this::applyNumber).accent=true;
    }
    private void applyNumber(){try{int value=Integer.parseInt(numberField.getValue());if(value<numberKey.min()||value>numberKey.max())throw new NumberFormatException();UnboxClient.set(numberKey.key(),value);UnboxClient.save();numberKey=null;init();}catch(NumberFormatException e){numberError="Enter a whole number from "+numberKey.min()+" to "+numberKey.max()+".";}}
    private void updateVisibility(){for(var w:content)w.visible=w.getBottom()>bodyTop&&w.getY()<bodyBottom;}
    private Action button(int x,int y,int w,int h,String text,Runnable action){return addRenderableWidget(new Action(x,y,w,h,text,action));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        int sw=minecraft.getWindow().getGuiScaledWidth(),sh=minecraft.getWindow().getGuiScaledHeight();
        // Place HUD behind the floating settings window; controls always win overlapping input.
        for(String id:ClientHud.IDS){if(minecraft.player==null)continue;if(!editing&&(!detail.isEmpty()||!UnboxClient.enabled(id)))continue;var b=ClientHud.bounds(id,sw,sh,true);ClientHud.draw(g,id,b.x(),b.y(),true);if(!editing&&colorKey==null&&b.contains(mx,my)&&!overPanel(mx,my)){PanelStyle.outline(g,b.x()-2,b.y()-2,b.w()+4,b.h()+4,PanelStyle.ACCENT);PanelStyle.label(g,ClientHud.flag(id+".locked",false)?"Locked":"Drag to move",b.x(),b.y()+b.h()+3,PanelStyle.ACCENT);}}
        float opacity=editing?1:entrance.frame();
        g.pose().pushMatrix();g.pose().scale(menuScale);g.pose().translate(0,menuOffsetY());PanelStyle.opacity(opacity);
        try{for(var child:children())if(child instanceof EditBox box)box.setAlpha(opacity);renderPanel(g,(int)(mx/menuScale),(int)(my/menuScale-menuOffsetY()),delta);}
        finally{PanelStyle.opacity(1);g.pose().popMatrix();}
        if(dragging&&!editing){var b=ClientHud.bounds(selected,sw,sh,true);ClientHud.draw(g,selected,b.x(),b.y(),true);PanelStyle.outline(g,b.x()-2,b.y()-2,b.w()+4,b.h()+4,PanelStyle.ACCENT);}
    }
    private boolean overPanel(double x,double y){return !editing&&x>=left*menuScale&&x<=(left+pw)*menuScale&&y>=(top+menuOffsetY())*menuScale&&y<=(top+ph+menuOffsetY())*menuScale;}
    private void renderPanel(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(editing){renderEditor(g);super.extractRenderState(g,mx,my,delta);return;}
        PanelStyle.fill(g,0,0,width,height,0x30141416);PanelStyle.round(g,left-1,top-1,pw+2,ph+2,13,0x20515156);PanelStyle.round(g,left,top,pw,ph,12,0xd91c1d20);
        if(numberKey!=null){int x=left+pw/2-150,y=top+ph/2-50;PanelStyle.label(g,numberKey.label(),x,y-32,PanelStyle.TEXT);PanelStyle.label(g,numberError.isEmpty()?numberKey.min()+" - "+numberKey.max():numberError,x,y+42,numberError.isEmpty()?PanelStyle.MUTED:0xffffad85);super.extractRenderState(g,mx,my,delta);return;}
        if(colorKey!=null){
            int x=left+pw/2-215,y=top+78;PanelStyle.label(g,colorKey.label().toUpperCase(Locale.ROOT),x,y,PanelStyle.TEXT);PanelStyle.label(g,"Saturation / Brightness",x,y+29,PanelStyle.MUTED);PanelStyle.label(g,"HEX",x+320,y+29,PanelStyle.MUTED);PanelStyle.label(g,"Hue",x,y+237,PanelStyle.MUTED);
            PanelStyle.round(g,x+320,y+238,104,40,5,ClientHud.color(colorKey.key(),colorKey.fallback()));PanelStyle.label(g,"Drag to choose a color. Changes preview immediately.",x,y+287,PanelStyle.MUTED);super.extractRenderState(g,mx,my,delta);return;
        }
        if(detail.isEmpty()){
            // Original Unbox monogram; the Feather brand and assets are not shipped.
            PanelStyle.icon(g,"logo",left+14,top+9,38,0xffffffff);
            PanelStyle.label(g,"UNBOX",left+55,top+14,PanelStyle.TEXT);PanelStyle.label(g,"CLIENT MODS",left+55,top+29,PanelStyle.MUTED);
            PanelStyle.round(g,left+pw-218,top+62,200,29,6,0x993b3b40);
            if(content.isEmpty())PanelStyle.center(g,"No matching mods",width/2,top+180,PanelStyle.MUTED);
        }else {
            PanelStyle.icon(g,detail,left+83,top+16,24,PanelStyle.ACCENT);PanelStyle.label(g,ModOptions.mod(detail).title().toUpperCase(Locale.ROOT),left+118,top+16,PanelStyle.TEXT);PanelStyle.label(g,ModOptions.mod(detail).hint(),left+118,top+32,PanelStyle.MUTED);
            if(!detail.equals("performance"))PanelStyle.round(g,left+pw-205,top+60,187,29,5,0x50434348);
            int rightWidth=Math.max(192,pw/3),rowWidth=detail.equals("performance")?pw-36:pw-rightWidth-47;
            g.enableScissor(left+17,bodyTop,left+pw-17,bodyBottom);
            for(Row row:rows){PanelStyle.round(g,left+18,row.y,rowWidth,42,6,0x25444449);PanelStyle.label(g,row.label,left+30,row.y+16,row.active?PanelStyle.TEXT:0xff66737c);}
            g.disableScissor();
            if(!detail.equals("performance"))renderPreview(g,left+pw-rightWidth-18,bodyTop,rightWidth,bodyBottom-bodyTop);
        }
        if(maxScroll>0){int h=bodyBottom-bodyTop,thumb=Math.max(24,h*h/(h+maxScroll));PanelStyle.round(g,detail.isEmpty()?left+pw-8:left+pw-Math.max(192,pw/3)-30,bodyTop+(h-thumb)*scroll/maxScroll,3,thumb,1,0xff65727c);}
        String footer=!notice.isEmpty()?notice:recording!=null?"Press a key. Esc cancels. Delete clears.":detail.isEmpty()?"Drag HUD to move     ·     Right Shift to close":"Saved automatically   ·   Click a number to type";
        PanelStyle.label(g,footer,left+19,top+ph-19,PanelStyle.MUTED);
        for(var child:children()){
            if(!(child instanceof AbstractWidget renderable))continue;
            boolean clipped=content.contains(renderable);
            if(clipped)g.enableScissor(left+16,bodyTop,left+pw-12,bodyBottom);
            renderable.extractRenderState(g,mx,my,delta);
            if(clipped)g.disableScissor();
        }
        if(expandedChoice!=null){int x=expandedChoice.getX(),y=dropdownY(),w=expandedChoice.getWidth();PanelStyle.round(g,x-3,y-3,w+6,expandedChoice.option.choices().length*24+6,6,0xff101820);for(int i=0;i<expandedChoice.option.choices().length;i++){boolean hover=mx>=x&&mx<x+w&&my>=y+i*24&&my<y+(i+1)*24;if(i==dropdownIndex||hover)PanelStyle.round(g,x,y+i*24,w,24,4,0xff3b4e75);PanelStyle.label(g,expandedChoice.option.choices()[i],x+10,y+i*24+7,PanelStyle.TEXT);}}
    }
    private int dropdownY(){return Math.min(expandedChoice.getY()+expandedChoice.getHeight()+3,top+ph-expandedChoice.option.choices().length*24-12);}
    private void renderPreview(GuiGraphicsExtractor g,int x,int y,int w,int h){
        PanelStyle.round(g,x,y,w,h,8,0x60101216);PanelStyle.label(g,detail.equals("crosshair")||detail.equals("chat")||detail.equals("particles")||ClientHud.IDS.contains(detail)?"LIVE PREVIEW":"ABOUT",x+15,y+15,PanelStyle.MUTED);
        if(detail.equals("armor")&&minecraft.level==null){PanelStyle.center(g,"Join a world to preview equipment",x+w/2,y+85,PanelStyle.MUTED);return;}
        if(detail.equals("crosshair")){int cy=y+108;int[] scene={0xff56664c,0xffd7d0be,0xff172231};g.fillGradient(x+12,y+43,x+w-12,y+177,scene[previewScene],PanelStyle.mix(scene[previewScene],0xff000000,.25f));UnboxClient.drawCrosshair(g,x+w/2,cy);PanelStyle.center(g,"Click preview to change contrast",x+w/2,y+191,PanelStyle.MUTED);PanelStyle.center(g,ClientHud.value("crosshair.shape","Cross"),x+w/2,y+216,PanelStyle.TEXT);}
        else if(detail.equals("particles")){
            String family="particles."+ModOptions.particleFamily();int density=UnboxClient.number("particles.density",100,0,100)*UnboxClient.number(family+".density",100,0,100)/100;int count=ClientHud.flag(family,true)?density*28/100:0;
            float time=(System.nanoTime()%20_000_000_000L)/1e9f;int size=Math.max(1,UnboxClient.number(family+".size",100,25,200)/40);int color=ClientHud.flag(family+".tint",false)?ClientHud.color(family+".color","#FFFFFF"):PanelStyle.ACCENT;
            for(int i=0;i<count;i++){float phase=(time/(UnboxClient.number(family+".lifetime",100,25,200)/50f)+i*.137f)%1;int px=x+25+(i*43)%Math.max(1,w-50),py=y+125-(int)(phase*65);PanelStyle.round(g,px,py,size,size,size/2,((int)((1-phase)*220*UnboxClient.number(family+".opacity",100,0,100)/100f)<<24)|(color&0xffffff));}
            PanelStyle.label(g,"Sample · "+ClientHud.value("particles.family","Critical"),x+13,y+150,PanelStyle.TEXT);PanelStyle.label(g,"New particles use these settings.",x+13,y+172,PanelStyle.MUTED);
        }
        else if(ClientHud.IDS.contains(detail)){
            int[] size=ClientHud.size(detail,true);float fit=Math.min(1.8f,Math.min((w-28f)/Math.max(1,size[0]*ClientHud.scale(detail)*ClientHud.unit()),(h-90f)/Math.max(1,size[1]*ClientHud.scale(detail)*ClientHud.unit())));g.enableScissor(x+8,y+36,x+w-8,y+h-38);g.pose().pushMatrix();g.pose().translate(x+(w-size[0]*ClientHud.scale(detail)*ClientHud.unit()*fit)/2f,y+60);g.pose().scale(fit);ClientHud.draw(g,detail,0,0,true);g.pose().popMatrix();g.disableScissor();PanelStyle.label(g,"Click preview to edit HUD layout",x+14,y+h-28,PanelStyle.MUTED);
        }else {
            if(!detail.equals("chat"))PanelStyle.icon(g,detail,x+w/2-16,y+70,PanelStyle.ACCENT);
            List<String> notes=switch(detail){
                case "particles"->List.of("Changes affect new particles.","Existing particles fade normally.");
                case "zoom"->List.of("Zoom changes your field of view.","Your movement stays unchanged.");
                case "freelook"->List.of("Uses the installed Freelook mod.","Server restrictions are respected.");
                case "cape"->List.of("Displays existing OptiFine capes.","Official capes keep priority.","Player names are sent to OptiFine.");
                case "sprint"->List.of("Sprints while moving forward.","Hunger and collisions still apply.");
                case "chat"->List.of("Sample messages", "Chat appearance stays personal.");
                default->List.of();};
            for(int i=0;i<notes.size();i++)PanelStyle.label(g,notes.get(i),x+13,y+135+i*17,PanelStyle.MUTED);
            if(detail.equals("chat")){int a=UnboxClient.number("chat.opacity",50,0,100)*255/100;g.enableScissor(x+12,y+44,x+w-12,y+126);g.pose().pushMatrix();g.pose().translate(x+12,y+46);float scale=UnboxClient.number("chat.scale",100,50,100)/100f;g.pose().scale(scale);int cw=(int)((w-24)/scale*UnboxClient.number("chat.width",100,20,100)/100f),spacing=12+UnboxClient.number("chat.spacing",0,0,100)/8;String[] sample={"<Alex> Anyone joining?","<Steve> On my way!","Alex joined the game"};for(int i=0;i<3;i++){PanelStyle.fill(g,0,i*spacing,cw,i*spacing+spacing,(a<<24)|(ClientHud.color("chat.color","#000000")&0xffffff));PanelStyle.label(g,sample[i],4,i*spacing+2,((UnboxClient.number("chat.textOpacity",100,10,100)*255/100)<<24)|0xffffff);}g.pose().popMatrix();g.disableScissor();}
        }
    }
    private class Action extends AbstractWidget {
        final PanelStyle.Motion hover=new PanelStyle.Motion();
        final Runnable action;boolean accent,selected;String display="",icon="",module="";ModOptions.Option option;KeyMapping key;int swatch;
        Action(int x,int y,int w,int h,String label,Runnable run){super(x,y,w,h,PanelStyle.text(label));action=run;setTooltip(Tooltip.create(Component.literal(label)));}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            boolean on=!module.isEmpty()&&!module.equals("performance")&&UnboxClient.enabled(module);
            boolean flag=option!=null&&option.kind().equals("toggle")&&Boolean.parseBoolean(ModOptions.value(option));
            int bg=PanelStyle.mix(accent||selected?0xff3b4e75:on?0xb33a4c70:0x78303a43,0xc34a565f,hover.to(isHoveredOrFocused()?1:0)*.55f);
            if(option!=null&&option.kind().equals("color")){PanelStyle.round(g,getX(),getY(),width,height,4,active?ClientHud.color(option.key(),option.fallback()):PanelStyle.mix(ClientHud.color(option.key(),option.fallback()),0xff20272d,.75f));return;}
            if(swatch!=0){PanelStyle.round(g,getX(),getY(),width,height,5,swatch);return;}
            PanelStyle.round(g,getX(),getY(),width,height,5,bg);
            if(isFocused())PanelStyle.outline(g,getX(),getY(),width,height,PanelStyle.ACCENT);
            if(option!=null&&option.kind().equals("toggle")){PanelStyle.round(g,getX()+3,getY()+6,width-6,12,6,flag?0xff86aaff:0xff5b626a);PanelStyle.round(g,getX()+5+Math.round((width-20)*toggleMotions.computeIfAbsent(option.key(),k->new PanelStyle.Motion()).to(flag?1:0)),getY()+8,8,8,4,0xfff5faf7);return;}
            if(!icon.isEmpty()){
                PanelStyle.icon(g,icon,getX()+width/2-7,getY()+height/2-7,14,icon.equals("close")&&isHoveredOrFocused()?0xffff9098:selected?PanelStyle.ACCENT:PanelStyle.MUTED);return;
            }
            String label=option!=null?ModOptions.value(option):key!=null?(recording==key?"Press a key...":key.getTranslatedKeyMessage().getString()):!module.isEmpty()?(module.equals("performance")?"Configure":on?"Enabled":"Disabled"):display.isEmpty()?getMessage().getString():display;
            PanelStyle.center(g,label,getX()+width/2,getY()+(height-10)/2,active?(on?0xffc5d5ff:PanelStyle.TEXT):PanelStyle.MUTED);
        }
        @Override public void onClick(MouseButtonEvent e,boolean doubled){action.run();}
        @Override public boolean keyPressed(KeyEvent e){if(e.isConfirmation()){action.run();return true;}return super.keyPressed(e);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
    private class Card extends AbstractWidget {
        final PanelStyle.Motion hover=new PanelStyle.Motion();final ModOptions.Mod mod;
        Card(int x,int y,int w,int h,ModOptions.Mod mod){super(x,y,w,h,PanelStyle.text(mod.title()));this.mod=mod;}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float d){PanelStyle.round(g,getX(),getY(),width,height,7,PanelStyle.mix(0x7a29333b,0xb83a454d,hover.to(isHoveredOrFocused()?1:0)));PanelStyle.label(g,mod.title(),getX()+12,getY()+12,PanelStyle.TEXT);PanelStyle.icon(g,mod.id(),getX()+width/2-16,getY()+37,UnboxClient.enabled(mod.id())||mod.id().equals("performance")?0xffd2e4dd:0xff7e8c92);if(isFocused())PanelStyle.outline(g,getX(),getY(),width,height,PanelStyle.ACCENT);}
        @Override public boolean isMouseOver(double x,double y){if(y>getY()+height-33||x>getX()+width-29&&y<getY()+30)return false;return super.isMouseOver(x,y);}
        @Override public void onClick(MouseButtonEvent e,boolean doubled){openDetail(mod.id());}
        @Override public boolean keyPressed(KeyEvent e){if(e.isConfirmation()){openDetail(mod.id());return true;}return super.keyPressed(e);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
    private class Preset extends AbstractWidget {
        final int index;Preset(int x,int y,int w,int h,int index){super(x,y,w,h,PanelStyle.text(CrosshairPattern.PRESETS[index]));this.index=index;}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float d){String pixels=CrosshairPattern.preset(index);boolean selected=ClientHud.value("crosshair.shape","").equals("Custom")&&CrosshairPattern.pixels().equals(pixels);PanelStyle.round(g,getX(),getY(),width,height,7,selected?0x99506387:isHoveredOrFocused()?0x993b4955:0x66303a43);int cx=getX()+width/2,cy=getY()+32;for(int py=0;py<15;py++)for(int px=0;px<15;px++)if(pixels.charAt(py*15+px)=='1')PanelStyle.fill(g,cx+(px-7)*2,cy+(py-7)*2,cx+(px-7)*2+2,cy+(py-7)*2+2,PanelStyle.TEXT);PanelStyle.center(g,getMessage().getString(),cx,getY()+height-19,selected?PanelStyle.ACCENT:PanelStyle.TEXT);}
        @Override public void onClick(MouseButtonEvent e,boolean d){CrosshairPattern.applyPreset(index);notice="Preset applied. Open Canvas to edit.";}
        @Override public boolean keyPressed(KeyEvent e){if(e.isConfirmation()){CrosshairPattern.applyPreset(index);return true;}return super.keyPressed(e);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
    private class ColorPlane extends AbstractWidget {
        ColorPlane(int x,int y,int w,int h){super(x,y,w,h,PanelStyle.text("Saturation and brightness"));}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float d){for(int i=0;i<64;i++){int start=getX()+i*width/64,end=getX()+(i+1)*width/64;g.fillGradient(start,getY(),end,getY()+height,java.awt.Color.HSBtoRGB(hue,i/63f,1),0xff000000);}int x=getX()+Math.round(saturation*(width-1)),y=getY()+Math.round((1-brightness)*(height-1));PanelStyle.outline(g,x-4,y-4,9,9,0xff000000);PanelStyle.outline(g,x-3,y-3,7,7,0xffffffff);}
        void update(double x,double y){saturation=(float)Math.clamp((x-getX())/(width-1),0,1);brightness=1-(float)Math.clamp((y-getY())/(height-1),0,1);applyHsv();}
        @Override public void onClick(MouseButtonEvent e,boolean d){update(e.x(),e.y());}
        @Override protected void onDrag(MouseButtonEvent e,double dx,double dy){update(e.x(),e.y());}
        @Override public boolean keyPressed(KeyEvent e){float step=e.hasShiftDown()?.1f:.01f;if(e.key()==GLFW.GLFW_KEY_LEFT||e.key()==GLFW.GLFW_KEY_RIGHT){saturation=Math.clamp(saturation+(e.key()==GLFW.GLFW_KEY_LEFT?-step:step),0,1);applyHsv();return true;}if(e.key()==GLFW.GLFW_KEY_UP||e.key()==GLFW.GLFW_KEY_DOWN){brightness=Math.clamp(brightness+(e.key()==GLFW.GLFW_KEY_UP?step:-step),0,1);applyHsv();return true;}return super.keyPressed(e);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
    private class Canvas extends AbstractWidget {
        boolean paint;
        Canvas(int x,int y){super(x,y,195,195,PanelStyle.text("Crosshair canvas"));setTooltip(Tooltip.create(Component.literal("Left-click to draw. Right-click to erase. Drag to paint.")));}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float d){String p=CrosshairPattern.pixels();for(int y=0;y<15;y++)for(int x=0;x<15;x++){int c=p.charAt(y*15+x)=='1'?ClientHud.color("crosshair.color","#FFFFFF"):x==7||y==7?0xff374a4b:0xff26343b;PanelStyle.fill(g,getX()+x*13,getY()+y*13,getX()+x*13+12,getY()+y*13+12,c);}}
        @Override protected boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo button){return button.button()==0||button.button()==1;}
        void paint(MouseButtonEvent e){CrosshairPattern.paint((int)(e.x()-getX())/13,(int)(e.y()-getY())/13,paint);}
        @Override public void onClick(MouseButtonEvent e,boolean d){int x=(int)(e.x()-getX())/13,y=(int)(e.y()-getY())/13;paint=e.button()==0&&CrosshairPattern.pixels().charAt(y*15+x)!='1';paint(e);}
        @Override protected void onDrag(MouseButtonEvent e,double dx,double dy){if(e.x()>=getX()&&e.y()>=getY())paint(e);}
        @Override public void onRelease(MouseButtonEvent e){UnboxClient.save();}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
    private class Choice extends AbstractWidget {
        final ModOptions.Option option;
        Choice(int x,int y,int w,int h,ModOptions.Option o){super(x,y,w,h,PanelStyle.text(o.label()));option=o;}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float d){
            PanelStyle.round(g,getX(),getY(),width,height,5,0x78303a43);int n=option.choices().length;if(n>3){PanelStyle.center(g,ModOptions.value(option)+"  ...",getX()+width/2,getY()+8,PanelStyle.TEXT);return;}
            for(int i=0;i<n;i++){int x=getX()+i*width/n,w=width/n;boolean selected=ModOptions.value(option).equals(option.choices()[i]);if(selected)PanelStyle.round(g,x+1,getY()+1,w-2,height-2,4,0xff3b4e75);PanelStyle.center(g,option.choices()[i],x+w/2,getY()+8,selected?PanelStyle.TEXT:PanelStyle.MUTED);}
            if(isFocused())PanelStyle.outline(g,getX(),getY(),width,height,PanelStyle.ACCENT);
        }
        private void choose(int index){UnboxClient.set(option.key(),option.choices()[Math.floorMod(index,option.choices().length)]);UnboxClient.save();refreshSettings();}
        @Override public void onClick(MouseButtonEvent e,boolean d){if(option.choices().length>3){expandedChoice=this;dropdownIndex=Math.max(0,Arrays.asList(option.choices()).indexOf(ModOptions.value(option)));}else choose(Math.min(option.choices().length-1,(int)(e.x()-getX())*option.choices().length/width));}
        @Override public boolean keyPressed(KeyEvent e){if(e.isConfirmation()&&option.choices().length>3){expandedChoice=this;dropdownIndex=Math.max(0,Arrays.asList(option.choices()).indexOf(ModOptions.value(option)));return true;}int index=Arrays.asList(option.choices()).indexOf(ModOptions.value(option));if(e.key()==GLFW.GLFW_KEY_LEFT){choose(index-1);return true;}if(e.key()==GLFW.GLFW_KEY_RIGHT||e.isConfirmation()){choose(index+1);return true;}return super.keyPressed(e);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
    private class Slider extends AbstractWidget {
        final ModOptions.Option option;IntSupplier getter;IntConsumer setter;
        Slider(int x,int y,int w,int h,ModOptions.Option o){super(x,y,w,h,PanelStyle.text(o.label()));option=o;setTooltip(Tooltip.create(Component.literal("Drag or use arrow keys. Click the number to type. Right-click to reset.")));getter=()->UnboxClient.number(o.key(),Integer.parseInt(o.fallback()),o.min(),o.max());setter=v->{UnboxClient.set(o.key(),v);};}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float d){int span=width-43,cy=getY()+height/2,n=getter.getAsInt(),px=getX()+(n-option.min())*span/(option.max()-option.min());if(option.label().equals("Hue")){for(int i=0;i<span;i++)PanelStyle.fill(g,getX()+i,cy-4,getX()+i+1,cy+4,java.awt.Color.HSBtoRGB(i/(float)span,1,1));}else {PanelStyle.round(g,getX(),cy-2,span,4,2,0xff526068);PanelStyle.round(g,getX(),cy-2,Math.max(1,px-getX()),4,2,PanelStyle.ACCENT);}PanelStyle.round(g,px-4,cy-5,8,10,4,0xffedf2ff);PanelStyle.round(g,getX()+width-37,getY()+1,37,height-2,4,0x80434f59);PanelStyle.center(g,""+n,getX()+width-18,getY()+8,PanelStyle.TEXT);if(isFocused())PanelStyle.outline(g,getX()-3,getY(),width+6,height,PanelStyle.ACCENT);}
        private void update(double x){int n=option.min()+(int)Math.round(Math.clamp((x-getX())/(width-43),0,1)*(option.max()-option.min()));setter.accept(n);}
        @Override public void onClick(MouseButtonEvent e,boolean d){if(!option.key().isEmpty()&&e.x()>=getX()+width-38)initNumber(option);else update(e.x());}
        @Override protected void onDrag(MouseButtonEvent e,double dx,double dy){update(e.x());}
        @Override public void onRelease(MouseButtonEvent e){UnboxClient.save();}
        @Override public boolean keyPressed(KeyEvent e){int n=getter.getAsInt();if(e.key()==GLFW.GLFW_KEY_LEFT||e.key()==GLFW.GLFW_KEY_RIGHT){setter.accept(Math.clamp(n+(e.key()==GLFW.GLFW_KEY_LEFT?-1:1),option.min(),option.max()));UnboxClient.save();return true;}if(e.key()==GLFW.GLFW_KEY_HOME||e.key()==GLFW.GLFW_KEY_END){setter.accept(e.key()==GLFW.GLFW_KEY_HOME?option.min():option.max());UnboxClient.save();return true;}return super.keyPressed(e);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
    private void startEditor(){if(minecraft.player==null)return;hudBefore.clear();for(String id:ClientHud.IDS)for(String suffix:List.of(".x",".y",".scale")){String key=id+suffix;hudBefore.setProperty(key,UnboxClient.CONFIG.getProperty(key,"\u0000"));}editing=true;init();}
    private void initEditor(){int x=width/2-191,y=height-43;button(x,y,69,27,"Cancel",()->finishEditor(false));button(x+77,y,82,27,snap?"Snap: ON":"Snap: OFF",()->{snap=!snap;init();});button(x+167,y,98,27,"Reset position",()->{UnboxClient.CONFIG.remove(selected+".x");UnboxClient.CONFIG.remove(selected+".y");UnboxClient.CONFIG.remove(selected+".scale");});button(x+273,y,108,27,"Done",()->finishEditor(true)).accent=true;}
    private void finishEditor(boolean save){if(!save)hudBefore.forEach((k,v)->{if(v.equals("\u0000"))UnboxClient.CONFIG.remove(k);else UnboxClient.CONFIG.put(k,v);});UnboxClient.save();editing=false;init();}
    private void renderEditor(GuiGraphicsExtractor g){
        PanelStyle.round(g,width/2-193,12,386,33,7,0xd6202024);PanelStyle.center(g,"Edit HUD   /   Drag to move · Corner to resize · Arrows to nudge",width/2,23,PanelStyle.TEXT);
        for(String id:ClientHud.IDS){var b=ClientHud.bounds(id,width,height,true);int c=id.equals(selected)?PanelStyle.ACCENT:0x887e909a;PanelStyle.outline(g,b.x()-2,b.y()-2,b.w()+4,b.h()+4,c);PanelStyle.fill(g,b.x()+b.w()-3,b.y()+b.h()-3,b.x()+b.w()+4,b.y()+b.h()+4,c);PanelStyle.label(g,ModOptions.mod(id).title(),b.x(),b.y()+b.h()+5,c);}
        if(dragging&&snap){PanelStyle.fill(g,width/2,45,width/2,height-48,0x3086aaff);PanelStyle.fill(g,0,height/2,width,height/2+1,0x3086aaff);}
        PanelStyle.round(g,width/2-200,height-50,400,40,8,0xe1202024);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){
        if(expandedChoice!=null){var input=scaledMouse(e);int x=expandedChoice.getX(),y=dropdownY(),index=(int)(input.y()-y)/24;Choice choice=expandedChoice;expandedChoice=null;if(input.x()>=x&&input.x()<x+choice.getWidth()&&input.y()>=y&&index<choice.option.choices().length)choice.choose(index);return true;}
        if(!editing&&detail.isEmpty()&&!overPanel(e.x(),e.y())&&colorKey==null&&recording==null&&e.button()==0){for(String id:ClientHud.IDS.reversed()){if(!UnboxClient.enabled(id)||ClientHud.flag(id+".locked",false))continue;var b=ClientHud.bounds(id,minecraft.getWindow().getGuiScaledWidth(),minecraft.getWindow().getGuiScaledHeight(),true);if(b.contains(e.x(),e.y())){selected=id;dragging=true;resizing=false;dragX=(int)e.x()-b.x();dragY=(int)e.y()-b.y();return true;}}}
        e=scaledMouse(e);
        if(!editing&&colorKey==null&&numberKey==null&&!detail.isEmpty()){int rw=Math.max(192,pw/3),px=left+pw-rw-18;if(e.x()>=px&&e.x()<=px+rw&&e.y()>=bodyTop&&e.y()<=bodyBottom){if(detail.equals("crosshair")){previewScene=(previewScene+1)%3;return true;}if(ClientHud.IDS.contains(detail)){startEditor();return true;}}}
        if(editing&&e.button()==0){for(String id:ClientHud.IDS.reversed()){if(ClientHud.flag(id+".locked",false))continue;var b=ClientHud.bounds(id,width,height,true);if(new ClientHud.Bounds(b.x()-3,b.y()-3,b.w()+9,b.h()+9).contains(e.x(),e.y())){selected=id;dragging=true;resizing=e.x()>b.x()+b.w()-8&&e.y()>b.y()+b.h()-8;dragX=(int)e.x()-b.x();dragY=(int)e.y()-b.y();return true;}}}
        if(recording!=null){recording=null;notice="Keybind unchanged.";}
        if(!editing&&colorKey==null&&numberKey==null&&e.button()==1&&e.y()>=bodyTop&&e.y()<bodyBottom){for(var widget:content){if(!widget.isMouseOver(e.x(),e.y()))continue;ModOptions.Option o=widget instanceof Slider v?v.option:widget instanceof Choice v?v.option:widget instanceof Action v?v.option:null;if(o!=null){UnboxClient.CONFIG.remove(o.key());UnboxClient.save();notice=o.label()+" reset to default.";refreshSettings();return true;}}}
        for(var w:content)if(w.isMouseOver(e.x(),e.y())&&(e.y()<bodyTop||e.y()>bodyBottom))return false;
        return super.mouseClicked(e,doubled);
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){
        if(!editing&&dragging){int sw=minecraft.getWindow().getGuiScaledWidth(),sh=minecraft.getWindow().getGuiScaledHeight();var b=ClientHud.bounds(selected,sw,sh,true);UnboxClient.set(selected+".x",Math.round(Math.clamp((int)e.x()-dragX,0,Math.max(0,sw-b.w()))/ClientHud.unit()));UnboxClient.set(selected+".y",Math.round(Math.clamp((int)e.y()-dragY,0,Math.max(0,sh-b.h()))/ClientHud.unit()));return true;}
        e=scaledMouse(e);dx/=menuScale;dy/=menuScale;
        if(editing&&dragging){var b=ClientHud.bounds(selected,width,height,true);if(resizing){int current=UnboxClient.number(selected+".scale",100,50,200);int n=(int)Math.round(current*Math.max((e.x()-b.x())/Math.max(1,b.w()),(e.y()-b.y())/Math.max(1,b.h())));UnboxClient.set(selected+".scale",Math.clamp(n,50,200));}
            else moveHud((int)e.x()-dragX,(int)e.y()-dragY);return true;}return super.mouseDragged(e,dx,dy);
    }
    private void moveHud(int x,int y){var b=ClientHud.bounds(selected,width,height,true);if(snap){x=Math.round(x/4f)*4;y=Math.round(y/4f)*4;if(Math.abs(x+b.w()/2-width/2)<7)x=(width-b.w())/2;if(Math.abs(y+b.h()/2-height/2)<7)y=(height-b.h())/2;}UnboxClient.set(selected+".x",Math.round(Math.clamp(x,0,Math.max(0,width-b.w()))/ClientHud.unit()));UnboxClient.set(selected+".y",Math.round(Math.clamp(y,0,Math.max(0,height-b.h()))/ClientHud.unit()));}
    @Override public boolean mouseReleased(MouseButtonEvent e){if(dragging&&!editing)UnboxClient.save();dragging=false;resizing=false;return super.mouseReleased(scaledMouse(e));}
    private MouseButtonEvent scaledMouse(MouseButtonEvent e){return new MouseButtonEvent(e.x()/menuScale,e.y()/menuScale-menuOffsetY(),e.buttonInfo());}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(expandedChoice!=null){dropdownIndex=Math.clamp(dropdownIndex-(int)vertical,0,expandedChoice.option.choices().length-1);return true;}x/=menuScale;y=y/menuScale-menuOffsetY();if(!editing&&colorKey==null&&numberKey==null&&x>=left&&x<=left+pw&&y>=bodyTop&&y<=bodyBottom){int next=Math.clamp(scroll-(int)(vertical*36),0,maxScroll);if(next!=scroll){scroll=next;if(detail.isEmpty())refreshGrid();else refreshSettings();}return true;}return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(KeyEvent e){
        if(numberKey!=null&&e.isConfirmation()){applyNumber();return true;}
        if(expandedChoice!=null){if(e.key()==GLFW.GLFW_KEY_ESCAPE){expandedChoice=null;return true;}if(e.key()==GLFW.GLFW_KEY_DOWN||e.key()==GLFW.GLFW_KEY_UP){dropdownIndex=Math.floorMod(dropdownIndex+(e.key()==GLFW.GLFW_KEY_DOWN?1:-1),expandedChoice.option.choices().length);return true;}if(e.isConfirmation()){Choice choice=expandedChoice;expandedChoice=null;choice.choose(dropdownIndex);return true;}}
        if(recording!=null){if(e.key()==GLFW.GLFW_KEY_ESCAPE){recording=null;notice="Keybind unchanged.";return true;}var candidate=e.key()==GLFW.GLFW_KEY_DELETE?InputConstants.UNKNOWN:InputConstants.Type.KEYSYM.getOrCreate(e.key());
            if(e.key()!=GLFW.GLFW_KEY_DELETE){for(KeyMapping other:minecraft.options.keyMappings)if(other!=recording&&other.matches(e)){other.setKey(InputConstants.UNKNOWN);}}
            recording.setKey(candidate);KeyMapping.resetMapping();minecraft.options.save();UnboxClient.set(detail+".key",candidate.getValue());UnboxClient.save();recording=null;notice="Keybind saved.";return true;}
        if(e.key()==GLFW.GLFW_KEY_RIGHT_SHIFT){if(numberKey!=null||colorKey!=null||editing){onClose();return true;}UnboxClient.save();minecraft.setScreen(minecraft.level==null?parent:null);return true;}
        if(editing&&!ClientHud.flag(selected+".locked",false)){var b=ClientHud.bounds(selected,width,height,true);int dx=e.key()==GLFW.GLFW_KEY_LEFT?-1:e.key()==GLFW.GLFW_KEY_RIGHT?1:0,dy=e.key()==GLFW.GLFW_KEY_UP?-1:e.key()==GLFW.GLFW_KEY_DOWN?1:0;if(dx!=0||dy!=0){boolean previous=snap;snap=false;moveHud(b.x()+dx,b.y()+dy);snap=previous;return true;}}
        return super.keyPressed(e);
    }
    @Override public void onClose(){
        recording=null;
        if(numberKey!=null){numberKey=null;init();return;}
        if(colorKey!=null){UnboxClient.set(colorKey.key(),colorBefore);UnboxClient.save();colorKey=null;init();return;}
        if(editing){finishEditor(false);return;}
        UnboxClient.save();minecraft.setScreen(parent);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){if(minecraft.level==null)extractPanorama(g,delta);if(!editing)extractBlurredBackground(g);minecraft.gui.extractDeferredSubtitles();}
    @Override public boolean isPauseScreen(){return false;}
}
