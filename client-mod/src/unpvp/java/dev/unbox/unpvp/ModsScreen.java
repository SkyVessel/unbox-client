package dev.unbox.unpvp;

import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Keyboard;
import java.util.*;
import java.util.function.*;

/** Modern Unbox controls shared by live HUD settings and the remaining previews. */
final class ModsScreen extends UiScreen {
    int left,top,pw,ph,bodyTop,bodyBottom,scroll,maxScroll;String query="",category="All",detail="",tab="General",settingQuery="",notice="";
    private final List<Row> rows=new ArrayList<>();private Field search,settingSearch;private final Properties undoReset=new Properties();
    private Choice dropdown;private int choiceIndex;private UiState.Option colorOption,numberOption;private String colorBefore,numberError="",recording="";private Field hex,number;
    private float hue,saturation,brightness;private boolean syncing;
    private int previewScene;private final Deque<String> canvasUndo=new ArrayDeque<>();
    private boolean editing,snap=true,dragHud,resizeHud;private String selected="fps";private float dragX,dragY;private final Properties hudBefore=new Properties();
    ModsScreen(GuiScreen parent){super(parent);}
    ModsScreen(GuiScreen parent,String id){super(parent);detail=id;}
    @Override void layout(){
        net.minecraft.client.gui.ScaledResolution r=new net.minecraft.client.gui.ScaledResolution(mc);int selectedScale=r.getScaleFactor()/RetinaDisplay.scale(),auto=1;int wp=mc.displayWidth/RetinaDisplay.scale(),hp=mc.displayHeight/RetinaDisplay.scale();while(wp/(auto+1)>=320&&hp/(auto+1)>=240)auto++;
        SolidLayout v=new SolidLayout("mods",mc.displayWidth,mc.displayHeight,Math.sqrt(Math.min(1,selectedScale/(double)auto)));
        double metrics=Math.max(1.25,Math.min(1.4/v.pixels,(v.width-428)/760));scale=editing?1:(float)(v.pixels*width/mc.displayWidth*metrics);logicalWidth=Math.round(width/scale);logicalHeight=Math.round(height/scale);
        left=(int)Math.round(244/metrics);top=(int)Math.round(166/metrics);pw=(int)Math.round((v.width-428)/metrics);ph=(int)Math.round((v.height-256)/metrics);rows.clear();dropdown=null;
        if(editing)return;
        if(colorOption!=null){colorControls();return;}if(numberOption!=null){numberControls();return;}
        button(left+pw-43,top+15,28,27,"Close",this::close).icon="close";
        if(detail.isEmpty())grid();else details();
    }
    private Button bodyButton(float x,float y,float w,float h,String text,Runnable run){Button b=button(x,y,w,h,text,run);b.clip=true;return b;}
    private void grid(){
        button(left+pw-155,top+15,103,27,"Edit HUD",this::editHud).active=mc.theWorld!=null;
        search=field(left+pw-207,top+69,179,20,query,"Search mods...",s->{query=s;scroll=0;refreshBody();});
        int x=left+18;for(final String name:new String[]{"All","HUD","Visuals","Controls","Favorites"}){int w=name.equals("Favorites")?68:name.equals("Controls")?65:50;Button b=button(x,top+62,w,29,name,()->{category=name;scroll=0;initGui();});b.selected=name.equals(category);x+=w+4;}
        bodyTop=top+108;bodyBottom=top+ph-31;gridBody();
    }
    private void gridBody(){
        List<UiState.Module> matches=new ArrayList<>();for(UiState.Module m:UiState.MODULES)if((m.title+" "+m.hint).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))&&(category.equals("All")||category.equals(m.group)||category.equals("Favorites")&&UiState.flag("favorite."+m.id,false)))matches.add(m);
        Collections.sort(matches,Comparator.comparing(m->!UiState.flag("favorite."+m.id,false)));
        int cols=pw>=660?5:4,cw=(pw-36-(cols-1)*12)/cols,ch=cw;
        maxScroll=Math.max(0,((matches.size()+cols-1)/cols)*(ch+12)-12-(bodyBottom-bodyTop));scroll=UiState.clamp(scroll,0,maxScroll);
        for(int i=0;i<matches.size();i++){
            final UiState.Module m=matches.get(i);int x=left+18+i%cols*(cw+12),y=bodyTop+i/cols*(ch+12)-scroll;
            controls.add(new Card(x,y,cw,ch-30,m));Button star=bodyButton(x+cw-26,y+7,20,20,"Favorite "+m.title,()->{UiState.set("favorite."+m.id,!UiState.flag("favorite."+m.id,false));UiState.save();refreshBody();});star.icon="favorite";star.selected=UiState.flag("favorite."+m.id,false);
            Button toggle=bodyButton(x,y+ch-30,cw,30,"Toggle "+m.title,()->{if(m.id.equals("performance"))openDetail(m.id);else{UiState.set(m.id,!UiState.on(m.id));UiState.save();}});toggle.module=m.id;toggle.strip=true;
        }
    }
    void openDetail(String id){detail=id;tab="General";scroll=0;settingQuery="";notice="";undoReset.clear();initGui();}
    private void details(){
        UiState.Module m=UiState.module(detail);button(left+17,top+15,54,27,"Back",this::close);
        if(!detail.equals("performance")){
            button(left+pw-155,top+15,103,27,"Toggle "+m.title,()->{UiState.set(detail,!UiState.on(detail));UiState.save();}).module=detail;
            int x=left+18;for(final String name:m.tabs.keySet()){Button b=button(x,top+60,84,26,name,()->{tab=name;scroll=0;initGui();});b.selected=tab.equals(name);x+=90;}
            settingSearch=field(left+pw-192,top+68,172,18,settingQuery,"Search settings...",s->{settingQuery=s;scroll=0;refreshBody();});
            button(left+pw-117,top+ph-27,99,22,undoReset.isEmpty()?"Reset settings":"Undo reset",this::reset);
        }
        bodyTop=top+101;bodyBottom=top+ph-32;settingsBody();
    }
    private void keyRow(int x,int y,int w,String label,String id){rows.add(new Row(y,label));Button b=bodyButton(x+w/2,y+8,w/2-12,28,"Change "+label,()->{recording=id;notice="Press a key or mouse button. Esc cancels. Delete clears.";});b.framed=true;b.display=()->recording.equals(id)?"Press a key...":UiKeys.label(id);}
    private void saveKey(int code){int replaced=UiKeys.assign(recording,code);recording="";notice=replaced==0?"Keybind saved.":"Keybind saved. Previous binding cleared.";}
    private void reset(){if(undoReset.isEmpty()){for(Object k:UiState.VALUES.keySet())if(k.toString().startsWith(detail+"."))undoReset.put(k,UiState.VALUES.get(k));undoReset.setProperty("$snapshot","1");UiState.reset(detail);notice="Settings reset. Positions preserved.";}else{UiState.reset(detail);for(Object k:undoReset.keySet())if(!k.toString().startsWith("$"))UiState.VALUES.put(k,undoReset.get(k));undoReset.clear();notice="Reset undone.";}UiState.save();initGui();}
    private void refreshBody(){controls.removeIf(c->c.clip);rows.clear();dropdown=null;if(detail.isEmpty())gridBody();else settingsBody();}
    private void settingsBody(){
        int rightWidth=Math.max(192,pw/3),x=left+18,w=pw-rightWidth-47,y=bodyTop-scroll;
        if(detail.equals("performance")){
            for(String[] engine:new String[][]{{"OptiFine HD U M5","optifine"},{"FoamFix","foamfix"},{"Patcher - Unbox performance subset","unbox_patcher"},{"Nothirium","nothirium"},{"Entity Culling","entityculling"},{"Phosphor","phosphor-lighting"}}){
                boolean loaded=net.minecraftforge.fml.common.Loader.isModLoaded(engine[1]);
                if(engine[1].equals("optifine"))try{Class.forName("optifine.OptiFineForgeTweaker",false,getClass().getClassLoader());loaded=true;}catch(ClassNotFoundException ignored){}
                rows.add(new Row(y,engine[0]+"   /   "+(loaded?"Loaded":"Not loaded")));y+=38;
            }
            bodyButton(x,y+6,180,28,"Open video settings",()->navigate(new net.minecraft.client.gui.GuiVideoSettings(this,mc.gameSettings)));
            bodyButton(x+192,y+6,180,28,"Uncap FPS",()->{mc.gameSettings.limitFramerate=260;mc.gameSettings.enableVsync=false;org.lwjgl.opengl.Display.setVSyncEnabled(false);mc.gameSettings.saveOptions();notice="Unlimited FPS. VSync off.";});y+=46;
            maxScroll=Math.max(0,y+scroll-bodyBottom);return;
        }
        List<UiState.Option> options=new ArrayList<>();UiState.Module mod=UiState.module(detail);
        if(settingQuery.isEmpty()&&tab.equals("General")){
            keyRow(x,y,w,"Toggle module",detail+".toggle");y+=46;
            if(Arrays.asList("zoom","lookback","sprint","sneak").contains(detail)){keyRow(x,y,w,"Activation key",detail);y+=46;}
        }
        if(settingQuery.isEmpty())options.addAll(mod.tabs.get(tab));else for(List<UiState.Option> section:mod.tabs.values())for(UiState.Option o:section)if((o.label+" "+o.key).toLowerCase(Locale.ROOT).contains(settingQuery.toLowerCase(Locale.ROOT)))options.add(o);
        if(detail.equals("nickname")){
            rows.add(new Row(y,"Local display name"));final Field name=field(x+w/2,y+13,w/2-95,22,UiState.get("nickname.value",""),"",s->{});name.clip=true;name.max=16;
            bodyButton(x+w-83,y+9,72,27,"Apply",()->{UiState.set("nickname.value",name.value);UiState.save();notice="Local name saved. Account unchanged.";});y+=52;rows.add(new Row(y,"Visible to you only. Leave blank to use your account name."));y+=46;
        }
        for(final UiState.Option o:options){
            Row row=new Row(y,o.label);row.active=o.available();rows.add(row);int cx=x+w/2,cw=w/2-12;
            if(o.kind.equals("toggle")){Button b=bodyButton(x+w-59,y+10,44,23,o.label,()->{UiState.set(o.key,!Boolean.parseBoolean(o.value()));UiState.save();refreshBody();});b.option=o;b.active=o.available();}
            if(o.kind.equals("slider")){Slider s=new Slider(cx,y+9,cw,27,o);s.clip=true;s.active=o.available();controls.add(s);}
            if(o.kind.equals("choice")){Choice c=new Choice(cx,y+9,cw,27,o);c.clip=true;c.active=o.available();controls.add(c);}
            if(o.kind.equals("color")){
                Field f=field(cx+27,y+15,cw-32,19,o.value(),"#FFFFFF",v->{if(v.matches("#[0-9a-fA-F]{6}")){UiState.set(o.key,v.toUpperCase(Locale.ROOT));UiState.save();}});f.max=7;f.clip=true;f.active=o.available();
                Button b=bodyButton(cx,y+10,21,24,"Choose "+o.label,()->{colorOption=o;colorBefore=o.value();syncColor(colorBefore);initGui();});b.option=o;b.active=o.available();
            }y+=46;
        }
        if(settingQuery.isEmpty()&&detail.equals("crosshair")&&tab.equals("Presets")){
            int cell=(w-16)/4;for(int i=0;i<8;i++){final int index=i;controls.add(new Preset(x+i%4*(cell+4),y+i/4*88,cell,80,index));}y+=180;
        }
        if(settingQuery.isEmpty()&&detail.equals("crosshair")&&tab.equals("Canvas")){
            Canvas canvas=new Canvas(left+30,y+10);canvas.clip=true;controls.add(canvas);y+=217;
            bodyButton(left+30,y,82,26,"Clear canvas",()->{rememberCanvas();UiState.set("crosshair.pixels",Pattern.empty());});
            bodyButton(left+120,y,82,26,"Copy design",()->{GuiScreen.setClipboardString("UNBOX1:"+Pattern.pixels());notice="Crosshair copied";});
            bodyButton(left+210,y,82,26,"Paste design",()->{String value=GuiScreen.getClipboardString();if(value.matches("UNBOX1:[01]{225}")){rememberCanvas();UiState.set("crosshair.pixels",value.substring(7));UiState.set("crosshair.shape","Custom");notice="Crosshair imported";}else notice="Invalid Unbox crosshair code";});
            bodyButton(left+300,y,65,26,"Undo",()->{if(!canvasUndo.isEmpty())UiState.set("crosshair.pixels",canvasUndo.pop());});y+=40;
        }
        if(settingQuery.isEmpty()&&detail.equals("freelook")){
            rows.add(new Row(y,"Activation"));Button mode=bodyButton(x+w/2,y+9,w/2-12,27,"Activation",()->{UiState.set("freelook.mode",UiState.get("freelook.mode","Hold").equals("Hold")?"Toggle":"Hold");});mode.display=()->UiState.get("freelook.mode","Hold");y+=46;
            rows.add(new Row(y,"Camera"));Button cam=bodyButton(x+w/2,y+9,w/2-12,27,"Camera",()->{String[] v={"THIRD PERSON BACK","FIRST PERSON","THIRD PERSON FRONT"};int i=Arrays.asList(v).indexOf(UiState.get("freelook.camera",v[0]));UiState.set("freelook.camera",v[(i+1)%3]);});cam.display=()->UiState.get("freelook.camera","THIRD PERSON BACK");y+=46;
        }
        if(settingQuery.isEmpty()&&Arrays.asList("resources","inventory","spatial","ghast").contains(detail)){
            String label=detail.equals("resources")?"Browse resource packs":detail.equals("inventory")?"Advanced settings":detail.equals("spatial")?"Advanced Spatial GUI settings":"Call Happy Ghast";
            bodyButton(x,y+9,w,27,label,()->notice="UI preview: this feature is not connected.");y+=46;
        }
        maxScroll=Math.max(0,y+scroll-bodyBottom);scroll=UiState.clamp(scroll,0,maxScroll);
    }
    @Override void beginClip(){Painter.clip(left+16,bodyTop,pw-28,bodyBottom-bodyTop);}
    @Override boolean insideClip(float x,float y){return x>=left+16&&x<left+pw-12&&y>=bodyTop&&y<bodyBottom;}
    @Override boolean hudVisible(){return false;}
    @Override boolean blurred(){return false;}
    @Override boolean entrance(){return false;}
    @Override void background(float partial){if(!editing)super.background(partial);}
    @Override void paint(float mx,float my,float partial){
        if(editing){editorPaint();return;}
        Painter.round(left,top,pw,ph,28,SolidTheme.panel());
        if(numberOption!=null){int x=left+pw/2-150,y=top+ph/2-50;Painter.text(numberOption.label,x,y-32,Painter.TEXT);Painter.text(numberError.isEmpty()?numberOption.min+" - "+numberOption.max:numberError,x,y+42,numberError.isEmpty()?Painter.MUTED:0xffffad85);return;}
        if(colorOption!=null){int x=left+pw/2-215,y=top+78;Painter.text(colorOption.label.toUpperCase(Locale.ROOT),x,y,Painter.TEXT);Painter.text("Saturation / Brightness",x,y+29,Painter.MUTED);Painter.text("HEX",x+320,y+29,Painter.MUTED);Painter.text("Hue",x,y+237,Painter.MUTED);Painter.round(x+320,y+238,104,40,5,UiState.color(colorOption.key,colorOption.fallback));Painter.text("Drag to choose a color. Changes preview immediately.",x,y+287,Painter.MUTED);return;}
        if(detail.isEmpty()){
            Painter.icon("logo",left+14,top+9,38,0xffffffff);Painter.text("UNBOX",left+55,top+14,Painter.TEXT);Painter.text("CLIENT MODS",left+55,top+29,Painter.MUTED);Painter.round(left+pw-218,top+62,200,29,6,0x993b3b40);
            if(controls.stream().noneMatch(c->c instanceof Card))Painter.center("No matching mods",logicalWidth/2,top+180,Painter.MUTED);
        }else{
            UiState.Module m=UiState.module(detail);Painter.icon(detail,left+83,top+16,24,Painter.ACCENT);Painter.text(m.title.toUpperCase(Locale.ROOT),left+118,top+16,Painter.TEXT);Painter.text(m.hint,left+118,top+32,Painter.MUTED);

            int rw=Math.max(192,pw/3),ww=detail.equals("performance")?pw-36:pw-rw-47;
            beginClip();for(Row row:rows){Painter.round(left+18,row.y,ww,42,6,SolidTheme.card(0));Painter.text(row.label,left+30,row.y+16,row.active?Painter.TEXT:0xff66737c);}Painter.unclip();
            if(!detail.equals("performance"))preview(left+pw-rw-18,bodyTop,rw,bodyBottom-bodyTop);
        }
        if(maxScroll>0){int h=bodyBottom-bodyTop,thumb=Math.max(24,h*h/(h+maxScroll));Painter.round(detail.isEmpty()?left+pw-8:left+pw-Math.max(192,pw/3)-30,bodyTop+(h-thumb)*scroll/maxScroll,3,thumb,1,0xff65727c);}
        Painter.text(notice.isEmpty()?(detail.isEmpty()?"Right Shift to close":"Saved automatically   ·   Click a number to type"):notice,left+19,top+ph-19,Painter.MUTED);
    }
    @Override void overlay(float mx,float my){if(dropdown!=null){float x=dropdown.x,y=dropdownY(),w=dropdown.w;Painter.round(x-3,y-3,w+6,dropdown.o.choices.length*24+6,6,SolidTheme.panel());for(int i=0;i<dropdown.o.choices.length;i++){if(i==choiceIndex||mx>=x&&mx<x+w&&my>=y+i*24&&my<y+(i+1)*24)Painter.round(x,y+i*24,w,24,4,SolidTheme.card(1));Painter.text(dropdown.o.choices[i],x+10,y+i*24+7,Painter.TEXT);}}}
    private float dropdownY(){return Math.min(dropdown.y+dropdown.h+3,top+ph-dropdown.o.choices.length*24-12);}
    private void preview(int x,int y,int w,int h){
        Painter.round(x,y,w,h,8,SolidTheme.card(1));Painter.text(detail.equals("crosshair")||detail.equals("chat")||detail.equals("scoreboard")||UiState.HUD.contains(detail)?"LIVE PREVIEW":"ABOUT",x+15,y+15,Painter.MUTED);
        if(detail.equals("crosshair")){int[] scene={0xff56664c,0xffd7d0be,0xff172231};Painter.gradient(x+12,y+43,w-24,134,scene[previewScene],Painter.mix(scene[previewScene],0xff000000,.25f));Preview.crosshair(x+w/2,y+108);Painter.center("Click preview to change contrast",x+w/2,y+191,Painter.MUTED);Painter.center(UiState.get("crosshair.shape","Cross"),x+w/2,y+216,Painter.MUTED);}
        else if(UiState.HUD.contains(detail)){if(detail.equals("armor")&&mc.theWorld==null){Painter.center("Join a world to preview equipment",x+w/2,y+85,Painter.MUTED);return;}Preview.inPanel(detail,x,y,w,h);Painter.text("Click preview to edit HUD layout",x+14,y+h-28,Painter.MUTED);}
        else if(detail.equals("chat")){Preview.chat(x+12,y+46,w-24);Painter.text("Sample messages",x+13,y+135,Painter.MUTED);Painter.text("Chat appearance stays personal.",x+13,y+152,Painter.MUTED);}
        
        else {Painter.icon(detail,x+w/2-16,y+70,32,Painter.ACCENT);String[] notes=detail.equals("zoom")?new String[]{"Zoom changes your field of view.","Your movement stays unchanged."}:detail.equals("nickname")?new String[]{"Your nameplate and player list.","Your account identity stays fixed.","Other players see your real name."}:new String[]{UiState.module(detail).title,"Personal settings apply immediately.","Your changes are saved automatically."};for(int i=0;i<notes.length;i++)Painter.text(notes[i],x+13,y+135+i*17,Painter.MUTED);}
    }
    private class Card extends Control {
        final UiState.Module mod;Card(float x,float y,float w,float h,UiState.Module mod){super(x,y,w,h,mod.title);this.mod=mod;clip=true;}
        void draw(float mx,float my){Painter.round(x,y,w,h+30,7,Painter.mix(0x9926262a,0xc33a3a40,hover.to(hit(mx,my)||focus?1:0)));Painter.icon(mod.id,x+w/2-23,y+Math.max(20,(h-65)/2),46,0xffeef0f2);Painter.center(mod.title,x+w/2,y+h-25,Painter.TEXT);ring();}
        boolean hit(float mx,float my){return !(mx>x+w-29&&my<y+30)&&super.hit(mx,my);}
        void click(float mx,float my,int b){if(b==0)openDetail(mod.id);}
    }
    private class Choice extends Control {
        final UiState.Option o;Choice(float x,float y,float w,float h,UiState.Option o){super(x,y,w,h,o.label);this.o=o;}
        void draw(float mx,float my){Painter.round(x,y,w,h,5,SolidTheme.card(1));int n=o.choices.length;if(n>3)Painter.center(o.value()+"  ...",x+w/2,y+8,Painter.TEXT);else for(int i=0;i<n;i++){float xx=x+i*w/n,ww=w/n;boolean selected=o.value().equals(o.choices[i]);if(selected)Painter.round(xx+1,y+1,ww-2,h-2,4,SolidTheme.card(1));Painter.center(o.choices[i],xx+ww/2,y+8,selected?Painter.TEXT:Painter.MUTED);}ring();}
        void choose(int i){UiState.set(o.key,o.choices[Math.floorMod(i,o.choices.length)]);UiState.save();refreshBody();}
        void click(float mx,float my,int b){if(b==1){UiState.VALUES.remove(o.key);refreshBody();return;}if(o.choices.length>3){dropdown=this;choiceIndex=Math.max(0,Arrays.asList(o.choices).indexOf(o.value()));}else choose(Math.min(o.choices.length-1,(int)((mx-x)*o.choices.length/w)));}
        void key(char c,int k){int i=Arrays.asList(o.choices).indexOf(o.value());if(k==Keyboard.KEY_LEFT)choose(i-1);else if(k==Keyboard.KEY_RIGHT)choose(i+1);else super.key(c,k);}
    }
    private class Slider extends Control {
        final UiState.Option o;IntSupplier getter;IntConsumer setter;
        Slider(float x,float y,float w,float h,UiState.Option o){super(x,y,w,h,o.label);this.o=o;getter=()->UiState.number(o.key,Integer.parseInt(o.fallback),o.min,o.max);setter=v->UiState.set(o.key,v);}
        void draw(float mx,float my){float span=w-43,cy=y+h/2;int n=getter.getAsInt();float px=x+(n-o.min)*span/(o.max-o.min);if(o.label.equals("Hue")){for(int i=0;i<span;i++)Painter.fill(x+i,cy-4,1,8,java.awt.Color.HSBtoRGB(i/span,1,1));}else{Painter.round(x,cy-2,span,4,2,SolidTheme.muted());Painter.round(x,cy-2,Math.max(1,px-x),4,2,Painter.ACCENT);}Painter.round(px-4,cy-5,8,10,4,0xffedf2ff);Painter.round(x+w-37,y+1,37,h-2,4,SolidTheme.input());Painter.center(""+n,x+w-18,y+8,active?Painter.TEXT:Painter.MUTED);ring();}
        void update(float mx){setter.accept(o.min+Math.round(UiState.clamp((mx-x)/(w-43),0,1)*(o.max-o.min)));}
        void click(float mx,float my,int b){if(b==1){UiState.VALUES.remove(o.key);return;}if(!o.key.isEmpty()&&mx>=x+w-38){numberOption=o;numberError="";initGui();}else update(mx);}
        void drag(float mx,float my,int b){if(numberOption==null)update(mx);}
        void key(char c,int k){if(k==Keyboard.KEY_LEFT||k==Keyboard.KEY_RIGHT)setter.accept(UiState.clamp(getter.getAsInt()+(k==Keyboard.KEY_LEFT?-1:1),o.min,o.max));else if(k==Keyboard.KEY_HOME||k==Keyboard.KEY_END)setter.accept(k==Keyboard.KEY_HOME?o.min:o.max);else super.key(c,k);UiState.save();}
    }
    private void numberControls(){int x=left+pw/2-150,y=top+ph/2-50;number=field(x,y,300,28,numberOption.value(),"",s->{});number.max=8;focus(number);button(x,y+65,100,28,"Cancel",()->{numberOption=null;initGui();});button(x+200,y+65,100,28,"Apply value",this::applyNumber);}
    private void applyNumber(){try{int n=Integer.parseInt(number.value);if(n<numberOption.min||n>numberOption.max)throw new NumberFormatException();UiState.set(numberOption.key,n);UiState.save();numberOption=null;initGui();}catch(NumberFormatException e){numberError="Enter a value from "+numberOption.min+" to "+numberOption.max;}}
    private void syncColor(String value){int rgb=Integer.parseInt(value.substring(1),16);float[] hsb=java.awt.Color.RGBtoHSB(rgb>>16&255,rgb>>8&255,rgb&255,null);hue=hsb[0];saturation=hsb[1];brightness=hsb[2];}
    private void applyColor(){String v=String.format(Locale.ROOT,"#%06X",java.awt.Color.HSBtoRGB(hue,saturation,brightness)&0xffffff);UiState.set(colorOption.key,v);if(hex!=null){syncing=true;hex.set(v);syncing=false;}}
    private void colorControls(){
        int x=left+pw/2-215,y=top+78;controls.add(new ColorPlane(x,y+48,300,190));Slider hs=new Slider(x,y+252,300,27,new UiState.Option("","Hue",0,0,360));hs.getter=()->Math.round(hue*360);hs.setter=v->{hue=v/360f;applyColor();};controls.add(hs);
        hex=field(x+320,y+49,100,22,UiState.get(colorOption.key,colorOption.fallback),"#FFFFFF",v->{if(!syncing&&v.matches("#[0-9a-fA-F]{6}")){syncColor(v);UiState.set(colorOption.key,v.toUpperCase(Locale.ROOT));}});hex.max=7;
        String[] swatches={"#FFFFFF","#78E4B0","#7DC8FF","#BCA2FF","#FFAD85","#F4768B","#191E24","#000000"};for(int i=0;i<swatches.length;i++){final String c=swatches[i];Button b=button(x+320+i%2*54,y+88+i/2*34,46,26,c,()->{syncColor(c);applyColor();});b.swatch=Integer.parseInt(c.substring(1),16)|0xff000000;}
        button(x,y+306,100,28,"Cancel",()->{UiState.set(colorOption.key,colorBefore);colorOption=null;initGui();});button(x+326,y+306,100,28,"Done",()->{UiState.save();colorOption=null;initGui();});
    }
    private class ColorPlane extends Control {
        ColorPlane(int x,int y,int w,int h){super(x,y,w,h,"Saturation and brightness");}
        void draw(float mx,float my){Edges.colorPlane(x,y,w,h,java.awt.Color.HSBtoRGB(hue,1,1));float xx=x+saturation*(w-1),yy=y+(1-brightness)*(h-1);Painter.outline(xx-4,yy-4,9,9,0xff000000);Painter.outline(xx-3,yy-3,7,7,0xffffffff);}
        void click(float mx,float my,int b){saturation=UiState.clamp((mx-x)/(w-1),0,1);brightness=1-UiState.clamp((my-y)/(h-1),0,1);applyColor();}
        void drag(float mx,float my,int b){click(mx,my,b);}
        void key(char c,int k){float step=GuiScreen.isShiftKeyDown()?.1f:.01f;if(k==Keyboard.KEY_LEFT||k==Keyboard.KEY_RIGHT)saturation=UiState.clamp(saturation+(k==Keyboard.KEY_LEFT?-step:step),0,1);if(k==Keyboard.KEY_UP||k==Keyboard.KEY_DOWN)brightness=UiState.clamp(brightness+(k==Keyboard.KEY_UP?step:-step),0,1);applyColor();}
    }
    private void rememberCanvas(){canvasUndo.push(Pattern.pixels());while(canvasUndo.size()>30)canvasUndo.removeLast();}
    private class Preset extends Control {
        final int index;Preset(int x,int y,int w,int h,int index){super(x,y,w,h,Pattern.NAMES[index]);this.index=index;clip=true;}
        void draw(float mx,float my){boolean selected=Pattern.pixels().equals(Pattern.preset(index));Painter.round(x,y,w,h,7,selected?0x99506387:hit(mx,my)?0x993b4955:0x66303a43);String p=Pattern.preset(index);for(int yy=0;yy<15;yy++)for(int xx=0;xx<15;xx++)if(p.charAt(yy*15+xx)=='1')Painter.fill(x+w/2+(xx-7)*2,y+32+(yy-7)*2,2,2,Painter.TEXT);Painter.center(label,x+w/2,y+h-19,Painter.TEXT);ring();}
        void click(float mx,float my,int b){rememberCanvas();UiState.set("crosshair.pixels",Pattern.preset(index));UiState.set("crosshair.shape","Custom");UiState.save();notice="Preset applied. Open Canvas to edit.";}
    }
    private class Canvas extends Control {
        boolean on;Canvas(int x,int y){super(x,y,195,195,"Crosshair canvas");}
        void draw(float mx,float my){String p=Pattern.pixels();for(int yy=0;yy<15;yy++)for(int xx=0;xx<15;xx++)Painter.fill(x+xx*13,y+yy*13,12,12,p.charAt(yy*15+xx)=='1'?UiState.color("crosshair.color","#FFFFFF"):xx==7||yy==7?0xff374a4b:0xff26343b);ring();}
        void click(float mx,float my,int b){rememberCanvas();int xx=(int)(mx-x)/13,yy=(int)(my-y)/13;on=b==0&&Pattern.pixels().charAt(yy*15+xx)!='1';Pattern.paint(xx,yy,on);}
        void drag(float mx,float my,int b){if(hit(mx,my))Pattern.paint((int)(mx-x)/13,(int)(my-y)/13,on);}
    }
    void editHud(){if(mc.theWorld==null)return;hudBefore.clear();for(String id:UiState.HUD)for(String suffix:Arrays.asList(".x",".y",".scale")){String key=id+suffix;hudBefore.setProperty(key,UiState.get(key,"\u0000"));}editing=true;initGui();}
    private void finishEditor(boolean save){if(!save)for(Object k:hudBefore.keySet()){String v=hudBefore.getProperty(k.toString());if(v.equals("\u0000"))UiState.VALUES.remove(k);else UiState.set(k.toString(),v);}UiState.save();editing=false;initGui();}
    private void editorControls(){int x=logicalWidth/2-191,y=logicalHeight-43;button(x,y,69,27,"Cancel",()->finishEditor(false));button(x+77,y,82,27,snap?"Snap: ON":"Snap: OFF",()->{snap=!snap;initGui();});button(x+167,y,98,27,"Reset position",()->{for(String suffix:Arrays.asList(".x",".y",".scale"))UiState.VALUES.remove(selected+suffix);});button(x+273,y,108,27,"Done",()->finishEditor(true));}
    private void editorPaint(){Painter.center("Edit HUD   /   Corner to resize · Arrows to nudge",logicalWidth/2,23,Painter.TEXT);for(String id:UiState.HUD){float[] b=Preview.bounds(id,logicalWidth,logicalHeight);Preview.hud(id,b[0],b[1]);int c=id.equals(selected)?0xffffffff:0x88ffffff;Painter.outline(b[0]-2,b[1]-2,b[2]+4,b[3]+4,c);Painter.fill(b[0]+b[2]-3,b[1]+b[3]-3,7,7,c);Painter.text(UiState.module(id).title,b[0],b[1]+b[3]+5,c);}if(dragHud&&snap){Painter.fill(logicalWidth/2,45,1,logicalHeight-93,0x3086aaff);Painter.fill(0,logicalHeight/2,logicalWidth,1,0x3086aaff);}}
    @Override boolean interceptClick(float x,float y,int b){
        if(!recording.isEmpty()){saveKey(b-100);return true;}
        if(dropdown!=null){Choice choice=dropdown;float yy=dropdownY();dropdown=null;int i=(int)((y-yy)/24);if(x>=choice.x&&x<choice.x+choice.w&&y>=yy&&i<choice.o.choices.length)choice.choose(i);return true;}
        if(editing&&b==0){for(int i=UiState.HUD.size()-1;i>=0;i--){String id=UiState.HUD.get(i);float[] bounds=Preview.bounds(id,logicalWidth,logicalHeight);if(!UiState.flag(id+".locked",false)&&x>=bounds[0]-3&&x<=bounds[0]+bounds[2]+6&&y>=bounds[1]-3&&y<=bounds[1]+bounds[3]+6){selected=id;dragHud=true;resizeHud=x>bounds[0]+bounds[2]-8&&y>bounds[1]+bounds[3]-8;dragX=x-bounds[0];dragY=y-bounds[1];return true;}}}
        if(!editing&&colorOption==null&&numberOption==null&&!detail.isEmpty()){int rw=Math.max(192,pw/3),px=left+pw-rw-18;if(x>=px&&x<=px+rw&&y>=bodyTop&&y<=bodyBottom){if(detail.equals("crosshair")){previewScene=(previewScene+1)%3;return true;}if(UiState.HUD.contains(detail)){editHud();return true;}}}
        return false;
    }
    @Override protected void mouseClickMove(int x,int y,int b,long held){if(dragHud){float[] bounds=Preview.bounds(selected,logicalWidth,logicalHeight);if(resizeHud){int current=UiState.number(selected+".scale",100,50,200);UiState.set(selected+".scale",UiState.clamp(Math.round(current*Math.max((x-bounds[0])/Math.max(1,bounds[2]),(y-bounds[1])/Math.max(1,bounds[3]))),50,200));}else moveHud(x-dragX,y-dragY);return;}super.mouseClickMove(x,y,b,held);}
    private void moveHud(float x,float y){float[] b=Preview.bounds(selected,logicalWidth,logicalHeight);if(snap){x=Math.round(x/4)*4;y=Math.round(y/4)*4;if(Math.abs(x+b[2]/2-logicalWidth/2)<7)x=(logicalWidth-b[2])/2;if(Math.abs(y+b[3]/2-logicalHeight/2)<7)y=(logicalHeight-b[3])/2;}UiState.set(selected+".x",Math.round(UiState.clamp(x,0,logicalWidth-b[2])/Preview.unit()));UiState.set(selected+".y",Math.round(UiState.clamp(y,0,logicalHeight-b[3])/Preview.unit()));}
    @Override protected void mouseReleased(int x,int y,int b){dragHud=false;resizeHud=false;super.mouseReleased(x,y,b);}
    @Override void scroll(float x,float y,int direction){if(dropdown!=null){choiceIndex=UiState.clamp(choiceIndex-direction,0,dropdown.o.choices.length-1);return;}if(!editing&&colorOption==null&&numberOption==null&&insideClip(x,y)){scroll=UiState.clamp(scroll-direction*36,0,maxScroll);refreshBody();}}
    @Override void reveal(Control c){if(c.clip&&(c.y<bodyTop||c.y+c.h>bodyBottom)){scroll=UiState.clamp(scroll+Math.round(c.y<bodyTop?c.y-bodyTop:c.y+c.h-bodyBottom),0,maxScroll);refreshBody();for(Control next:controls)if(next.label.equals(c.label)&&next.getClass()==c.getClass()){focus(next);break;}}}
    @Override boolean interceptKey(char c,int k){
        if(numberOption!=null&&k==Keyboard.KEY_RETURN){applyNumber();return true;}
        if(dropdown!=null){if(k==Keyboard.KEY_ESCAPE){dropdown=null;return true;}if(k==Keyboard.KEY_DOWN||k==Keyboard.KEY_UP){choiceIndex=Math.floorMod(choiceIndex+(k==Keyboard.KEY_DOWN?1:-1),dropdown.o.choices.length);return true;}if(k==Keyboard.KEY_RETURN){Choice choice=dropdown;dropdown=null;choice.choose(choiceIndex);return true;}}
        if(!recording.isEmpty()){
            if(k==Keyboard.KEY_ESCAPE){recording="";notice="Keybind unchanged.";return true;}
            saveKey(k==Keyboard.KEY_DELETE?0:k);return true;
        }
        if(k==Keyboard.KEY_RSHIFT&&colorOption==null&&numberOption==null&&!editing){UiState.save();navigate(mc.theWorld==null?parent:null);return true;}
        if(editing&&(k==Keyboard.KEY_LEFT||k==Keyboard.KEY_RIGHT||k==Keyboard.KEY_UP||k==Keyboard.KEY_DOWN)){float[] b=Preview.bounds(selected,logicalWidth,logicalHeight);boolean previous=snap;snap=false;moveHud(b[0]+(k==Keyboard.KEY_LEFT?-1:k==Keyboard.KEY_RIGHT?1:0),b[1]+(k==Keyboard.KEY_UP?-1:k==Keyboard.KEY_DOWN?1:0));snap=previous;return true;}return false;
    }
    @Override void close(){recording="";if(numberOption!=null){numberOption=null;initGui();return;}if(colorOption!=null){UiState.set(colorOption.key,colorBefore);colorOption=null;initGui();return;}if(editing){UiState.save();editing=false;navigate(parent);return;}super.close();}
    private static final class Row {final int y;final String label;boolean active=true;Row(int y,String label){this.y=y;this.label=label;}}
}
