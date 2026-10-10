package dev.unbox.client;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import com.google.gson.*;
final class ModMetadata {
 record Entry(String id,String version,boolean clientOnly){}
 static List<Entry> read(Path path)throws Exception{try(var z=new ZipFile(path.toFile())){var f=z.getEntry("fabric.mod.json");if(f==null||f.getSize()>262144)throw new java.io.IOException("Not a Fabric mod");try(var in=z.getInputStream(f)){byte[] bytes=in.readNBytes(262145);if(bytes.length>262144)throw new java.io.IOException("Mod metadata too large");var m=JsonParser.parseString(new String(bytes,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();return List.of(new Entry(m.get("id").getAsString(),m.has("version")?m.get("version").getAsString():"",m.has("environment")&&!m.get("environment").getAsString().equals("*")));}}}
}
