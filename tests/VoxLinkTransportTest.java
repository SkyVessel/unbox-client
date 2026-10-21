package dev.unbox.client;
import com.google.gson.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
public final class VoxLinkTransportTest {
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
 public static void main(String[] args)throws Exception{
  check(!VoxLinkTransport.allowed("127.0.0.1",false)&&!VoxLinkTransport.allowed("::ffff:127.0.0.1",false)&&!VoxLinkTransport.allowed("192.168.1.1",false)&&!VoxLinkTransport.allowed("fe80::1",false)&&!VoxLinkTransport.allowed("fc00::1",false)&&!VoxLinkTransport.allowed("metadata.google.internal",false),"private, local, and DNS route targets rejected");
  check(VoxLinkTransport.allowed("2606:4700::1111",false)&&VoxLinkTransport.allowed("1.1.1.1",false),"public literal candidates accepted");
  run(false);run(true);rejectAndExpire();lossy();
  AtomicReference<JsonObject> offer=new AtomicReference<>();try(var s=new VoxLinkTransport.Session("invitation",UUID.randomUUID().toString(),"aa".repeat(32),false,0,()->null,offer::set,e->{},true,List.of())){s.start();while(offer.get()==null)Thread.sleep(10);s.close();check(s.result.get(2,TimeUnit.SECONDS)==null,"cancellation releases waiting candidate exchange");}
 }
 static void run(boolean tcp)throws Exception{
  if(tcp)System.setProperty("unbox.testTcpCandidate","true");else System.clearProperty("unbox.testTcpCandidate");
  String attempt=UUID.randomUUID().toString();AtomicReference<JsonObject> h=new AtomicReference<>(),g=new AtomicReference<>();List<String> events=new CopyOnWriteArrayList<>();
  try(ServerSocket echo=new ServerSocket(0,5,InetAddress.getLoopbackAddress());var host=new VoxLinkTransport.Session("invite",attempt,"ab".repeat(32),true,echo.getLocalPort(),g::get,o->h.set(filter(o,tcp)),events::add,true,List.of());var guest=new VoxLinkTransport.Session("invite",attempt,"ab".repeat(32),false,0,h::get,o->g.set(filter(o,tcp)),events::add,true,List.of())){
   Thread.ofVirtual().start(()->{try{while(!echo.isClosed()){var socket=echo.accept();Thread.ofVirtual().start(()->{try(socket){socket.getInputStream().transferTo(socket.getOutputStream());}catch(Exception ignored){}});}}catch(Exception ignored){}});
   host.start();guest.start();var route=guest.result.get(30,TimeUnit.SECONDS);check(route!=null,"negotiated "+(tcp?"TCP":"VoxLink UDP")+" route");check(route.kind().equals(tcp?"tcp":"udp"),"selected path recorded");
   try(Socket client=new Socket("127.0.0.1",Integer.parseInt(route.address().split(":")[1]))){client.setSoTimeout(15000);byte[] sent=new byte[2*1024*1024];new Random(7).nextBytes(sent);var send=CompletableFuture.runAsync(()->{try{client.getOutputStream().write(sent);client.getOutputStream().flush();}catch(Exception e){throw new CompletionException(e);}});byte[] got=client.getInputStream().readNBytes(sent.length);send.get(20,TimeUnit.SECONDS);check(Arrays.equals(sent,got),"2 MiB bidirectional byte stream preserved over "+route.kind());}
  }
 }
 static void rejectAndExpire()throws Exception{
  System.setProperty("unbox.testTcpCandidate","true");AtomicReference<JsonObject> h=new AtomicReference<>(),g=new AtomicReference<>();String attempt=UUID.randomUUID().toString();var events=new CopyOnWriteArrayList<String>();
  try(var host=new VoxLinkTransport.Session("invite",attempt,"aa".repeat(32),true,1,g::get,o->h.set(filter(o,true)),e->{},true,List.of());var guest=new VoxLinkTransport.Session("invite",attempt,"bb".repeat(32),false,0,h::get,o->g.set(filter(o,true)),events::add,true,List.of())){
   host.start();guest.start();check(guest.result.get(30,TimeUnit.SECONDS)==null,"wrong transport secret never exposes a local game bridge");check(events.contains("direct_exhausted"),"failed direct methods expire into relay fallback");check(!guest.negotiating(),"expired negotiation cannot publish or accept late candidates");
  }
 }
 static void lossy()throws Exception{
  var loop=InetAddress.getLoopbackAddress();var a=new DatagramSocket(0,loop);var b=new DatagramSocket(0,loop);
  try(var proxy=new DatagramSocket(0,loop);var left=new icu.wuhui.voxlink.network.ReliableUdpTransport(a,new InetSocketAddress(loop,proxy.getLocalPort()));var right=new icu.wuhui.voxlink.network.ReliableUdpTransport(b,new InetSocketAddress(loop,proxy.getLocalPort()))){
   int portA=a.getLocalPort(),portB=b.getLocalPort();byte[] key=new byte[32];Arrays.fill(key,(byte)7);left.setAuthKey(key);right.setAuthKey(key);
   var delayed=Executors.newSingleThreadScheduledExecutor();
   Thread.ofVirtual().start(()->{int n=0;while(!proxy.isClosed())try{var packet=new DatagramPacket(new byte[2048],2048);proxy.receive(packet);if(++n%17==0)continue;byte[] data=Arrays.copyOf(packet.getData(),packet.getLength());int target=packet.getPort()==portA?portB:portA;Runnable send=()->{try{proxy.send(new DatagramPacket(data,data.length,loop,target));}catch(IOException ignored){}};if(n%11==0)delayed.schedule(send,30,TimeUnit.MILLISECONDS);else send.run();if(n%19==0)send.run();}catch(IOException ignored){}});
   try{left.start();right.start();byte[] expected=new byte[512*1024];new Random(9).nextBytes(expected);var send=CompletableFuture.runAsync(()->{try{left.getOutputStream().write(expected);}catch(IOException e){throw new CompletionException(e);}});var read=CompletableFuture.supplyAsync(()->{try{return right.getInputStream().readNBytes(expected.length);}catch(IOException e){throw new CompletionException(e);}});check(Arrays.equals(expected,read.get(35,TimeUnit.SECONDS)),"VoxLink reliable UDP preserves bytes with loss, reordering and duplicates");send.get(5,TimeUnit.SECONDS);}finally{delayed.shutdownNow();}
  }
 }
 static JsonObject filter(JsonObject o,boolean tcp){JsonArray a=new JsonArray();for(var c:o.getAsJsonArray("candidates"))if(c.getAsJsonObject().get("kind").getAsString().equals(tcp?"tcp":"udp"))a.add(c);JsonObject f=new JsonObject();f.add("candidates",a);return f;}
}
