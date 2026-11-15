package dev.unbox.unpvp;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
/** Explicit per-launch consent. Only structured connection measurements; never reads the game log. */
public final class Feedback {
 private static volatile boolean active;private static boolean closed;private static long started;private static final ArrayDeque<JsonObject> events=new ArrayDeque<>();private static final ArrayDeque<JsonObject> samples=new ArrayDeque<>();
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"Unbox feedback file");t.setDaemon(true);return t;});
 static String status="Off";private static int generation;
 static synchronized int ticket(){return active?generation:-1;}
 static boolean active(){return active;}
 private static Path file(){return SocialBridge.root().resolve("logs/unbox-feedback-recording.json");}
 static synchronized void start(){if(active)return;generation++;samples.clear();events.clear();started=System.currentTimeMillis();active=true;ConnectionDiagnostics.resetWindow();status="Recording — sends when Minecraft exits";ConnectionDiagnostics.tick();}
 static synchronized void discard(){active=false;generation++;samples.clear();events.clear();try{Files.deleteIfExists(file());status="Off — recording discarded";}catch(Exception e){status="Off — could not remove local file";}ConnectionDiagnostics.tick();}
 public static synchronized void event(String stage,String route){if(!active)return;JsonObject e=new JsonObject();e.addProperty("at",System.currentTimeMillis());e.addProperty("stage",stage);e.addProperty("route",route);events.add(e);while(events.size()>40)events.removeFirst();}
 static synchronized void record(JsonObject window,int ticket){if(!active||ticket!=generation)return;samples.add(window);while(samples.size()>40)samples.removeFirst();JsonObject report=new JsonObject();report.addProperty("schema",1);report.addProperty("consent",true);report.addProperty("session",System.getProperty("unbox.feedbackSession","test"));report.addProperty("accountUuid",Minecraft.getMinecraft().getSession().getProfile().getId().toString().replace("-",""));report.addProperty("startedAt",started);report.addProperty("minecraft","1.8.9");JsonArray history=new JsonArray();for(JsonObject e:events)history.add(e);report.add("events",history);report.addProperty("clientVersion",System.getProperty("unbox.clientVersion","development"));JsonArray list=new JsonArray();for(JsonObject w:samples)list.add(w);report.add("windows",list);
  while(report.toString().getBytes(StandardCharsets.UTF_8).length>48000&&list.size()>1){samples.removeFirst();list=new JsonArray();for(JsonObject w:samples)list.add(w);report.add("windows",list);}final byte[] data=report.toString().getBytes(StandardCharsets.UTF_8);if(data.length>48000)return;final int batch=generation;
  IO.submit(()->{synchronized(Feedback.class){if(!active||batch!=generation)return;try{Files.createDirectories(file().getParent());Path tmp=file().resolveSibling(".unbox-feedback.tmp");Files.write(tmp,data);Files.move(tmp,file(),StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){status="Recording — could not save report";}}});
 }
 public static void shutdown(){if(closed)return;closed=true;ConnectionDiagnostics.finish();IO.shutdown();try{IO.awaitTermination(2,TimeUnit.SECONDS);synchronized(Feedback.class){if(active&&Files.exists(file()))Files.move(file(),file().resolveSibling("unbox-feedback.json"),StandardCopyOption.REPLACE_EXISTING);}}catch(Exception e){status="Could not finish feedback report";}}
}
