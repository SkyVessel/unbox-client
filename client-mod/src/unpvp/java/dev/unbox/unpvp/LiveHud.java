package dev.unbox.unpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetworkPlayerInfo;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.opengl.GL11;
import java.util.Arrays;
import java.util.List;

/** Native 1.8.9 port of modern ClientHud / KeystrokesHud. No network polling or input rewriting. */
final class LiveHud {
    static final List<String> IDS=Arrays.asList("fps","cps","keystrokes","ping");
    private static final ClickWindow[] clicks={new ClickWindow(),new ClickWindow()};
    private static final boolean[] held=new boolean[2],keyHeld=new boolean[5];
    private static final long[] released=new long[2],keyReleased=new long[5];
    private static long refreshed;private static String fps="0 FPS",ping="Local";
    static int rendered;

    @SubscribeEvent public void mouse(MouseEvent e){
        Minecraft mc=Minecraft.getMinecraft();
        input(e.button,e.buttonstate,System.nanoTime(),mc.theWorld!=null&&mc.currentScreen==null&&mc.inGameHasFocus);
    }
    static void input(int button,boolean pressed,long now,boolean playing){
        if(button<0||button>1)return;
        if(held[button]&&!pressed)released[button]=now;
        boolean wasHeld=held[button];held[button]=pressed&&playing;
        if(pressed&&playing&&!wasHeld)clicks[button].add(now);
    }
    static int count(int button){return clicks[button].count(System.nanoTime());}
    static void reset(){for(ClickWindow c:clicks)c.clear();Arrays.fill(held,false);Arrays.fill(released,0);Arrays.fill(keyHeld,false);Arrays.fill(keyReleased,0);}
    static void tick(){
        Minecraft mc=Minecraft.getMinecraft();
        if(mc.theWorld==null){reset();return;}
        if(mc.currentScreen!=null||!mc.inGameHasFocus){Arrays.fill(held,false);Arrays.fill(keyHeld,false);}
        long now=System.nanoTime();int interval=250;
        try{interval=Integer.parseInt(UiState.get("fps.interval","250 ms").split(" ")[0]);}catch(NumberFormatException ignored){}
        if(now-refreshed>=Math.max(100,interval)*1_000_000L){
            fps=Minecraft.getDebugFPS()+(UiState.flag("fps.label",true)?" FPS":"");
            NetworkPlayerInfo info=mc.getNetHandler()==null||mc.thePlayer==null?null:mc.getNetHandler().getPlayerInfo(mc.thePlayer.getUniqueID());
            ping=latency(mc.isSingleplayer(),info==null?null:info.getResponseTime(),UiState.flag("ping.label",true));refreshed=now;
        }
    }
    static String latency(boolean local,Integer ms,boolean label){return local?"Local":ms==null||ms<0?(label?"-- ms":"--"):ms+(label?" ms":"");}
    static String text(String id){return id.equals("fps")?fps:ping;}
    static boolean visible(String id){Minecraft mc=Minecraft.getMinecraft();return UiState.on(id)&&(!mc.gameSettings.showDebugInfo||UiState.flag(id+".inDebug",false))&&(!(mc.currentScreen instanceof GuiChat)||UiState.flag(id+".inChat",true));}
    @SubscribeEvent public void render(RenderGameOverlayEvent.Post e){
        Minecraft mc=Minecraft.getMinecraft();
        if(e.type!=RenderGameOverlayEvent.ElementType.ALL||mc.thePlayer==null||mc.gameSettings.hideGUI||mc.currentScreen instanceof UiScreen)return;
        boolean any=false;for(String id:IDS)any|=visible(id);if(!any)return;
        ScaledResolution r=e.resolution;Painter.begin(1,0);
        try{for(String id:IDS)if(visible(id)){float[] b=Preview.bounds(id,r.getScaledWidth(),r.getScaledHeight());draw(id,b[0],b[1],false);rendered++;}}finally{Painter.end();}
    }
    static int[] size(String id){
        int p=UiState.number(id+".padding",4,0,12);
        if(id.equals("keystrokes")){int k=UiState.number("keystrokes.size",24,18,36),gap=UiState.number("keystrokes.gap",3,0,8),rows=(UiState.flag("keystrokes.movement",true)?2:0)+(UiState.flag("keystrokes.mouse",true)?1:0)+(UiState.flag("keystrokes.space",true)?1:0);return new int[]{k*3+gap*2,Math.max(0,rows*(k+gap)-gap)};}
        if(id.equals("cps")){int w=UiState.number("cps.width",48,36,80);return new int[]{UiState.get("cps.mode","Both").equals("Both")?w*2+UiState.number("cps.spacing",4,0,16):w,UiState.number("cps.height",32,28,48)};}
        return new int[]{Math.max(width(id.equals("fps")?"240 FPS":"42 ms"),width(text(id)))+p*2,12+p*2};
    }
    private static int width(String s){return Minecraft.getMinecraft().fontRendererObj.getStringWidth(s);}
    private static void label(String id,String s,float x,float y,int color){Preview.pixel(s,x,y,color,UiState.flag(id+".shadow",true));}
    private static void center(String id,String s,float x,float y,int color){label(id,s,x-width(s)/2f,y,color);}
    private static float fade(boolean down,long at,int ms){return down?1:at==0||ms==0?0:Math.max(0,1-(System.nanoTime()-at)/(ms*1_000_000f));}
    static void draw(String id,float x,float y,boolean preview){
        int[] sz=size(id);int color=UiState.color(id+".color","#F4F6F8"),p=UiState.number(id+".padding",4,0,12);Minecraft mc=Minecraft.getMinecraft();boolean playing=!preview&&mc.currentScreen==null&&mc.inGameHasFocus;
        GL11.glPushMatrix();GL11.glTranslatef(x,y,0);GL11.glScalef(Preview.factor(id),Preview.factor(id),1);
        if(id.equals("ping")&&UiState.flag("ping.background",false))Painter.round(0,0,sz[0],sz[1],UiState.flag("ping.rounded",true)?4:0,(UiState.number("ping.opacity",60,0,100)*255/100<<24)|(UiState.color("ping.backgroundColor","#191E24")&0xffffff));
        if(id.equals("fps")||id.equals("ping"))label(id,preview?(id.equals("fps")?(UiState.flag("fps.label",true)?"240 FPS":"240"):(UiState.flag("ping.label",true)?"42 ms":"42")):text(id),p,p,color);
        else if(id.equals("cps")){
            int w=UiState.number("cps.width",48,36,80),h=sz[1],gap=UiState.number("cps.spacing",4,0,16),n=0;String mode=UiState.get("cps.mode","Both");
            for(int b=0;b<2;b++){if(mode.equals("Left")&&b==1||mode.equals("Right")&&b==0)continue;int xx=n++*(w+gap);float flash=!preview&&UiState.flag("cps.feedback",true)?fade(playing&&held[b],released[b],UiState.number("cps.fade",180,50,500)):0;
                if(flash>0)Painter.fill(xx,0,w,h,((int)(flash*UiState.number("cps.pressOpacity",35,10,100)*255/100)<<24)|(UiState.color("cps.pressColor","#FFFFFF")&0xffffff));
                Painter.outline(xx,0,w,h,0xffffffff);int c=Painter.mix(color,UiState.color("cps.pressText","#FFFFFF"),flash);center(id,b==0?"LCPS":"RCPS",xx+w/2f,4,c);center(id,preview?"0":""+count(b),xx+w/2f,h-14,c);
            }
        }else if(id.equals("keystrokes")){
            int k=UiState.number("keystrokes.size",24,18,36),gap=UiState.number("keystrokes.gap",3,0,8),w=sz[0],yy=0;KeyBinding[] keys={mc.gameSettings.keyBindForward,mc.gameSettings.keyBindLeft,mc.gameSettings.keyBindBack,mc.gameSettings.keyBindRight,mc.gameSettings.keyBindJump};String[] arrows={"^","<","v",">"};
            if(UiState.flag("keystrokes.movement",true)){for(int i=0;i<4;i++)key(i==0?k+gap:(i-1)*(k+gap),i==0?0:k+gap,k,k,UiState.flag("keystrokes.arrows",false)?arrows[i]:keyName(keys[i]),keyLight(i,playing&&keys[i].isKeyDown()));yy+=2*(k+gap);}
            if(UiState.flag("keystrokes.mouse",true)){for(int b=0;b<2;b++){int bw=(w-gap)/2;String s=UiState.flag("keystrokes.cps",true)?(preview?0:count(b))+" CPS":b==0?"LMB":"RMB";key(b*(bw+gap),yy,b==1?w-bw-gap:bw,k,s,preview?0:fade(playing&&held[b],released[b],UiState.number("keystrokes.fade",180,0,500)));}yy+=k+gap;}
            if(UiState.flag("keystrokes.space",true))key(0,yy,w,k,keyName(keys[4]),keyLight(4,playing&&keys[4].isKeyDown()));
        }
        GL11.glPopMatrix();
    }
    static String keyName(KeyBinding key){return net.minecraft.client.settings.GameSettings.getKeyDisplayString(key.getKeyCode());}
    private static float keyLight(int i,boolean down){if(keyHeld[i]&&!down)keyReleased[i]=System.nanoTime();keyHeld[i]=down;return fade(down,keyReleased[i],UiState.number("keystrokes.fade",180,0,500));}
    private static void key(int x,int y,int w,int h,String s,float press){
        int idle=((UiState.flag("keystrokes.background",false)?UiState.number("keystrokes.opacity",40,0,100)*255/100:0)<<24)|(UiState.color("keystrokes.idleColor","#191E24")&0xffffff);
        Painter.fill(x,y,w,h,Painter.mix(idle,UiState.color("keystrokes.pressColor","#FFFFFF"),press));if(UiState.flag("keystrokes.border",true))Painter.outline(x,y,w,h,UiState.color("keystrokes.borderColor","#FFFFFF"));
        float fit=Math.min(1,(w-4f)/Math.max(1,width(s)));GL11.glPushMatrix();GL11.glTranslatef(x+w/2f,y+(h-10*fit)/2f,0);GL11.glScalef(fit,fit,1);center("keystrokes",s,0,0,Painter.mix(UiState.color("keystrokes.color","#F4F6F8"),UiState.color("keystrokes.pressText","#171D23"),press));GL11.glPopMatrix();
    }
    /** Fixed storage: exactly one second of presses, never repeat-counts a held button. */
    static final class ClickWindow {
        private final long[] times=new long[1024];private int start,size;
        void clear(){start=size=0;}
        void add(long now){count(now);if(size==times.length){start=(start+1)%times.length;size--;}times[(start+size++)%times.length]=now;}
        int count(long now){while(size>0&&now-times[start]>=1_000_000_000L){start=(start+1)%times.length;size--;}return size;}
    }
}
