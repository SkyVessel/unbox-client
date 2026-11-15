package dev.unbox.unpvp;

import net.minecraft.client.gui.*;
import org.lwjgl.input.*;
import java.io.IOException;
import java.util.*;
import java.util.function.*;

/** Screen-local input, focus and drawing. The only state it edits is the UI preview. */
abstract class UiScreen extends GuiScreen {
    final GuiScreen parent;final List<Control> controls=new ArrayList<>();
    float scale=1,offset,alpha=1;int logicalWidth,logicalHeight;
    private static boolean startupReported;private boolean drawn;
    private String draggingHud;private float hudX,hudY;private Control tooltip;private long tooltipAt;
    private long entered;private Control pressed,focused;private boolean warmed;float releaseX,releaseY;
    UiScreen(GuiScreen parent){this.parent=parent;}
    @Override public void initGui(){if(drawn&&mc.currentScreen==this)UiTransition.refresh(this);drawn=false;controls.clear();pressed=null;focused=null;logicalWidth=width;logicalHeight=height;scale=1;layout();Keyboard.enableRepeatEvents(true);}
    void prepare(net.minecraft.client.Minecraft game,int w,int h){mc=game;width=w;height=h;layout();}
    abstract void layout();
    abstract void paint(float x,float y,float partial);
    boolean blurred(){return false;}
    boolean entrance(){return true;}
    boolean hudVisible(){return false;}
    void background(float partial){if(mc.theWorld==null)Backdrop.panorama(width,height,partial);Painter.begin(1,0);try{Painter.fill(0,0,width,height,(Math.round(SolidTheme.opacity()*255)<<24)|(SolidTheme.background()&0xffffff));}finally{Painter.end();}}
    @Override public void drawScreen(int mx,int my,float partial){
        if(!warmed){Painter.warm();warmed=true;}
        background(partial);
        if(hudVisible()){Painter.begin(1,0);try{for(String id:UiState.HUD)if(UiState.on(id)){float[] box=Preview.bounds(id,width,height);Preview.hud(id,box[0],box[1]);if(mx>=box[0]&&mx<=box[0]+box[2]&&my>=box[1]&&my<=box[1]+box[3])Painter.outline(box[0]-2,box[1]-2,box[2]+4,box[3]+4,0xffffffff);}}finally{Painter.end();}}

        if(entered==0)entered=System.nanoTime();alpha=1;offset=0;
        Painter.begin(scale,offset);Painter.opacity=alpha;
        try{paint(mx/scale,my/scale-offset,partial);for(Control c:controls){if(!c.visible)continue;if(c.clip)beginClip();c.draw(mx/scale,my/scale-offset);if(c.clip)Painter.unclip();}overlay(mx/scale,my/scale-offset);tooltip(mx/scale,my/scale-offset);}finally{Painter.end();}
        drawn=true;if(this instanceof HomeScreen)reportStartup();
    }
    static void reportStartup(){if(!startupReported){startupReported=true;System.out.println("[unpvp-startup] first-frame-ms="+java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());}}
    void tooltip(float x,float y){Control next=null;for(Control c:controls)if(c instanceof Button&&!((Button)c).icon.isEmpty()&&c.hit(x,y)&&(!c.clip||insideClip(x,y)))next=c;
        if(next!=tooltip){tooltip=next;tooltipAt=System.nanoTime();}if(next==null||System.nanoTime()-tooltipAt<600000000L)return;
        float w=Painter.width(next.label)+12,xx=Math.min(logicalWidth-w-4,x+12),yy=Math.min(logicalHeight-22,y+16);Painter.round(xx,yy,w,20,3,0xf0101018);Painter.text(next.label,xx+6,yy+5,Painter.TEXT);
    }
    void beginClip(){}
    void overlay(float x,float y){}
    Button button(float x,float y,float w,float h,String label,Runnable action){Button b=new Button(x,y,w,h,label,action);controls.add(b);return b;}
    Field field(float x,float y,float w,float h,String value,String hint,Consumer<String> change){Field f=new Field(x,y,w,h,value,hint,change);controls.add(f);return f;}
    void navigate(GuiScreen s){mc.displayGuiScreen(s);}
    void close(){UiState.save();navigate(parent);}
    @Override public void onGuiClosed(){entered=0;Keyboard.enableRepeatEvents(false);UiState.save();}
    @Override public boolean doesGuiPauseGame(){return false;}
    @Override protected void mouseClicked(int mx,int my,int button){
        float x=mx/scale,y=my/scale-offset;if(interceptClick(x,y,button))return;
        Control hit=null;for(int i=controls.size()-1;i>=0;i--){Control c=controls.get(i);if(c.visible&&c.active&&c.hit(x,y)&&(!c.clip||insideClip(x,y))){hit=c;break;}}
        focus(hit);pressed=hit;if(hit!=null)hit.click(x,y,button);
        else if(button==0&&hudVisible())for(int i=UiState.HUD.size()-1;i>=0;i--){String id=UiState.HUD.get(i);float[] box=Preview.bounds(id,width,height);if(UiState.on(id)&&!UiState.flag(id+".locked",false)&&mx>=box[0]&&mx<=box[0]+box[2]&&my>=box[1]&&my<=box[1]+box[3]){draggingHud=id;hudX=mx-box[0];hudY=my-box[1];break;}}

    }
    boolean interceptClick(float x,float y,int button){return false;}
    boolean insideClip(float x,float y){return true;}
    void focus(Control c){if(focused!=null)focused.focus=false;focused=c;if(c!=null)c.focus=true;}
    @Override protected void mouseClickMove(int mx,int my,int button,long held){if(draggingHud!=null){float[] box=Preview.bounds(draggingHud,width,height);UiState.set(draggingHud+".x",Math.round(UiState.clamp(mx-hudX,0,width-box[2])/Preview.unit()));UiState.set(draggingHud+".y",Math.round(UiState.clamp(my-hudY,0,height-box[3])/Preview.unit()));}else if(pressed!=null)pressed.drag(mx/scale,my/scale-offset,button);}
    @Override protected void mouseReleased(int mx,int my,int button){releaseX=mx/scale;releaseY=my/scale-offset;if(draggingHud!=null){draggingHud=null;UiState.save();}if(pressed!=null)pressed.release();pressed=null;}
    @Override public void handleMouseInput(){super.handleMouseInput();int delta=Mouse.getEventDWheel();if(delta!=0){float x=Mouse.getEventX()*(float)width/mc.displayWidth/scale,y=(height-Mouse.getEventY()*(float)height/mc.displayHeight-1)/scale-offset;scroll(x,y,delta>0?1:-1);}}
    void scroll(float x,float y,int direction){}
    @Override protected void keyTyped(char ch,int key){
        if(interceptKey(ch,key))return;
        if(key==Keyboard.KEY_ESCAPE||key==Keyboard.KEY_RSHIFT){close();return;}
        if(key==Keyboard.KEY_TAB){int current=controls.indexOf(focused),step=isShiftKeyDown()?-1:1;for(int n=0;n<controls.size();n++){current=Math.floorMod(current+step,controls.size());Control c=controls.get(current);if(c.active&&c.visible){focus(c);reveal(c);break;}}return;}
        if(focused!=null)focused.key(ch,key);
    }
    boolean interceptKey(char ch,int key){return false;}
    boolean textFocused(){return focused instanceof Field;}
    void reveal(Control c){}
    abstract class Control {
        float x,y,w,h;String label;boolean active=true,visible=true,focus,clip;final Painter.Motion hover=new Painter.Motion();
        Control(float x,float y,float w,float h,String label){this.x=x;this.y=y;this.w=w;this.h=h;this.label=label;}
        boolean hit(float mx,float my){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
        abstract void draw(float mx,float my);
        void click(float mx,float my,int b){}
        void drag(float mx,float my,int b){}
        void release(){UiState.save();}
        void key(char c,int key){if(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_SPACE)click(x+w/2,y+h/2,0);}
        void ring(){if(focus)Painter.outline(x,y,w,h,Painter.ACCENT);}
    }
    class Button extends Control {
        final Runnable action;boolean selected,framed,strip,homeStyle,down;String icon="",module="";Supplier<String> display;UiState.Option option;Integer swatch;final Painter.Motion toggle=new Painter.Motion();
        Button(float x,float y,float w,float h,String label,Runnable action){super(x,y,w,h,label);this.action=action;}
        void draw(float mx,float my){
            float over=hover.to(active&&(hit(mx,my)||focus)?1:0);boolean on=UiState.on(module);
            int base=selected||framed?SolidTheme.input():0x00000000;
            int bg=Painter.mix(base,(SolidTheme.text()&0xffffff)|0x18000000,over);
            if(swatch!=null){Painter.round(x,y,w,h,5,swatch);ring();return;}
            if(option!=null&&option.kind.equals("color")){Painter.round(x,y,w,h,4,UiState.color(option.key,option.fallback));ring();return;}
            if(strip)Painter.bottomRound(x,y,w,h,5,bg);else Painter.round(x,y,w,h,homeStyle?4:5,bg);ring();
            if(option!=null&&option.kind.equals("toggle")){boolean enabled=Boolean.parseBoolean(option.value());Painter.round(x+3,y+6,w-6,12,6,enabled?SolidTheme.accent():0xff999999);Painter.round(x+5+Math.round((w-20)*toggle.to(enabled?1:0)),y+8,8,8,4,SolidTheme.index()==2?0xff323437:0xfff5f3ed);return;}
            if(!icon.isEmpty()){float size=framed||homeStyle?18:14;Painter.icon(icon,x+(w-size)/2,y+(h-size)/2,size,homeStyle?(active?Painter.TEXT:0xff6d777e):icon.equals("close")&&over>.1f?0xffff9098:selected?Painter.ACCENT:Painter.MUTED);return;}
            String text=display!=null?display.get():!module.isEmpty()?(module.equals("performance")?"Configure":on?"Enabled":"Disabled"):label;
            Painter.center(text,x+w/2,y+(h-10)/2,active?Painter.TEXT:Painter.MUTED);
        }
        void click(float mx,float my,int b){if(b==0&&active)down=true;else if(b==1&&option!=null){UiState.VALUES.remove(option.key);UiState.save();}}
        @Override void release(){boolean run=down&&active&&hit(releaseX,releaseY);down=false;if(run)action.run();UiState.save();}
        @Override void key(char c,int key){if(active&&(key==Keyboard.KEY_RETURN||key==Keyboard.KEY_SPACE))action.run();}
    }
    class Field extends Control {
        String value,hint;int cursor,anchor,max=256,view;final Consumer<String> changed;long blink=System.nanoTime();
        Field(float x,float y,float w,float h,String value,String hint,Consumer<String> changed){super(x,y,w,h,hint);this.value=value;this.hint=hint;this.changed=changed;cursor=anchor=value.length();}
        void set(String s){value=s;cursor=anchor=Math.min(cursor,s.length());changed.accept(s);}
        void edit(String s){int from=Math.min(cursor,anchor),to=Math.max(cursor,anchor);s=s.replaceAll("[\\p{Cntrl}]","");if(value.length()-(to-from)+s.length()>max)s=s.substring(0,Math.max(0,max-value.length()+to-from));value=value.substring(0,from)+s+value.substring(to);cursor=anchor=from+s.length();blink=System.nanoTime();changed.accept(value);}
        int position(float mx){float p=x;for(int i=view;i<value.length();i++){float a=Painter.advance(value.charAt(i));if(mx<p+a/2)return i;p+=a;}return value.length();}
        void draw(float mx,float my){
            Painter.round(x-4,y-3,w+8,h+6,4,SolidTheme.input());
            cursor=UiState.clamp(cursor,0,value.length());anchor=UiState.clamp(anchor,0,value.length());view=Math.min(view,cursor);while(view<cursor&&Painter.width(value.substring(view,cursor))>w-3)view++;
            Painter.clip(x,y,w,h);int end=view;while(end<value.length()&&Painter.width(value.substring(view,end+1))<=w)end++;
            if(focus&&anchor!=cursor){int a=Math.max(view,Math.min(cursor,anchor)),b=Math.min(end,Math.max(cursor,anchor));if(b>a)Painter.fill(x+Painter.width(value.substring(view,a)),y+1,Painter.width(value.substring(a,b)),h-2,0x885774b5);}
            Painter.text(value.isEmpty()&&!focus?hint:value.substring(view,end),x,y+(h-10)/2,value.isEmpty()?Painter.MUTED:Painter.TEXT);
            if(focus&&(System.nanoTime()-blink)/500000000L%2==0)Painter.fill(x+Painter.width(value.substring(view,cursor)),y+2,1,h-4,Painter.TEXT);Painter.unclip();
            if(focus)Painter.fill(x,y+h,w,1,0x9986aaff);
        }
        void click(float mx,float my,int b){if(b==0){cursor=anchor=position(mx);blink=System.nanoTime();}}
        void drag(float mx,float my,int b){cursor=position(mx);}
        void key(char c,int key){
            boolean ctrl=GuiScreen.isCtrlKeyDown(),shift=GuiScreen.isShiftKeyDown();
            if(ctrl&&key==Keyboard.KEY_A){anchor=0;cursor=value.length();return;}
            if(ctrl&&(key==Keyboard.KEY_C||key==Keyboard.KEY_X)){GuiScreen.setClipboardString(value.substring(Math.min(cursor,anchor),Math.max(cursor,anchor)));if(key==Keyboard.KEY_X)edit("");return;}
            if(ctrl&&key==Keyboard.KEY_V){edit(GuiScreen.getClipboardString());return;}
            if(key==Keyboard.KEY_BACK||key==Keyboard.KEY_DELETE){if(anchor==cursor){if(key==Keyboard.KEY_BACK)anchor=Math.max(0,cursor-1);else anchor=Math.min(value.length(),cursor+1);}edit("");return;}
            if(key==Keyboard.KEY_LEFT||key==Keyboard.KEY_RIGHT||key==Keyboard.KEY_HOME||key==Keyboard.KEY_END){cursor=key==Keyboard.KEY_HOME?0:key==Keyboard.KEY_END?value.length():UiState.clamp(cursor+(key==Keyboard.KEY_LEFT?-1:1),0,value.length());if(!shift)anchor=cursor;blink=System.nanoTime();return;}
            if(c>=32&&c!=127&&!ctrl)edit(""+c);
        }
    }
}
