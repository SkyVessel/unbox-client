package dev.unbox.client;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
final class ModMetadata {
 record Entry(String id,String version,boolean clientOnly){}
 static List<Entry> read(Path path)throws Exception{try(var z=new ZipFile(path.toFile())){var f=z.getEntry("META-INF/neoforge.mods.toml");if(f==null||f.getSize()>262144)throw new java.io.IOException("Not a NeoForge mod");try(var in=z.getInputStream(f)){byte[] bytes=in.readNBytes(262145);if(bytes.length>262144)throw new java.io.IOException("Mod metadata too large");var m=new TomlParser().parse(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));List<UnmodifiableConfig> mods=m.get("mods");if(mods==null||mods.isEmpty()||mods.size()>64)throw new java.io.IOException("Invalid mod metadata");List<Entry> entries=new ArrayList<>();for(var mod:mods)entries.add(new Entry(mod.get("modId"),mod.getOrElse("version",""),Boolean.TRUE.equals(mod.get("clientSideOnly"))||Boolean.TRUE.equals(m.get("clientSideOnly"))));return entries;}}}
}
