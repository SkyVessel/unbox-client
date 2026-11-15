package dev.unbox.unpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.util.ScreenShotHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.*;
import java.io.File;

/** Explicitly enabled, isolated display regression test; never runs in a user's session. */
final class RetinaProbe {
    private static final boolean DISPLAY_ONLY=Boolean.getBoolean("unpvp.test.displayOnly");
    private static int stage,frames,checks;private static long since;
    private static final CursorScreen screen=new CursorScreen();
    private static java.awt.Robot robot;
    static void frame(){
        Minecraft mc=Minecraft.getMinecraft();if(!(mc.currentScreen instanceof HomeScreen)&&mc.currentScreen!=screen)return;
        if(since==0)since=System.nanoTime();
        // Wait for Minecraft's deferred focus/ungrab step before positioning the cursor.
        if(stage>=1&&stage<=4&&frames==8)move();
        if(!DISPLAY_ONLY&&stage>=1&&stage<=4&&frames==12)try{if(robot==null)robot=new java.awt.Robot();robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);}catch(java.awt.AWTException e){throw new IllegalStateException(e);}
        if(++frames<20||System.nanoTime()-since<900000000L)return;
        try{
            if(stage>0){verify(mc);if(stage<=4&&!DISPLAY_ONLY)cursor(mc);}
            switch(stage){
                case 0:mc.gameSettings.guiScale=2;mc.displayGuiScreen(screen);move();break;
                case 1:mc.gameSettings.guiScale=1;mc.displayGuiScreen(screen);move();break;
                case 2:mc.gameSettings.guiScale=0;mc.displayGuiScreen(screen);move();break;
                case 3:mc.gameSettings.guiScale=2;Display.setDisplayMode(new DisplayMode(960,640));move();break;
                case 4:shot(mc,"window-960");mc.toggleFullscreen();break;
                case 5:check(mc.isFullScreen(),"fullscreen entered");shot(mc,"fullscreen");mc.toggleFullscreen();break;
                case 6:check(!mc.isFullScreen(),"returned to window");Display.setDisplayMode(new DisplayMode(1280,800));break;
                case 7:shot(mc,"window-restored");check(Math.abs(Preview.unit()-1)<.001,"HUD size preserved at GUI scale 2");System.out.println("[unpvp-retina-test] PASS checks="+checks);mc.shutdown();return;
            }
            stage++;frames=0;since=System.nanoTime();
        }catch(Exception e){e.printStackTrace();System.err.println("[unpvp-retina-test] FAILED stage="+stage);mc.shutdown();}
    }
    private static void move(){if(DISPLAY_ONLY)return;screen.eventSeen=false;Mouse.setCursorPosition(Display.getWidth()*3/4,Display.getHeight()/2);}
    private static void cursor(Minecraft mc)throws ReflectiveOperationException{
        // Read untransformed LWJGL coordinates independently of the production hooks.
        // Native cursor warping may be adjusted by macOS; verify the delivered input.
        int rawX=(Integer)Mouse.class.getMethod("getX").invoke(null),rawY=(Integer)Mouse.class.getMethod("getY").invoke(null);
        int expectedX=rawX*screen.width/Display.getWidth(),expectedY=screen.height-rawY*screen.height/Display.getHeight()-1;
        check(Math.abs(rawX-Display.getWidth()/2)>10,"native pointer moved away from window centre");
        System.out.println("[unpvp-retina-test] cursor active="+Display.isActive()+" grabbed="+Mouse.isGrabbed()+" event="+screen.eventX+","+screen.eventY);
        check(Math.abs(screen.x-expectedX)<=2&&Math.abs(screen.y-expectedY)<=2,"absolute cursor reaches GUI target stage="+stage+" actual="+screen.x+","+screen.y+" expected="+expectedX+","+expectedY);
        check(screen.eventSeen&&Math.abs(screen.eventX-screen.expectedClickX)<=2&&Math.abs(screen.eventY-screen.expectedClickY)<=2,"native click reaches GUI target stage="+stage);
    }
    private static void verify(Minecraft mc){
        int scale=RetinaDisplay.scale(),w=Display.getWidth()*scale,h=Display.getHeight()*scale;
        check(mc.displayWidth==w&&mc.displayHeight==h,"display dimensions match backing pixels stage="+stage);
        check(mc.getFramebuffer().framebufferWidth==w&&mc.getFramebuffer().framebufferHeight==h,"world framebuffer matches backing pixels stage="+stage);
        ScaledResolution r=new ScaledResolution(mc);int factor=r.getScaleFactor()/scale;
        check(screen.width==(int)Math.ceil(Display.getWidth()/(double)factor)&&screen.height==(int)Math.ceil(Display.getHeight()/(double)factor),"GUI retains window-relative size stage="+stage);
        check(GL11.glGetError()==0,"no GL errors stage="+stage);
    }
    private static void shot(Minecraft mc,String name){File dir=new File(mc.mcDataDir,"retina-probe");dir.mkdirs();ScreenShotHelper.saveScreenshot(dir,name+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
    private static void check(boolean ok,String message){if(!ok)throw new IllegalStateException(message);checks++;System.out.println("[unpvp-retina-check] "+message);}
    private static final class CursorScreen extends GuiScreen {
        int x,y,eventX,eventY,expectedClickX,expectedClickY;boolean eventSeen;
        @Override public void drawScreen(int mx,int my,float partial){x=mx;y=my;drawDefaultBackground();drawCenteredString(fontRendererObj,"Retina display / cursor regression",width/2,height/2,0xffffff);drawRect(width*3/4-4,height*3/4-4,width*3/4+4,height*3/4+4,0xff86aaff);}
        @Override protected void mouseClicked(int mx,int my,int button){
            // Compare the click with its own queued event, not a later pointer position.
            try{
                int rawX=(Integer)Mouse.class.getMethod("getEventX").invoke(null),rawY=(Integer)Mouse.class.getMethod("getEventY").invoke(null);
                expectedClickX=rawX*width/Display.getWidth();expectedClickY=height-rawY*height/Display.getHeight()-1;
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            eventSeen=true;eventX=mx;eventY=my;
        }
    }
}
