package dev.unbox.client;
import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Bounded, authenticated gameplay and visual-pack manifest; never copies personal controls. */
public final class EnvironmentManifest {
 public static final long MAX_FILE=512L*1024*1024,MAX_TOTAL=4L*1024*1024*1024;
 public static final Set<String> MANAGED=Set.of("wick","unbox","fabric-api","sodium","iris","lithium","ferritecore","entityculling","immediatelyfast","dynamic_fps","e4mc","freelook","freecam","itemphysiclite","iteminteractions","creativecore","inventoryprofilesnext","libipn","fabric-language-kotlin","klf","veinminer","veinminer_client","fallingtree","anvianslib","chiseledbookshelfvisualizer","cloth-config","cloth_config","colourscroller","easyshulkerboxes","effortlessbuilding","forgeconfigapiport","kotlinforforge","modmenu","mousetweaks","placeholder-api","puzzleslib","resourcify","spatial-gui","spatial_gui","status-effect-bars","status_effect_bars","yet_another_config_lib_v3");
 public record Entry(String hash,long size,String id,String version,String kind,String name){}
 public record Snapshot(JsonObject manifest,Map<String,Path> files){}
 public static String hash(Path p)throws Exception{var digest=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(p)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}return HexFormat.of().formatHex(digest.digest());}
 public static boolean gameplay(Path p)throws Exception{
  if(!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))return false;
  var mods=ModMetadata.read(p);return mods.stream().noneMatch(m->MANAGED.contains(m.id()))&&mods.stream().anyMatch(m->!m.clientOnly());
 }
 public static boolean safeName(String name){return !name.isBlank()&&name.length()<=180&&!name.startsWith(".")&&!name.matches(".*[/\\\\\\r\\n:=].*")&&name.toLowerCase(Locale.ROOT).endsWith(".zip");}
 public static Snapshot scan(Path root)throws Exception{
  JsonArray selected=new JsonArray();Path options=root.resolve("options.txt");if(Files.isRegularFile(options)&&Files.size(options)<1048576)for(String line:Files.readAllLines(options))if(line.startsWith("resourcePacks:")){try{for(var x:JsonParser.parseString(line.substring(14)).getAsJsonArray())if(x.isJsonPrimitive()&&x.getAsString().startsWith("file/")&&safeName(x.getAsString().substring(5)))selected.add(x.getAsString());}catch(Exception ignored){}}
  Properties iris=new Properties();Path irisFile=root.resolve("config/iris.properties");if(Files.isRegularFile(irisFile)&&Files.size(irisFile)<65536)try(var in=Files.newBufferedReader(irisFile)){iris.load(in);}String shader=iris.getProperty("shaderPack","");boolean shadersEnabled=Boolean.parseBoolean(iris.getProperty("enableShaders","false"))&&safeName(shader);if(!shadersEnabled)shader="";
  Set<String> selectedFiles=new HashSet<>();for(var pack:selected)selectedFiles.add(pack.getAsString().substring(5));
  JsonArray entries=new JsonArray();Map<String,Path> files=new TreeMap<>();long total=0;
  for(String kind:List.of("mods","resourcepacks","shaderpacks")){var dir=root.resolve(kind);if(Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS))try(var list=Files.list(dir)){for(Path p:list.sorted().toList()){
   boolean mod=kind.equals("mods");String name=p.getFileName().toString();if(mod?!gameplay(p):!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)||!safeName(name))continue;
   if(kind.equals("resourcepacks")&&!selectedFiles.contains(name)||kind.equals("shaderpacks")&&!name.equals(shader))continue;
   long size=Files.size(p);if(size>MAX_FILE||(total+=size)>MAX_TOTAL||entries.size()>=512)throw new IOException("Shared files exceed the transfer limit");String hash=hash(p);files.putIfAbsent(hash,p);JsonObject e=new JsonObject();e.addProperty("hash",hash);e.addProperty("size",size);e.addProperty("kind",kind);e.addProperty("name",name);
   if(mod){var descriptor=ModMetadata.read(p).getFirst();e.addProperty("id",descriptor.id());e.addProperty("version",descriptor.version());}else{e.addProperty("id","pack_"+hash.substring(0,24));e.addProperty("version","1");}entries.add(e);
  }}}
  JsonObject m=new JsonObject();m.addProperty("schema",2);m.addProperty("minecraft",net.minecraft.SharedConstants.getCurrentVersion().id());m.addProperty("loader",Platform.loader());m.addProperty("loaderVersion",Platform.loaderVersion());m.add("files",entries);
  m.add("resourcePacks",selected);m.addProperty("shaderPack",shader);m.addProperty("shadersEnabled",shadersEnabled);validate(m);return new Snapshot(m,files);
 }
 public static List<Entry> validate(JsonObject m)throws Exception{
  int schema=m.get("schema").getAsInt();if(schema!=2||!m.get("minecraft").getAsString().equals(net.minecraft.SharedConstants.getCurrentVersion().id())||!m.get("loader").getAsString().equals(Platform.loader())||!m.get("loaderVersion").getAsString().equals(Platform.loaderVersion()))throw new IOException("Friend's game version or loader is unsupported");
  List<Entry> out=new ArrayList<>();Set<String> ids=new HashSet<>(),names=new HashSet<>();long total=0;var files=m.getAsJsonArray("files");if(files.size()>512)throw new IOException("Too many shared files");
  for(var v:files){var e=v.getAsJsonObject();String h=e.get("hash").getAsString(),id=e.get("id").getAsString(),version=e.get("version").getAsString(),kind=e.has("kind")?e.get("kind").getAsString():"mods",name=e.has("name")?e.get("name").getAsString():h+".jar";long n=e.get("size").getAsLong();boolean mod=kind.equals("mods");if(!Set.of("mods","resourcepacks","shaderpacks").contains(kind)||!h.matches("[a-f0-9]{64}")||!id.matches("[a-z][a-z0-9_-]{1,63}")||MANAGED.contains(id)||version.length()>128||n<1||n>MAX_FILE||(total+=n)>MAX_TOTAL||!(mod?ids.add(id):safeName(name)&&names.add(kind+":"+name)))throw new IOException("Invalid shared file manifest");out.add(new Entry(h,n,id,version,kind,name));}
  if(schema==2){if(m.getAsJsonArray("resourcePacks").size()>512)throw new IOException("Too many selected packs");for(var v:m.getAsJsonArray("resourcePacks"))if(!v.getAsString().startsWith("file/")||!names.contains("resourcepacks:"+v.getAsString().substring(5)))throw new IOException("Selected resource pack missing");String shader=m.get("shaderPack").getAsString();if(!shader.isEmpty()&&!names.contains("shaderpacks:"+shader))throw new IOException("Selected shader pack missing");}
  return out;
 }
 public static Set<String> hashes(JsonObject m)throws Exception{Set<String>s=new HashSet<>();for(var e:validate(m))s.add(e.kind+":"+(e.kind.equals("mods")?e.id:e.name)+":"+e.hash);s.add("resourcePacks:"+m.get("resourcePacks"));s.add("shaderPack:"+m.get("shaderPack")+":"+m.get("shadersEnabled"));return s;}
}
