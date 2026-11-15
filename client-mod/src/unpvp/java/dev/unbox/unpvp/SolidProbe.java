package dev.unbox.unpvp;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ScreenShotHelper;
import java.io.File;
/** Explicitly enabled isolated UI regression; never runs in normal play. */
final class SolidProbe {
    private static int stage,frames,checks,uploads;private static long since;
    static void afterFrame(SolidScreen screen){
        if(!screen.surfaceReady())return;if(since==0)since=System.nanoTime();if(++frames<35||System.nanoTime()-since<800000000L)return;
        Minecraft mc=Minecraft.getMinecraft();
        try{
            check(org.lwjgl.opengl.GL11.glGetError()==0,"No GL errors at stage "+stage);
            switch(stage){
                case 0:shot("title-red");uploads=screen.uploads;break;
                case 1:check(screen.uploads==uploads,"Stable frames reuse cached surfaces");SolidTheme.select(2);screen.rebuild();break;
                case 2:shot("title-yellow");mc.displayGuiScreen(new VectorModsScreen(screen));break;
                case 3:interaction(screen);shot("mods-yellow");check(screen.viewport.gridHeight()>0,"Responsive grid has a viewport");uploads=screen.uploads;UiState.set("ui.opacity",10);break;
                case 4:check(screen.uploads==uploads,"Opacity does not rebuild surfaces");shot("mods-opacity-10");UiState.set("ui.opacity",90);break;
                case 5:check(screen.uploads==uploads,"Second opacity change reuses surface");shot("mods-opacity-90");SolidTheme.select(1);screen.rebuild();break;
                case 6:shot("mods-red");VectorModsScreen mods=(VectorModsScreen)screen;mods.scroll(300,400,-1);break;
                case 7:shot("mods-scroll");check(screen.uploads==uploads+1,"Scrolling does not rebuild surfaces");mc.displayGuiScreen(new SkinsScreen(screen));break;
                case 8:shot("appearance");check(screen.controls.stream().anyMatch(c->c.label.equals("Use skin")),"Appearance has separate selection and apply");mc.displayGuiScreen(new HomeScreen(true));break;
                case 9:shot("quick-feedback");check(screen.controls.stream().anyMatch(c->c.label.equals("Feedback")&&c.y>screen.logicalHeight-80),"Feedback stays at bottom-left");mc.displayGuiScreen(null);check(SolidTexture.activeResidents()==0&&SolidTexture.residentBytes<=SolidTexture.LIMIT,"Page handles released; retained cache is bounded");SolidTexture.clearIdle();SolidText.clear();SolidGeometry.clear();check(SolidTexture.alive==0,"Closing releases GPU surfaces and masks");System.out.println("[unpvp-solid-test] PASS checks="+checks);mc.shutdown();break;
            }
            stage++;frames=0;since=System.nanoTime();
        }catch(Throwable e){e.printStackTrace();System.out.println("[unpvp-solid-test] FAILED stage="+stage);mc.shutdown();}
    }
    private static void interaction(SolidScreen screen){UiScreen.Control toggle=null;for(UiScreen.Control c:screen.controls)if(c.label.equals("Toggle FPS")){toggle=c;break;}check(toggle!=null,"FPS has a live toggle");int x=Math.round((toggle.x+toggle.w/2)*screen.scale),y=Math.round((toggle.y+toggle.h/2)*screen.scale);boolean before=UiState.on("fps");screen.mouseClicked(x,y,0);check(UiState.on("fps")==before,"Button press alone does not activate");screen.mouseReleased(0,0,0);check(UiState.on("fps")==before,"Dragging out cancels button activation");screen.mouseClicked(x,y,0);screen.mouseReleased(x,y,0);check(UiState.on("fps")!=before,"Release inside the visible toggle activates it");UiState.set("fps",before);}
    private static void check(boolean condition,String label){if(!condition)throw new IllegalStateException(label);checks++;System.out.println("[unpvp-solid-check] "+label);}
    private static void shot(String name){Minecraft mc=Minecraft.getMinecraft();File dir=new File(mc.mcDataDir,"solid-probe");dir.mkdirs();ScreenShotHelper.saveScreenshot(dir,name+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());}
}
