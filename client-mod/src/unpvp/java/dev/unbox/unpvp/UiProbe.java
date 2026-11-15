package dev.unbox.unpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.WorldSettings.GameType;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import java.io.*;
import java.util.*;

/** Opt-in isolated game smoke test, dormant in normal launches. */
final class UiProbe {
    private static int stage,frames,worldTicks,finishingTicks;private static int checks;private static long since=System.nanoTime(),last;private static boolean waitingWorld,finishing;private static final List<Long> samples=new ArrayList<>();
    static void tick(){Minecraft mc=Minecraft.getMinecraft();if(finishing){if(++finishingTicks<20)return;check(!Backdrop.active(),"Closing the UI releases blur buffers");try{hudChecks();shot("13-return-to-game");}catch(IOException e){throw new IllegalStateException(e);}finish();return;}if(waitingWorld&&mc.theWorld!=null&&mc.thePlayer!=null&&++worldTicks>40){waitingWorld=false;mc.displayGuiScreen(null);net.minecraft.client.settings.KeyBinding.onTick(Keyboard.KEY_RSHIFT);since=System.nanoTime();}if((System.nanoTime()-since)>120000000000L){System.err.println("[unpvp-test] TIMEOUT stage="+stage);mc.shutdown();}}
    static void afterFrame(UiScreen screen){
        long now=System.nanoTime();if(last!=0&&now-since>500000000L)samples.add(now-last);last=now;frames++;
        if(now-since<1800000000L||frames<12)return;
        Minecraft mc=Minecraft.getMinecraft();
        try{
            int error=GL11.glGetError();if(error!=0)throw new IllegalStateException("OpenGL error "+error);
            switch(stage){
                case 0:shot("01-home");mc.displayGuiScreen(new ModsScreen(screen));break;
                case 1:shot("02-mods");gridChecks((ModsScreen)screen);((ModsScreen)screen).openDetail("crosshair");break;
                case 2:shot("03-crosshair");settingChecks((ModsScreen)screen);clickLabel(screen,"Canvas");break;
                case 3:shot("04-canvas");UiScreen.Control canvas=control(screen,"Crosshair canvas");String before=Pattern.pixels();click(screen,canvas,0);check(!Pattern.pixels().equals(before),"Canvas paints through scaled mouse coordinates");clickLabel(screen,"Undo");check(Pattern.pixels().equals(before),"Canvas undo restores design");((ModsScreen)screen).openDetail("fps");break;
                case 4:shot("05-fps");((ModsScreen)screen).openDetail("crosshair");click(screen,control(screen,"Choose Color"),0);break;
                case 5:shot("05a-color-picker");mc.displayGuiScreen(new FriendsScreen(new HomeScreen(false)));break;
                case 6:shot("06-friends");mc.displayGuiScreen(new SkinsScreen(new HomeScreen(false)));break;
                case 7:shot("07-skins");mc.displayGuiScreen(new RedeemScreen(screen));break;
                case 8:shot("08-redeem");if(Boolean.getBoolean("unpvp.test.world")){waitingWorld=true;mc.launchIntegratedServer("UIPreview","UI Preview",new WorldSettings(42,GameType.CREATIVE,false,false,WorldType.FLAT));}else{finish();return;}break;
                case 9:check(screen instanceof HomeScreen,"Right Shift opens the quick menu from gameplay");shot("09-quick-menu");mc.displayGuiScreen(new ModsScreen(screen));break;
                case 10:shot("10-world-mods");((ModsScreen)screen).editHud();break;
                case 11:shot("11-hud-editor");float[] box=Preview.bounds("fps",screen.width,screen.height);float beforeX=box[0];screen.mouseClicked((int)box[0]+5,(int)box[1]+5,0);screen.mouseClickMove((int)box[0]+105,(int)box[1]+35,0,50);screen.mouseReleased((int)box[0]+105,(int)box[1]+35,0);check(Preview.bounds("fps",screen.width,screen.height)[0]!=beforeX,"HUD drag changes preview position");screen.keyTyped('\0',Keyboard.KEY_ESCAPE);break;
                case 12:shot("12-hud-return");screen.keyTyped('\0',Keyboard.KEY_RSHIFT);check(mc.currentScreen==null,"Right Shift exits hierarchy to gameplay");check(UiFramePolicy.limit()==-1,"Game frame limit retains the original path");finishing=true;return;
            }
            stage++;since=System.nanoTime();frames=0;samples.clear();last=0;
        }catch(Exception e){e.printStackTrace();System.err.println("[unpvp-test] FAILED stage="+stage);mc.shutdown();}
    }
    private static void hudChecks(){
        Minecraft mc=Minecraft.getMinecraft();check(LiveHud.rendered>0,"Live HUD draws after returning to gameplay");
        LiveHud.ClickWindow window=new LiveHud.ClickWindow();window.add(100);window.add(200);check(window.count(999999999)==2,"CPS counts both presses within one second");check(window.count(1000000100)==1&&window.count(1000000200)==0,"CPS expires presses at the one-second boundary");
        LiveHud.reset();long now=System.nanoTime();LiveHud.input(0,true,now,true);LiveHud.input(0,true,now+1,true);check(LiveHud.count(0)==1,"Holding a mouse button never adds repeated CPS");LiveHud.input(0,false,now+2,true);LiveHud.input(0,true,now+3,true);check(LiveHud.count(0)==2,"A new press increments CPS");LiveHud.input(1,true,now,false);check(LiveHud.count(1)==0,"Clicks inside menus do not count as gameplay CPS");LiveHud.reset();
        check(LiveHud.latency(true,42,true).equals("Local")&&LiveHud.latency(false,null,true).equals("-- ms")&&LiveHud.latency(false,87,true).equals("87 ms"),"Ping distinguishes local, unavailable and server latency");
        net.minecraft.client.settings.KeyBinding key=mc.gameSettings.keyBindForward;int code=key.getKeyCode();key.setKeyCode(Keyboard.KEY_UP);check(LiveHud.keyName(key).equals(net.minecraft.client.settings.GameSettings.getKeyDisplayString(Keyboard.KEY_UP)),"Keystrokes displays remapped movement keys");key.setKeyCode(code);
        String old=UiState.get("fps","true");UiState.set("fps",false);check(!LiveHud.visible("fps"),"Disabled HUD is hidden");UiState.set("fps",old);boolean debug=mc.gameSettings.showDebugInfo;mc.gameSettings.showDebugInfo=true;check(!LiveHud.visible("fps"),"HUD respects debug-screen visibility preference");mc.gameSettings.showDebugInfo=debug;
        check(org.lwjgl.opengl.GL11.glGetError()==0,"HUD leaves OpenGL free of errors");
    }
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;System.out.println("[unpvp-check] "+message);}
    private static UiScreen.Control control(UiScreen s,String label){for(UiScreen.Control c:s.controls)if(c.label.equals(label))return c;throw new IllegalStateException("Missing control "+label);}
    private static void click(UiScreen s,UiScreen.Control c,int button){s.mouseClicked(Math.round((c.x+c.w/2)*s.scale),Math.round((c.y+c.h/2+s.offset)*s.scale),button);s.mouseReleased(0,0,button);}
    private static void gridChecks(ModsScreen s){
        check(UiFramePolicy.limit()==Minecraft.getMinecraft().gameSettings.limitFramerate,"Unbox UI uses the configured FPS limit");check(Backdrop.active(),"GPU background blur is active");
        check(UiState.MODULES.stream().noneMatch(m->m.id.equals("freecam")||m.id.equals("particles")||m.id.equals("replay")),"Excluded modules have no UI descriptors");
        click(s,control(s,"HUD"),0);check(s.controls.stream().noneMatch(c->c.label.equals("Crosshair")),"HUD category filters cards");click(s,control(s,"All"),0);
        click(s,control(s,"Favorite Crosshair"),0);click(s,control(s,"Favorites"),0);check(s.controls.stream().anyMatch(c->c.label.equals("Crosshair")),"Favorite is visible in Favorites");click(s,control(s,"Favorite Crosshair"),0);click(s,control(s,"All"),0);
        UiScreen.Field search=(UiScreen.Field)control(s,"Search mods...");s.focus(search);for(char c:"crosshair".toCharArray())s.keyTyped(c,0);check(s.controls.stream().anyMatch(c->c.label.equals("Crosshair"))&&s.controls.stream().noneMatch(c->c.label.equals("FPS")),"Search accepts typed text and filters immediately");search.set("");s.focus(null);
        boolean enabled=UiState.on("fps");click(s,control(s,"Toggle FPS"),0);check(UiState.on("fps")!=enabled,"Toggle controls the live FPS HUD");click(s,control(s,"Toggle FPS"),0);
    }
    private static void settingChecks(ModsScreen s)throws IOException{
        click(s,control(s,"Choose Color"),0);String original=UiState.get("crosshair.color","#FFFFFF");UiScreen.Field hex=null;for(UiScreen.Control c:s.controls)if(c instanceof UiScreen.Field)hex=(UiScreen.Field)c;
        hex.set("#ABCDEF");check(UiState.get("crosshair.color","").equals("#ABCDEF"),"Hex input updates the color preview");hex.set("#GGGGGG");check(UiState.get("crosshair.color","").equals("#ABCDEF"),"Invalid hex input preserves the last valid color");s.keyTyped('\0',Keyboard.KEY_ESCAPE);check(UiState.get("crosshair.color","#FFFFFF").equals(original),"Escape cancels color edits");
        UiScreen.Field search=(UiScreen.Field)control(s,"Search settings...");search.set("rotation");UiScreen.Control slider=control(s,"Rotation");s.mouseClicked(Math.round((slider.x+slider.w-10)*s.scale),Math.round((slider.y+12+s.offset)*s.scale),0);s.mouseReleased(0,0,0);
        UiScreen.Field number=null;for(UiScreen.Control c:s.controls)if(c instanceof UiScreen.Field)number=(UiScreen.Field)c;number.set("999");s.keyTyped('\0',Keyboard.KEY_RETURN);check(s.controls.contains(number),"Out-of-range numeric entry stays open");number.set("45");s.keyTyped('\0',Keyboard.KEY_RETURN);check(UiState.number("crosshair.rotation",0,-180,180)==45,"Enter applies an exact slider value");click(s,control(s,"Rotation"),1);check(UiState.number("crosshair.rotation",0,-180,180)==0,"Right click resets the individual setting");((UiScreen.Field)control(s,"Search settings...")).set("");
    }
    private static void clickLabel(UiScreen screen,String label){for(UiScreen.Control c:screen.controls)if(c.label.equals(label)){c.click(c.x+c.w/2,c.y+c.h/2,0);return;}throw new IllegalStateException("Missing control "+label);}
    private static void shot(String name)throws IOException{Minecraft mc=Minecraft.getMinecraft();File root=new File(mc.mcDataDir,"ui-probe");root.mkdirs();ScreenShotHelper.saveScreenshot(root,name+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());Collections.sort(samples);double median=samples.isEmpty()?0:samples.get(samples.size()/2)/1e6,p95=samples.isEmpty()?0:samples.get(Math.min(samples.size()-1,(int)(samples.size()*.95)))/1e6;try(PrintWriter out=new PrintWriter(new FileWriter(new File(root,"frames.csv"),true))){out.printf(Locale.ROOT,"%s,%d,%.3f,%.3f%n",name,samples.size(),median,p95);}System.out.println("[unpvp-test] captured "+name);}
    private static void finish(){System.out.println("[unpvp-test] PASS checks="+checks);Minecraft.getMinecraft().shutdown();}
}
