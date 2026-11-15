package dev.unbox.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Explicit opt-in, bounded metadata only. Never writes files on a game/network thread. */
public final class SyncFeedback {
 private static final Path ROOT=Platform.gameDir().resolve("logs");
 private static final ArrayDeque<JsonObject> events=new ArrayDeque<>();
 private static final ScheduledExecutorService IO=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"unbox-sync-feedback");t.setDaemon(true);return t;});
 private static volatile boolean recording;private static boolean initialized;private static long started;private static String account="";
 public static volatile String status="Off — no report is sent";
 private SyncFeedback(){}
 public static synchronized boolean active(){return recording;}
 public static synchronized void start(){
  if(recording)return;
  if(System.getProperty("unbox.feedbackSession","").isEmpty()){status="Update and keep the Unbox launcher open";return;}
  if(!initialized){initialized=true;IO.scheduleWithFixedDelay(SyncFeedback::save,2,2,TimeUnit.SECONDS);Runtime.getRuntime().addShutdownHook(new Thread(SyncFeedback::shutdown,"unbox-feedback-exit"));}
  events.clear();started=System.currentTimeMillis();account=Minecraft.getInstance().getUser().getProfileId().toString().replace("-","");recording=true;status="Recording — sends on game exit";event("recording_started","client",0,0,"",null);
 }
 public static synchronized void discard(){recording=false;events.clear();status="Off — recording discarded";IO.execute(()->{synchronized(SyncFeedback.class){if(recording)return;try{Files.deleteIfExists(ROOT.resolve("unbox-feedback-recording.json"));Files.deleteIfExists(ROOT.resolve("unbox-feedback.json"));}catch(Exception e){status="Off — could not delete local report";}}});}
 public static synchronized void event(String stage,String role,long bytes,long total,String file,Throwable failure){
  if(!recording)return;
  JsonObject e=new JsonObject();e.addProperty("at",System.currentTimeMillis());e.addProperty("stage",code(stage));e.addProperty("role",code(role));e.addProperty("bytes",Math.max(0,bytes));e.addProperty("total",Math.max(0,total));
  if(file!=null&&file.matches("[a-z0-9_-]{1,64}"))e.addProperty("fileId",file);
  if(failure!=null){Throwable cause=failure;for(int i=0;i<6&&cause.getCause()!=null;i++)cause=cause.getCause();e.addProperty("errorType",code(cause.getClass().getSimpleName()));String m=String.valueOf(cause.getMessage()).toLowerCase(Locale.ROOT);e.addProperty("errorCode",m.contains("checksum")?"checksum":m.contains("cancel")?"cancelled":m.contains("timed out")||m.contains("timeout")?"timeout":m.contains("space")?"disk_space":m.contains("denied")?"permission":m.contains("unauth")?"untrusted_host":m.contains("chunk")?"invalid_chunk":m.contains("manifest")?"manifest":m.contains("changed")?"host_changed":"other");}
  events.add(e);while(events.size()>120)events.removeFirst();
 }
 private static String code(String s){return s==null?"":s.replaceAll("[^A-Za-z0-9_-]","_").substring(0,Math.min(64,s.length()));}
 private static void save(){byte[] bytes;synchronized(SyncFeedback.class){if(!recording)return;
  JsonObject v=new JsonObject();v.addProperty("schema",2);v.addProperty("kind","mod-sync");v.addProperty("consent",true);v.addProperty("session",System.getProperty("unbox.feedbackSession"));v.addProperty("accountUuid",account);v.addProperty("startedAt",started);v.addProperty("minecraft","26.3");v.addProperty("loader",Platform.loader());v.addProperty("clientVersion",System.getProperty("unbox.clientVersion","development"));JsonArray list=new JsonArray();events.forEach(list::add);v.add("events",list);bytes=v.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>48000)return;}try{
  Files.createDirectories(ROOT);Path temp=ROOT.resolve(".unbox-sync-feedback.tmp");Files.write(temp,bytes);Files.move(temp,ROOT.resolve("unbox-feedback-recording.json"),StandardCopyOption.REPLACE_EXISTING);
 }catch(Exception e){status="Recording — could not save report";}}
 public static void shutdown(){IO.shutdown();try{IO.awaitTermination(2,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}if(!recording)return;event("game_exit","client",0,0,"",null);save();try{Files.move(ROOT.resolve("unbox-feedback-recording.json"),ROOT.resolve("unbox-feedback.json"),StandardCopyOption.REPLACE_EXISTING);}catch(Exception ignored){}}
}
