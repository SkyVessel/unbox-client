package dev.unbox.client;
import java.nio.file.*;
public class AsyncSettingsFileTest {
 public static void main(String[] args)throws Exception{
  Path root=Path.of(args[0]);var writer=new AsyncSettingsFile();
  for(int i=0;i<1000;i++){writer.save(root.resolve("world-a/settings"),"a="+i);writer.save(root.resolve("world-b/settings"),"b="+i);}
  writer.finish();
  if(!Files.readString(root.resolve("world-a/settings")).equals("a=999")||!Files.readString(root.resolve("world-b/settings")).equals("b=999"))throw new AssertionError("Latest snapshot lost or shutdown did not drain both worlds");
  Path obstacle=root.resolve("blocked");Files.writeString(obstacle,"block");writer.save(obstacle.resolve("settings"),"old");Thread.sleep(100);writer.save(obstacle.resolve("settings"),"latest");Files.delete(obstacle);Thread.sleep(5200);writer.finish();
  if(!Files.readString(obstacle.resolve("settings")).equals("latest"))throw new AssertionError("Retry replaced the latest snapshot");
  System.out.println("PASS: coalesced world snapshots, shutdown drain and failed-write recovery");
 }
}
