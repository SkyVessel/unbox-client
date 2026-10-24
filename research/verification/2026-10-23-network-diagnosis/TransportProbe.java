import java.net.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;import icu.wuhui.voxlink.network.ReliableUdpTransport;
public class TransportProbe {
 public static void main(String[] args)throws Exception{run(1352,false);run(1352,true);}
 static void run(int mtu,boolean small)throws Exception{
  var loop=InetAddress.getLoopbackAddress();var a=new DatagramSocket(0,loop);var b=new DatagramSocket(0,loop);
  var counts=new ConcurrentHashMap<Integer,AtomicInteger>();var dropped=new AtomicInteger();var delayed=Executors.newSingleThreadScheduledExecutor();long began=System.currentTimeMillis();
  try(var proxy=new DatagramSocket(0,loop);var left=new ReliableUdpTransport(a,new InetSocketAddress(loop,proxy.getLocalPort()));var right=new ReliableUdpTransport(b,new InetSocketAddress(loop,proxy.getLocalPort()))){
   int pa=a.getLocalPort(),pb=b.getLocalPort();byte[] key=new byte[32];Arrays.fill(key,(byte)7);left.setAuthKey(key);right.setAuthKey(key);
   Thread.ofVirtual().start(()->{while(!proxy.isClosed())try{var p=new DatagramPacket(new byte[2048],2048);proxy.receive(p);int type=p.getData()[2];counts.computeIfAbsent(type,k->new AtomicInteger()).incrementAndGet();if(p.getLength()>mtu){dropped.incrementAndGet();continue;}byte[] data=Arrays.copyOf(p.getData(),p.getLength());int target=p.getPort()==pa?pb:pa;delayed.schedule(()->{try{proxy.send(new DatagramPacket(data,data.length,loop,target));}catch(IOException ignored){}},30,TimeUnit.MILLISECONDS);}catch(IOException | RejectedExecutionException ignored){}});
   left.start();right.start();left.getOutputStream().write(new byte[32]);byte[] hello=right.getInputStream().readNBytes(32);System.out.println("smallFrames="+small+" MTU="+mtu+" small handshake="+(hello.length==32));
   byte[] payload=new byte[128*1024];new Random(7).nextBytes(payload);
   var read=CompletableFuture.supplyAsync(()->{try{return right.getInputStream().readNBytes(payload.length);}catch(Exception e){throw new CompletionException(e);}});
   var send=CompletableFuture.runAsync(()->{try{if(small){for(int i=0;i<payload.length;i+=900)left.getOutputStream().write(payload,i,Math.min(900,payload.length-i));}else left.getOutputStream().write(payload);}catch(Exception e){throw new CompletionException(e);}});
   try{byte[] got=read.get(16,TimeUnit.SECONDS);System.out.println("smallFrames="+small+" MTU="+mtu+" bytes="+got.length+" equal="+Arrays.equals(payload,got));}catch(Exception e){System.out.println("smallFrames="+small+" MTU="+mtu+" read="+e.getClass().getSimpleName());}
   if(small){Thread.sleep(13000);left.getOutputStream().write(new byte[]{42});System.out.println("smallFrames="+small+" MTU="+mtu+" after idle="+right.getInputStream().read());}
   System.out.println("smallFrames="+small+" MTU="+mtu+" elapsed="+(System.currentTimeMillis()-began)+" dropped="+dropped+" frameCounts="+counts);
  }finally{delayed.shutdownNow();}
 }
}
