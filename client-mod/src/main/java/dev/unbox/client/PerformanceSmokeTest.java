package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import java.nio.file.*;
import java.util.*;

/** Opt-in disposable fixture; never runs during ordinary play. */
final class PerformanceSmokeTest {
    private static int ticks;private static boolean done;private static final List<String> checks=new ArrayList<>();
    static void tick(Minecraft mc){
        if(done||mc.player==null||mc.getSingleplayerServer()==null)return;
        try{
            if(++ticks==100){
                if(PerformanceMonitor.running()||PerformanceMonitor.begin()!=0)throw new AssertionError("Profiler must default off");
                checks.addAll(ModToggleChecks.run(mc));OptimizationChecks.verify(mc,checks);
                mc.setScreen(null);UnboxClient.set("minimap",true);PerformanceMonitor.start();
            }
            if(ticks==240){
                PerformanceMonitor.stop();
                var field=PerformanceMonitor.class.getDeclaredField("snapshot");field.setAccessible(true);Map<?,?> report=(Map<?,?>)field.get(null);
                var groups=(Map<?,?>)report.get("groups");
                for(String key:List.of("CLIENT_TICK","RENDER","EXTRACT","SERVER","SETTINGS","MAP","HUD","MAP_DRAW","SELF")){
                    var sample=(PerformanceSamples.Summary)groups.get(key);if(sample.calls()==0||sample.totalMs()<=0)throw new AssertionError("Missing live timings: "+key);checks.add("Live timing captured: "+key);
                }
                if(PerformanceMonitor.begin()!=0)throw new AssertionError("Stop did not disable instrumentation");
                checks.add("Profiler starts disabled and stops instrumentation immediately");
                PerformanceMonitor.export();mc.setScreen(new PerformanceScreen(new UnboxScreen(null)));
            }
            if(ticks==255)Screenshot.grab(mc.gameDirectory,"performance-tester.png",mc.getMainRenderTarget(),1,c->{});
            if(ticks==260){
                var screen=(PerformanceScreen)mc.screen;
                if(screen.children().size()!=5)throw new AssertionError("Profiler controls missing");
                for(int i=0;i<3;i++){var toggle=screen.children().stream().filter(w->w instanceof net.minecraft.client.gui.components.Button b&&b.getMessage().getString().startsWith("HUD:")).map(w->(net.minecraft.client.gui.components.Button)w).findFirst().orElseThrow();toggle.onPress(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER,0,0));}
                if(screen.children().size()!=5)throw new AssertionError("HUD toggle duplicated controls");
                mc.options.guiScale().set(6);mc.resizeGui();
            }
            if(ticks==275)Screenshot.grab(mc.gameDirectory,"performance-tester-small.png",mc.getMainRenderTarget(),1,c->{});
            if(ticks==290){
                if(!PerformanceMonitor.exportStatus.startsWith("Saved"))throw new AssertionError(PerformanceMonitor.exportStatus);
                checks.add("Stopped recording exported as JSON; HUD toggle retains 5 controls at small GUI size");finish(mc,null);
            }
        }catch(Throwable e){finish(mc,e);}
    }
    private static void finish(Minecraft mc,Throwable error){done=true;PerformanceMonitor.stop();if(error!=null)error.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("logs/feature-smoke.json"),new com.google.gson.Gson().toJson(Map.of("ok",error==null,"checks",checks,"error",error==null?"":error.toString())));}catch(Exception e){e.printStackTrace();}mc.stop();}
}
