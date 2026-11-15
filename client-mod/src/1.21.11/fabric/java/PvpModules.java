package dev.unbox.client;

import java.util.*;
import java.nio.file.*;
import java.security.MessageDigest;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import com.google.gson.*;

/** Public, version-pinned Fabric modules. JAR enablement is explicitly restart-bound. */
public final class PvpModules {
 private record Entry(String id,String title,String filename,String sha512,boolean defaultEnabled){}
 private static final Map<String,Entry> ENTRIES=read();
 private static final Map<String,Boolean> NEXT=new HashMap<>();
 private static final Set<String> INSTALLED=new HashSet<>();
 static{for(var e:ENTRIES.values()){boolean on=Files.isRegularFile(file(e,true));NEXT.put(e.id,on);if(on||Files.isRegularFile(file(e,false)))INSTALLED.add(e.id);}}
 private static String error="";
 private static Map<String,Entry> read(){
  Map<String,Entry> result=new LinkedHashMap<>();
  try(var in=PvpModules.class.getResourceAsStream("/unbox-pvp.json")){
   for(var value:JsonParser.parseReader(new java.io.InputStreamReader(in)).getAsJsonObject().getAsJsonArray("mods")){
    var o=value.getAsJsonObject();if(!o.get("primary").getAsBoolean())continue;
    var e=new Entry(o.get("id").getAsString(),o.get("title").getAsString(),o.get("filename").getAsString(),o.get("sha512").getAsString(),o.get("defaultEnabled").getAsBoolean());result.put(e.id,e);
   }
  }catch(Exception e){throw new IllegalStateException("Invalid PvP catalogue",e);}return Collections.unmodifiableMap(result);
 }
 public static boolean available(){return true;}
 public static boolean contains(String id){return id.equals("eatingpack")||ENTRIES.containsKey(id);}
 public static List<ModOptions.Mod> entries(){var list=new ArrayList<>(ENTRIES.values().stream().map(e->new ModOptions.Mod(e.id,e.title,"PvP","Settings saved automatically")).toList());list.add(new ModOptions.Mod("eatingpack","Eating Animation Pack","PvP","Optional resource pack from Theone; author download"));return List.copyOf(list);}
 public static boolean shared(String id){return Set.of("fps","cps","ping","keystrokes","armor","sprint","zoom","crosshair","chat","scoreboard","coordinates","viewmodel","daytime").contains(id);}
 private static Path file(Entry e,boolean enabled){return Platform.gameDir().resolve("mods").resolve(e.filename+(enabled?"":".disabled"));}
 public static boolean enabled(String id){var e=ENTRIES.get(id);return e!=null&&NEXT.getOrDefault(id,false);}
 public static String status(String id){if(id.equals("eatingpack"))return "Optional resource pack";var e=ENTRIES.get(id);if(e==null)return "";boolean on=enabled(id);if(!on&&!INSTALLED.contains(id))return "Not installed";return on!=Platform.loaded(id)?"Restart required":on?"Loaded":"Disabled";}
 public static String error(){return error;}
 public static boolean toggle(String id){
  if(id.equals("eatingpack")){open(id,Minecraft.getInstance().screen);return false;}
  var e=ENTRIES.get(id);if(e==null)return false;boolean on=enabled(id);Path source=file(e,on),dest=file(e,!on);
  try{if(!Files.isRegularFile(source)||Files.isSymbolicLink(source))throw new java.io.IOException("Install this module with the pinned PvP preparation first.");
   String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-512").digest(Files.readAllBytes(source)));
   if(!actual.equals(e.sha512))throw new java.io.IOException("Modified JAR preserved. Restore the pinned file to change installation.");
   Files.move(source,dest);NEXT.put(id,!on);if(id.equals("handtweaks")&&!on)UnboxClient.set("viewmodel",false);UnboxClient.set("pvp.install."+id,!on);UnboxClient.save();error="Restart Minecraft to apply this change.";return true;
  }catch(Exception ex){error=ex.getMessage();return false;}
 }
 public static void open(String id,Screen parent){Minecraft.getInstance().setScreen(id.equals("eatingpack")?new PvpModuleScreen(id,parent):new UnboxScreen(parent,id));}
 static boolean configure(String id,Screen parent){open(id,parent);return true;}
}
