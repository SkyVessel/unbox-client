package dev.unbox.unpvp;
import java.nio.file.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
final class FeedbackProbe {
 static void flush(Class<?> type)throws Exception{java.lang.reflect.Field field=type.getDeclaredField("IO");field.setAccessible(true);((ExecutorService)field.get(null)).submit(()->{}).get(3,TimeUnit.SECONDS);}
 static void run()throws Exception{
  Path file=SocialBridge.root().resolve("logs/unbox-feedback-recording.json");CombatProbe.check(!Feedback.active()&&!Files.exists(file),"Feedback is off by default and creates no upload report");
  Feedback.start();ConnectionDiagnostics.save();flush(ConnectionDiagnostics.class);flush(Feedback.class);
  com.google.gson.JsonObject report=SocialBridge.read("logs/unbox-feedback-recording.json");CombatProbe.check(Feedback.active()&&report.get("consent").getAsBoolean()&&report.getAsJsonArray("windows").size()==1,"Opt-in records structured measurements for this session only");
  CombatProbe.check(Files.size(file)<=48000&&!report.has("accessToken")&&!report.has("chat"),"Feedback report is bounded and excludes credentials and chat");
  Feedback.discard();flush(ConnectionDiagnostics.class);flush(Feedback.class);CombatProbe.check(!Files.exists(file),"Stop discards recording and queued writes cannot recreate it");
  UiState.set("fps.padding",3);CombatProbe.check(UiState.number("fps.padding",4,0,12)==3,"Parsed HUD settings return the current value");UiState.set("fps.padding",7);CombatProbe.check(UiState.number("fps.padding",4,0,12)==7,"HUD setting cache invalidates immediately on editing");
  UiState.set("fps.color","#123456");CombatProbe.check(UiState.color("fps.color","#FFFFFF")==0xff123456,"HUD color parses correctly");UiState.set("fps.color","#654321");CombatProbe.check(UiState.color("fps.color","#FFFFFF")==0xff654321,"HUD color cache invalidates immediately");UiState.set("fps.padding",4);UiState.set("fps.color","#F4F6F8");
  Feedback.start();Minecraft mc=Minecraft.getMinecraft();net.minecraft.client.gui.GuiScreen old=mc.currentScreen;FeedbackScreen screen=new FeedbackScreen(old);mc.displayGuiScreen(screen);mc.getFramebuffer().bindFramebuffer(true);mc.entityRenderer.setupOverlayRendering();screen.drawScreen(0,0,0);java.io.File dir=new java.io.File(mc.mcDataDir,"feedback-probe");dir.mkdirs();net.minecraft.util.ScreenShotHelper.saveScreenshot(dir,"feedback.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());int theme=SolidTheme.index();SolidTheme.select(2);mc.entityRenderer.setupOverlayRendering();screen.drawScreen(0,0,0);net.minecraft.util.ScreenShotHelper.saveScreenshot(dir,"feedback-dark.png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());SolidTheme.select(theme);mc.displayGuiScreen(old);
  // Leave enabled to verify the real shutdown mixin flushes the final partial window.
 }
}
