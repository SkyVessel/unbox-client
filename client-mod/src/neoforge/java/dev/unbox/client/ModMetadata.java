package dev.unbox.client;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.*;
import java.io.*;
import com.google.gson.JsonParser;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
final class ModMetadata {
 record Entry(String id,String version,boolean clientOnly){}
 private static List<Entry> parse(byte[] bytes)throws IOException{
  if(bytes.length>262144)throw new IOException("Mod metadata too large");
  var m=new TomlParser().parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
  List<UnmodifiableConfig> mods=m.get("mods");if(mods==null||mods.isEmpty()||mods.size()>64)throw new IOException("Invalid mod metadata");
  List<Entry> entries=new ArrayList<>();for(var mod:mods)entries.add(new Entry(mod.get("modId"),mod.getOrElse("version",""),Boolean.TRUE.equals(mod.get("clientSideOnly"))||Boolean.TRUE.equals(m.get("clientSideOnly"))));return entries;
 }
 static List<Entry> read(Path path)throws Exception{
  try(var z=new ZipFile(path.toFile())){
   var f=z.getEntry("META-INF/neoforge.mods.toml");
   if(f!=null)try(var in=z.getInputStream(f)){return parse(in.readNBytes(262145));}
   // NeoForge language/library containers (e.g. Kotlin For Forge) keep their mod
   // descriptor in a declared Jar-in-Jar dependency instead of the outer archive.
   var index=z.getEntry("META-INF/jarjar/metadata.json");if(index==null)throw new IOException("Not a NeoForge mod");
   byte[] data;try(var in=z.getInputStream(index)){data=in.readNBytes(262145);}if(data.length>262144)throw new IOException("Jar-in-Jar metadata too large");
   var jars=JsonParser.parseString(new String(data,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("jars");
   if(jars==null||jars.size()>64)throw new IOException("Invalid Jar-in-Jar metadata");
   List<Entry> entries=new ArrayList<>();long total=0;
   for(var item:jars){
    String name=item.getAsJsonObject().get("path").getAsString();if(!name.startsWith("META-INF/jarjar/")||name.contains("..")||!name.endsWith(".jar"))throw new IOException("Invalid embedded mod path");
    var nested=z.getEntry(name);if(nested==null)throw new IOException("Missing embedded mod");
    try(var in=new ZipInputStream(z.getInputStream(nested))){ZipEntry e;int count=0;
     while((e=in.getNextEntry())!=null){if(++count>16384)throw new IOException("Too many embedded files");
      if(e.getName().equals("META-INF/neoforge.mods.toml")){entries.addAll(parse(in.readNBytes(262145)));break;}
      byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){total+=n;if(total>256L*1024*1024)throw new IOException("Embedded metadata scan exceeds limit");}
     }
    }
   }
   if(entries.isEmpty()||entries.size()>64)throw new IOException("Invalid embedded mod metadata");return entries;
  }
 }
}
