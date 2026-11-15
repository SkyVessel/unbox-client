import dev.unbox.client.EnvironmentManifest;
import java.nio.file.*;
import java.util.zip.*;

public class EnvironmentPreparationTest {
 static void fail(Path root,String expected)throws Exception{
  try{EnvironmentManifest.scan(root);throw new AssertionError("Invalid files accepted");}
  catch(EnvironmentManifest.PreparationException e){
   String message=EnvironmentManifest.preparationMessage(new java.util.concurrent.CompletionException(e));
   if(!message.contains(expected)||message.contains(root.toString()))throw new AssertionError(message);
  }
 }
 public static void main(String[] args)throws Exception{
  Path root=Files.createTempDirectory("unbox-sharing-preparation-");
  try{
   Path mods=Files.createDirectories(root.resolve("mods"));
   Files.writeString(root.resolve("options.txt"),"resourcePacks:[\"vanilla\",\"file/Removed Pack.zip\"]\n");
   fail(root,"Removed Pack.zip");
   Files.writeString(root.resolve("options.txt"),"resourcePacks:[\"vanilla\"]\n");
   Path config=Files.createDirectories(root.resolve("config"));
   Files.writeString(config.resolve("iris.properties"),"enableShaders=true\nshaderPack=Missing Shader.zip\n");
   fail(root,"Missing Shader.zip");
   Files.writeString(config.resolve("iris.properties"),"enableShaders=false\nshaderPack=Missing Shader.zip\n");
   EnvironmentManifest.scan(root);
   Path broken=mods.resolve("broken.jar");Files.writeString(broken,"unfinished download");
   fail(root,"mods/broken.jar");Files.delete(broken);
   try(var zip=new ZipOutputStream(Files.newOutputStream(mods.resolve("multiline.jar")))){
    zip.putNextEntry(new ZipEntry("fabric.mod.json"));
    zip.write("{\"id\":\"multiline\",\"version\":\"1\",\"description\":\"First line\nSecond line\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));zip.closeEntry();
   }
   if(EnvironmentManifest.scan(root).files().size()!=1)throw new AssertionError("Fabric-compatible description rejected");
   Files.copy(mods.resolve("multiline.jar"),mods.resolve("same-bytes.jar"));
   var deduplicated=EnvironmentManifest.scan(root);
   if(deduplicated.files().size()!=1||deduplicated.manifest().getAsJsonArray("files").size()!=1)throw new AssertionError("Identical JAR copies were not deduplicated");
   try(var zip=new ZipOutputStream(Files.newOutputStream(mods.resolve("different.jar")))){
    zip.putNextEntry(new ZipEntry("fabric.mod.json"));zip.write("{\"id\":\"multiline\",\"version\":\"2\"}".getBytes());zip.closeEntry();
   }
   fail(root,"mods/multiline.jar");
   System.out.println("Preparation checks passed: missing pack, missing shader, disabled shader, corrupt JAR, safe actionable errors, multiline Fabric metadata, identical duplicate deduplication, conflicting copy rejection");
  }finally{try(var files=Files.walk(root)){for(Path p:files.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(p);}}
 }
}
