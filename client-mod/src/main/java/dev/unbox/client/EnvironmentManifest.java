package dev.unbox.client;

import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.ZipFile;
import java.io.*;

/** Bounded, content-addressed gameplay manifest. No configs, saves or personal client mods. */
public final class EnvironmentManifest {
 public static final long MAX_FILE=512L*1024*1024,MAX_TOTAL=4L*1024*1024*1024;
 public static final Set<String> MANAGED=Set.of("unbox","fabric-api","sodium","lithium","ferritecore","entityculling","immediatelyfast","dynamic_fps","e4mc","freelook");
 public record Entry(String hash,long size,String id,String version){}
 public record Snapshot(JsonObject manifest,Map<String,Path> files){}
 public static String hash(Path p)throws Exception{var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return HexFormat.of().formatHex(digest.digest());}
 public static boolean gameplay(Path p)throws Exception{
  if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))return false;
  try(var zip=new ZipFile(p.toFile())){var f=zip.getEntry("fabric.mod.json");if(f==null||f.getSize()>262144)throw new IOException("Unsupported mod metadata: "+p.getFileName());try(var in=zip.getInputStream(f)){var j=JsonParser.parseString(new String(in.readNBytes(262145),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();return !MANAGED.contains(j.get("id").getAsString())&&(!j.has("environment")||!j.get("environment").getAsString().equals("client"))&&(!j.has("environment")||!j.get("environment").getAsString().equals("server"));}}
 }
 public static Snapshot scan(Path root)throws Exception{
  JsonArray entries=new JsonArray();Map<String,Path> files=new TreeMap<>();long total=0;
  var dir=root.resolve("mods");if(Files.isDirectory(dir))try(var list=Files.list(dir)){for(Path p:list.sorted().toList())if(gameplay(p)){long size=Files.size(p);if(size>MAX_FILE||(total+=size)>MAX_TOTAL||files.size()>=512)throw new IOException("Shared mods exceed the transfer limit");String hash=hash(p);if(files.putIfAbsent(hash,p)!=null)throw new IOException("Duplicate gameplay JAR");JsonObject e=new JsonObject();e.addProperty("hash",hash);e.addProperty("size",size);try(var zip=new ZipFile(p.toFile());var in=zip.getInputStream(zip.getEntry("fabric.mod.json"))){var m=JsonParser.parseString(new String(in.readNBytes(262145),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();e.addProperty("id",m.get("id").getAsString());e.addProperty("version",m.has("version")?m.get("version").getAsString():"");}entries.add(e);}}
  JsonObject m=new JsonObject();m.addProperty("schema",1);m.addProperty("minecraft","26.1");m.addProperty("loader","fabric");m.addProperty("loaderVersion","0.19.5");m.add("files",entries);validate(m);return new Snapshot(m,files);
 }
 public static List<Entry> validate(JsonObject m)throws Exception{
  if(m.get("schema").getAsInt()!=1||!m.get("minecraft").getAsString().equals("26.1")||!m.get("loader").getAsString().equals("fabric")||!m.get("loaderVersion").getAsString().equals("0.19.5"))throw new IOException("Friend's game version or loader is unsupported");
  List<Entry> out=new ArrayList<>();Set<String> hashes=new HashSet<>(),ids=new HashSet<>();long total=0;var files=m.getAsJsonArray("files");if(files.size()>512)throw new IOException("Too many shared mods");
  for(var v:files){var e=v.getAsJsonObject();String h=e.get("hash").getAsString(),id=e.get("id").getAsString(),version=e.get("version").getAsString();long n=e.get("size").getAsLong();if(!h.matches("[a-f0-9]{64}")||!id.matches("[a-z][a-z0-9_-]{1,63}")||MANAGED.contains(id)||version.length()>128||n<1||n>MAX_FILE||(total+=n)>MAX_TOTAL||!hashes.add(h)||!ids.add(id))throw new IOException("Invalid shared mod manifest");out.add(new Entry(h,n,id,version));}return out;
 }
 public static Set<String> hashes(JsonObject m)throws Exception{Set<String>s=new HashSet<>();for(var e:validate(m))s.add(e.hash);return s;}
}
