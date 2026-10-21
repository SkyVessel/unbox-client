package dev.unbox.client;

import com.google.gson.*;
import icu.wuhui.voxlink.network.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Invitation-scoped VoxLink transport. Carries the original encrypted Minecraft byte stream. */
public final class VoxLinkTransport {
 private static final ScheduledExecutorService CLOCK=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"unbox-route-timeouts");t.setDaemon(true);return t;});
 private static final List<String> STUN=List.of("stun:stun.cloudflare.com:3478","stun:stun.l.google.com:19302");
 private static final int DEADLINE=25000;
 public record Route(String address,String kind){}
 public static final class Session implements AutoCloseable {
  public final String invite,attempt;public final boolean host;
  public final CompletableFuture<Route> result=new CompletableFuture<>();
  private final byte[] key;private final int gamePort;private final Supplier<JsonObject> remote;
  private final Consumer<JsonObject> publish;private final Consumer<String> diagnostic;
  private final Set<AutoCloseable> pending=ConcurrentHashMap.newKeySet(),active=ConcurrentHashMap.newKeySet();
  private final List<UdpHolePuncher> punches=new CopyOnWriteArrayList<>();
  private final AtomicBoolean closed=new AtomicBoolean(),retired=new AtomicBoolean(),chosen=new AtomicBoolean();
  private final long until=System.currentTimeMillis()+DEADLINE;
  private final boolean fixture;private final List<String> stunServers;
  private volatile int mappedPort;private final TcpHolePuncher tcpPunch=new TcpHolePuncher();private volatile ServerSocket listener;
  public Session(String invite,String attempt,String secret,boolean host,int gamePort,Supplier<JsonObject> remote,Consumer<JsonObject> publish,Consumer<String> diagnostic){this(invite,attempt,secret,host,gamePort,remote,publish,diagnostic,false,STUN);}
  // Explicit fixture injection. Production never enables loopback candidates or replaces STUN through room data.
  Session(String invite,String attempt,String secret,boolean host,int gamePort,Supplier<JsonObject> remote,Consumer<JsonObject> publish,Consumer<String> diagnostic,boolean fixture,List<String> stunServers){
   this.invite=invite;this.attempt=attempt;this.host=host;this.gamePort=gamePort;this.remote=remote;this.publish=publish;this.diagnostic=diagnostic;this.fixture=fixture;this.stunServers=List.copyOf(stunServers);
   try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(HexFormat.of().parseHex(secret),"HmacSHA256"));key=mac.doFinal(("unbox-voxlink-v1|"+invite+"|"+attempt).getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalArgumentException("Invalid invitation",e);}
  }
  public void start(){CLOCK.schedule(this::retire,DEADLINE,TimeUnit.MILLISECONDS);Thread.ofVirtual().name("unbox-route-prepare").start(this::negotiate);}
  private void negotiate(){
   try{
    JsonArray candidates=new JsonArray();listener=new ServerSocket(0);listener.setSoTimeout(500);track(listener,pending);
    Thread.ofVirtual().name("unbox-route-accept").start(this::accept);
    List<String> ipv6=addresses();for(String ip:ipv6)add(candidates,"tcp",ip,listener.getLocalPort(),0,0);
    // Two independent source mappings; same slot is paired on both peers. Both directions punch.
    for(int slot=0;slot<2&&negotiating();slot++){
     UdpHolePuncher punch=new UdpHolePuncher();PunchParams params=PunchParams.fromProfile(PunchProfile.DEFAULT);params.timeoutMs=12000;params.portRange=16;params.sendInterval=100;params.sendMinRounds=1;params.sendMinPass=1;punch.setPunchParams(params);punch.setAuthKey(key);punch.createSocket();punches.add(punch);if(!negotiating()){punch.cancel();break;}
     StunProbe.PublicMappedAddress first=fixture?new StunProbe.PublicMappedAddress("127.0.0.1",punch.getSocket().getLocalPort()):punch.discoverMappedAddress(stunServers);
     StunProbe.PublicMappedAddress second=fixture?first:punch.discoverMappedAddress(stunServers.size()>1?List.of(stunServers.get(1)):stunServers);
     if(first!=null&&allowed(first.ip(),fixture)){int range=second!=null&&(!first.ip().equals(second.ip())||first.port()!=second.port())?16:0;add(candidates,"udp",first.ip(),first.port(),slot,range);}
     if(slot==0&&first!=null&&!fixture){
      add(candidates,"tcp-punch",first.ip(),30000+((key[0]&255)*256+(key[1]&255))%20000,0,0);
      // UPnP runs off the game thread; the mapping is temporary and owned by this session.
      int localPort=listener.getLocalPort();var mapping=CompletableFuture.supplyAsync(()->UPnPManager.openPort(localPort,"Unbox invitation"));
      mapping.whenComplete((m,e)->{if(m!=null&&m.success()){mappedPort=localPort;if(closed.get()||(!negotiating()&&active.isEmpty()))unmap();}});
      try{var m=mapping.get(2500,TimeUnit.MILLISECONDS);if(m.success()&&allowed(first.ip(),false))add(candidates,"tcp",first.ip(),m.externalPort(),0,0);}catch(Exception ignored){}
     }
    }
    if(fixture&&Boolean.getBoolean("unbox.testTcpCandidate"))add(candidates,"tcp","127.0.0.1",listener.getLocalPort(),0,0);
    if(!negotiating())return;
    JsonObject offer=new JsonObject();offer.add("candidates",candidates);publish.accept(offer);diagnostic.accept("candidates_ready");
    JsonObject peer=null;
    while(negotiating()){peer=remote.get();if(peer!=null&&peer.has("candidates"))break;Thread.sleep(100);}
    if(peer==null||!peer.has("candidates")||!negotiating())return;
    Set<Integer> used=new HashSet<>();int count=0;
    for(var value:peer.getAsJsonArray("candidates")){
     if(++count>8)break;var c=value.getAsJsonObject();String ip=c.get("host").getAsString(),kind=c.get("kind").getAsString();int port=c.get("port").getAsInt();if(!allowed(ip,fixture)||port<1||port>65535)continue;
     if(kind.equals("tcp-punch")){diagnostic.accept("tcp_punch");tcpPunch.punchAsync(ip,port,4,closed).whenComplete((socket,error)->{if(socket!=null){track(socket,pending);Thread.ofVirtual().name("unbox-route-tcp-punch").start(()->{try{socket.setSoTimeout(6000);establish(new Link(socket.getInputStream(),socket.getOutputStream(),socket),"tcp_punch");}catch(Exception e){discard(socket);}});}});}
     else if(kind.equals("tcp")){Thread.ofVirtual().name("unbox-route-tcp").start(()->dial(ip,port));}
     else if(kind.equals("udp")){int slot=c.get("slot").getAsInt();if(slot<0||slot>=punches.size()||!used.add(slot))continue;UdpHolePuncher punch=punches.get(slot);int range=Math.min(16,Math.max(0,c.get("range").getAsInt()));
      diagnostic.accept(range>0?"udp_prediction":"udp_punch");
      punch.punchWithPortPrediction(ip,port,range).whenComplete((r,e)->{if(e==null&&r!=null&&r.isSuccess()&&negotiating()){
       punch.markSocketTransferred();punch.stopPunch();var transport=new ReliableUdpTransport(r.successSocket,new InetSocketAddress(ip,punch.getRemotePort()));transport.setAuthKey(key);track(transport,pending);transport.start();Thread.ofVirtual().name("unbox-route-udp").start(()->establish(new Link(transport.getInputStream(),transport.getOutputStream(),transport),"udp"));
      }});
     }
    }
   }catch(Exception e){diagnostic.accept("candidate_failure");} // bounded deadline still permits inbound/reverse candidates
  }
  private void accept(){while(negotiating()){try{Socket socket=listener.accept();socket.setTcpNoDelay(true);socket.setSoTimeout(6000);track(socket,pending);Thread.ofVirtual().name("unbox-route-reverse").start(()->{try{establish(new Link(socket.getInputStream(),socket.getOutputStream(),socket),"tcp");}catch(IOException e){discard(socket);}});}catch(SocketTimeoutException ignored){}catch(IOException e){break;}}}
  private void dial(String ip,int port){Socket socket=new Socket();track(socket,pending);try{socket.setTcpNoDelay(true);socket.connect(new InetSocketAddress(ip,port),2500);socket.setSoTimeout(6000);establish(new Link(socket.getInputStream(),socket.getOutputStream(),socket),ip.contains(":")?"ipv6":"tcp");}catch(Exception e){discard(socket);}}
  private record Link(InputStream in,OutputStream out,AutoCloseable owner){}
  private void establish(Link link,String kind){
   ScheduledFuture<?> timeout=CLOCK.schedule(()->{if(pending.remove(link.owner))quiet(link.owner);},6,TimeUnit.SECONDS);
   boolean selectedHere=false;
   try{
    // Authenticate the raw bridge before exposing a local Minecraft socket. Full private login follows unchanged.
    DataOutputStream out=new DataOutputStream(link.out);DataInputStream in=new DataInputStream(link.in);out.writeInt(0x55425831);out.write(proof(host?"host":"guest"));out.flush();if(in.readInt()!=0x55425831||!MessageDigest.isEqual(in.readNBytes(32),proof(host?"guest":"host")))throw new IOException("Route proof failed");
    if(!negotiating())throw new IOException("Route expired");
    if(!host){if(in.read()!=1)throw new IOException("Host game unavailable");if(!chosen.compareAndSet(false,true))throw new IOException("Another route won");selectedHere=true;}
    pending.remove(link.owner);track(link.owner,active);timeout.cancel(false);if(link.owner instanceof Socket s)s.setSoTimeout(0);
    if(host){Socket game=new Socket();track(game,active);game.setTcpNoDelay(true);try{game.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(),gamePort),3000);out.write(1);out.flush();pipe(link,game);}finally{discard(game);}}
    else{
     ServerSocket local=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"));local.setSoTimeout(15000);track(local,active);retire();result.complete(new Route("127.0.0.1:"+local.getLocalPort(),kind));
     try{Socket game=local.accept();game.setTcpNoDelay(true);track(game,active);pipe(link,game);}finally{discard(local);}
    }
   }catch(Exception e){discard(link.owner);if(selectedHere&&!result.isDone())result.complete(null);}finally{timeout.cancel(false);}
  }
  private byte[] proof(String side)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));return mac.doFinal(side.getBytes(StandardCharsets.US_ASCII));}
  private void pipe(Link link,Socket game)throws IOException{
   Thread reverse=Thread.ofVirtual().name("unbox-route-stream").start(()->{try{game.getInputStream().transferTo(link.out);link.out.flush();}catch(IOException ignored){}finally{discard(game);discard(link.owner);}});
   try{link.in.transferTo(game.getOutputStream());}finally{discard(game);discard(link.owner);reverse.interrupt();}
  }
  private void track(AutoCloseable c,Set<AutoCloseable> set){set.add(c);if(closed.get()||(set==pending&&!negotiating())){set.remove(c);quiet(c);}}
  private void discard(AutoCloseable c){pending.remove(c);active.remove(c);quiet(c);}
  private static void quiet(AutoCloseable c){try{c.close();}catch(Exception ignored){}}
  public void retire(){if(!retired.compareAndSet(false,true))return;tcpPunch.cancel();for(var p:punches)p.cancel();pending.forEach(Session::quiet);pending.clear();if(active.isEmpty())unmap();if(!host&&!chosen.get()){diagnostic.accept("direct_exhausted");result.complete(null);}}
  private synchronized void unmap(){if(mappedPort>0){int port=mappedPort;mappedPort=0;Thread.ofVirtual().name("unbox-upnp-cleanup").start(()->UPnPManager.closePort(port));}}
  public boolean negotiating(){return !closed.get()&&!retired.get()&&System.currentTimeMillis()<until;}
  public boolean idle(){return active.isEmpty()&&!negotiating();}
  @Override public void close(){closed.set(true);retire();active.forEach(Session::quiet);active.clear();unmap();result.complete(null);}
 }
 private static void add(JsonArray array,String kind,String host,int port,int slot,int range){if(array.size()>=8)return;var c=new JsonObject();c.addProperty("kind",kind);c.addProperty("host",host);c.addProperty("port",port);c.addProperty("slot",slot);c.addProperty("range",range);array.add(c);}
 static boolean allowed(String host,boolean fixture){try{if(host==null||host.length()>64||!host.matches("[0-9a-fA-F:.]+"))return false;InetAddress ip=InetAddress.getByName(host);if(fixture&&ip.isLoopbackAddress())return true;return !ip.isAnyLocalAddress()&&!ip.isLoopbackAddress()&&!ip.isLinkLocalAddress()&&!ip.isSiteLocalAddress()&&!ip.isMulticastAddress()&&!(ip instanceof Inet6Address&&(ip.getAddress()[0]&0xfe)==0xfc);}catch(Exception e){return false;}}
 private static List<String> addresses(){var out=new ArrayList<String>();try{for(var ni:Collections.list(NetworkInterface.getNetworkInterfaces()))if(ni.isUp()&&!ni.isLoopback())for(var ip:Collections.list(ni.getInetAddresses()))if(ip instanceof Inet6Address&&allowed(ip.getHostAddress(),false)&&out.size()<3)out.add(ip.getHostAddress());}catch(Exception ignored){}return out;}
}
