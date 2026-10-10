package dev.unbox.client;

import com.google.gson.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.server.players.*;
import io.netty.channel.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Local launcher bridge; cloud credentials never enter the game directory. */
public final class SocialBridge {
 private static final Path ROOT=Platform.gameDir();
 private static final Gson JSON=new Gson();
 private static final ExecutorService IO=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("unbox-friends-io",0).factory());
 public static volatile JsonObject view=new JsonObject();
 public static volatile String message="";
 private static volatile JsonObject room;
 private static volatile boolean guarded,privateAuthentication;
 private static UUID owner;
 private static final Set<UUID> INVITED=ConcurrentHashMap.newKeySet();
 public static boolean guarded(){return guarded;}
 public static boolean privateAuthentication(){return guarded&&privateAuthentication;}
 public static boolean hosting(){return room!=null;}
 public static boolean allowed(UUID id){return !guarded||INVITED.contains(id);}
 public static void stopSharing(){SocialLogin.clearHost();INVITED.clear();if(owner!=null)INVITED.add(owner);kickUninvited();if(discovery!=null){discovery.close();discovery=null;}room=null;relay="";relayStarted=0;try{if(link.e4mc.E4mcClient.session!=null)link.e4mc.E4mcClient.session.stop();}catch(LinkageError ignored){}message="Sharing stopped. Reopen the world to host again.";}
 private static volatile String relay="",pendingJoin="";
 private static volatile boolean probing;
 private static volatile DatagramSocket discovery;
 private static volatile ChannelHandler handler;private static volatile EventLoopGroup group;
 private static int ticks;private static volatile boolean queued;private static boolean wasWorld;
 private static String lastAction="",lastAck="";private static boolean rejoined;
 private static volatile long relayStarted,joinStarted;private static volatile boolean relayFailed;
 public static boolean relayFailed(){return room!=null&&relayFailed;}
 public static void retryRelay(){if(!relayFailed())return;try{if(link.e4mc.E4mcClient.session!=null)link.e4mc.E4mcClient.session.stop();}catch(LinkageError ignored){}relayStarted=0;relayFailed=false;relay="";startRelay();}
 public static void init(){SocialLogin.init();EnvironmentSync.init();try{link.e4mc.Config.INSTANCE.hostEnabled.setOverride(false);link.e4mc.Config.INSTANCE.dialtoneHostEnabled.setOverride(false);}catch(LinkageError e){message="Update the profile to enable world sharing";}}
 public static void handler(ChannelHandler h){handler=h;}public static void group(EventLoopGroup g){group=g;}
 public static void relayDomain(String domain){if(room==null||relayStarted==0){try{if(link.e4mc.E4mcClient.session!=null)link.e4mc.E4mcClient.session.stop();}catch(LinkageError ignored){}return;}if(room!=null&&relayStarted>0&&domain.matches("[a-z0-9.-]{1,200}\\.e4mc\\.link")){relay=domain;message="Internet sharing ready";}}
 public static String text(JsonObject v,String k){return v.has(k)&&!v.get(k).isJsonNull()?v.get(k).getAsString():"";}
 public static boolean flag(JsonObject v,String k){return v.has(k)&&!v.get(k).isJsonNull()&&v.get(k).getAsBoolean();}
 public static List<JsonObject> rows(String key){var out=new ArrayList<JsonObject>();if(view.has(key)&&view.get(key).isJsonArray())for(var v:view.getAsJsonArray(key))if(v.isJsonObject())out.add(v.getAsJsonObject());return out;}
 private static JsonObject read(String name){try{Path p=ROOT.resolve(name);if(Files.size(p)>200000)return new JsonObject();return JsonParser.parseString(Files.readString(p)).getAsJsonObject();}catch(Exception e){return new JsonObject();}}
 private static void write(String name,JsonObject value)throws Exception{Path p=ROOT.resolve(name),tmp=ROOT.resolve(".unbox-"+UUID.randomUUID()+".tmp");if(Files.getFileStore(ROOT).supportsFileAttributeView("posix"))Files.createFile(tmp,java.nio.file.attribute.PosixFilePermissions.asFileAttribute(java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")));else Files.createFile(tmp);Files.writeString(tmp,JSON.toJson(value));Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}
 public static void command(String op,String id){JsonObject c=new JsonObject();c.addProperty("requestId",UUID.randomUUID().toString());c.addProperty("op",op);c.addProperty("id",id);c.addProperty("at",System.currentTimeMillis());IO.submit(()->{try{write("unbox-social-command.json",c);}catch(Exception e){message="Could not contact launcher";}});}
 public static void tick(Minecraft mc){
  EnvironmentSync.tick(mc);
  boolean inWorld=mc.getSingleplayerServer()!=null&&mc.level!=null;if(wasWorld&&!inWorld)stop();wasWorld=inWorld;
  if(++ticks%20!=0||queued)return;queued=true;
  JsonObject world=new JsonObject();world.addProperty("updated",System.currentTimeMillis());world.addProperty("accountName",mc.getUser().getName());world.addProperty("accountUuid",mc.getUser().getProfileId().toString().replace("-",""));world.addProperty("inWorld",inWorld);world.addProperty("canInvite",inWorld&&verifiedAccount());world.addProperty("error",message);if(room!=null){JsonObject r=room.deepCopy();if(!relay.isEmpty())r.addProperty("relay",relay);world.add("room",r);world.add("grants",SocialLogin.secrets());}
  IO.submit(()->{try{write("unbox-world.json",world);JsonObject next=read("unbox-social-view.json");if(next.has("updated")&&System.currentTimeMillis()-next.get("updated").getAsLong()<60000){view=next;if(flag(next,"connected")&&message.equals("Launcher disconnected"))message="";mc.execute(()->{if(guarded&&flag(next,"connected")){Set<UUID> accepted=new HashSet<>();if(owner!=null)accepted.add(owner);for(var f:rows("friends"))if(f.get("accepted").getAsInt()==1)try{accepted.add(UUID.fromString(text(f,"uuid").replaceFirst("(........)(....)(....)(....)(............)","$1-$2-$3-$4-$5")));}catch(IllegalArgumentException ignored){}INVITED.retainAll(accepted);SocialLogin.retain(INVITED);kickUninvited();}});}else{JsonObject cached=view.deepCopy();cached.addProperty("connected",false);cached.add("invites",new JsonArray());if(cached.has("friends"))for(var f:cached.getAsJsonArray("friends"))f.getAsJsonObject().addProperty("online",false);view=cached;}
   if(!rejoined&&flag(view,"connected")){JsonObject resume=read("unbox-rejoin.json");if(resume.has("at")&&System.currentTimeMillis()-resume.get("at").getAsLong()<180000){rejoined=true;Files.deleteIfExists(ROOT.resolve("unbox-rejoin.json"));mc.execute(()->join(text(resume,"id")));}}
   JsonObject action=read("unbox-social-action.json");String id=text(action,"requestId");if(!id.isEmpty()&&!id.equals(lastAction)&&action.has("at")&&System.currentTimeMillis()-action.get("at").getAsLong()<15000){lastAction=id;mc.execute(()->{if(text(action,"op").equals("invite"))invite(text(action,"id"));else if(text(action,"op").equals("join"))join(text(action,"id"));});}
   JsonObject ack=read("unbox-social-ack.json");String aid=text(ack,"requestId");if(!aid.isEmpty()&&!aid.equals(lastAck)){lastAck=aid;message=flag(ack,"ok")?"Invitation updated":text(ack,"error");}
   if(room!=null&&rows("invites").stream().anyMatch(i->text(i,"sender").equals(self())&&i.has("relay_requested")&&i.get("relay_requested").getAsInt()==1))mc.execute(SocialBridge::startRelay);
   if(!pendingJoin.isEmpty()&&System.currentTimeMillis()-joinStarted>75000){pendingJoin="";message="Connection timed out. Ask your friend to retry the relay.";}
   if(!pendingJoin.isEmpty()&&!probing){var invitation=findInvite(pendingJoin);if(invitation==null){pendingJoin="";message="Invitation expired";}else{String address=text(invitation.getAsJsonObject("room"),"relay");if(!address.isEmpty()){pendingJoin="";mc.execute(()->connect(address));}}}
  }catch(Exception e){message="Could not synchronize friends";}finally{queued=false;}});
 }
 private static String self(){return view.has("self")?text(view.getAsJsonObject("self"),"id"):"";}
 private static boolean verifiedAccount(){return flag(view,"connected")&&view.has("self")&&Set.of("microsoft","unbox").contains(text(view.getAsJsonObject("self"),"kind"));}
 public static void invite(String id){
  var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(server==null||mc.level==null){message="Open a singleplayer world first";return;}
  var friend=rows("friends").stream().filter(f->text(f,"id").equals(id)&&f.get("accepted").getAsInt()==1).findFirst().orElse(null);
  if(friend==null||!flag(view,"connected")){message="Friend is unavailable";return;}if(!verifiedAccount()||!Set.of("microsoft","unbox").contains(text(friend,"kind"))){message="Sign in with Microsoft or an Unbox account";return;}
  try{
   UUID peer=UUID.fromString(text(friend,"uuid").replaceFirst("(........)(....)(....)(....)(............)","$1-$2-$3-$4-$5"));
   if(room==null&&server.isPublished()){message="Reopen this world before using private invites";return;}
   if(room==null){
    privateAuthentication=view.has("protocol")&&view.get("protocol").getAsInt()>=2;
    if(!privateAuthentication&&(!text(view.getAsJsonObject("self"),"kind").equals("microsoft")||!text(friend,"kind").equals("microsoft"))){message="Account service update is required for Unbox invitations";return;}
    // Keep vanilla encryption; remote peers must prove an Unbox invitation before admission.
    INVITED.clear();owner=mc.getUser().getProfileId();INVITED.add(owner);guarded=true;server.setUsesAuthentication(true);
    int port;try(var s=new java.net.ServerSocket(0)){port=s.getLocalPort();}
    if(!server.publishServer(server.getDefaultGameType(),false,port)){message="World could not be opened";return;}
    JsonObject r=new JsonObject();r.addProperty("id",UUID.randomUUID().toString());r.addProperty("protocol",privateAuthentication?2:1);r.addProperty("name",server.getWorldData().getLevelName());r.addProperty("port",server.getPort());r.addProperty("probeToken",UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""));JsonArray lan=new JsonArray();
    for(var ni:Collections.list(NetworkInterface.getNetworkInterfaces()))if(ni.isUp()&&!ni.isLoopback())for(var a:Collections.list(ni.getInetAddresses()))if(a instanceof Inet4Address&&a.isSiteLocalAddress()&&lan.size()<8)lan.add(a.getHostAddress());r.add("lan",lan);
    discovery=new DatagramSocket(0);r.addProperty("probePort",discovery.getLocalPort());room=r;relay="";relayFailed=false;startDiscovery(discovery,text(r,"probeToken"));
   }
   if(privateAuthentication)SocialLogin.grant(id,peer,text(friend,"name"),text(room,"id"));INVITED.add(peer);
   JsonObject world=new JsonObject();world.addProperty("updated",System.currentTimeMillis());world.addProperty("accountName",mc.getUser().getName());world.addProperty("accountUuid",mc.getUser().getProfileId().toString().replace("-",""));JsonObject current=room.deepCopy();if(!relay.isEmpty())current.addProperty("relay",relay);world.add("room",current);world.add("grants",SocialLogin.secrets());world.addProperty("inWorld",true);world.addProperty("canInvite",true);message="Sending invitation…";
   IO.submit(()->{try{write("unbox-world.json",world);command("send",id);}catch(Exception e){message="Could not send invitation";}});
  }catch(Exception e){message="Could not prepare world invitation";}
 }
 private static void startDiscovery(DatagramSocket socket,String token){Thread.ofVirtual().name("unbox-lan-discovery").start(()->{byte[] expected=("UNBOX?"+token).getBytes(StandardCharsets.US_ASCII),answer=("UNBOX!"+token).getBytes(StandardCharsets.US_ASCII);while(!socket.isClosed()){try{byte[] b=new byte[128];var p=new DatagramPacket(b,b.length);socket.receive(p);if(p.getAddress().isSiteLocalAddress()&&Arrays.equals(Arrays.copyOf(b,p.getLength()),expected))socket.send(new DatagramPacket(answer,answer.length,p.getSocketAddress()));}catch(Exception e){break;}}});}
 private static JsonObject findInvite(String id){return rows("invites").stream().filter(i->text(i,"id").equals(id)&&text(i,"receiver").equals(self())&&i.get("expires").getAsLong()>System.currentTimeMillis()).findFirst().orElse(null);}
 public static void join(String id){if(probing)return;JsonObject invite=findInvite(id);if(invite==null){message="Invitation expired";return;}if(!verifiedAccount()){message="Sign in with Microsoft or Unbox before joining";return;}try{var mc=Minecraft.getInstance();var r=invite.getAsJsonObject("room");if(r.has("protocol")&&r.get("protocol").getAsInt()==2){EnvironmentSync.prepare(invite);SocialLogin.prepareJoin(invite,mc.getUser().getProfileId(),mc.getUser().getName());}else if(!text(view.getAsJsonObject("self"),"kind").equals("microsoft")||mc.getUser().getAccessToken().length()<20)throw new IllegalArgumentException();}catch(Exception e){message="Ask your friend for a new invitation from the latest Unbox";return;}JsonObject r=invite.getAsJsonObject("room");pendingJoin=id;joinStarted=System.currentTimeMillis();probing=true;message="Checking local network…";
  Thread.ofVirtual().name("unbox-lan-probe").start(()->{String address=probe(r);probing=false;if(!pendingJoin.equals(id))return;if(address!=null){pendingJoin="";Minecraft.getInstance().execute(()->connect(address));}else{message="Connecting over the internet…";command("relay",id);}});
 }
 public static String probe(JsonObject r){
  try(var socket=new DatagramSocket()){socket.setSoTimeout(350);String token=text(r,"probeToken");if(!token.matches("[a-f0-9]{64}"))return null;byte[] q=("UNBOX?"+token).getBytes(StandardCharsets.US_ASCII),expected=("UNBOX!"+token).getBytes(StandardCharsets.US_ASCII);List<InetAddress> addresses=new ArrayList<>();
   for(var ip:r.getAsJsonArray("lan")){String host=ip.getAsString();if(!host.matches("\\d{1,3}(\\.\\d{1,3}){3}"))continue;InetAddress a=InetAddress.getByName(host);if(a instanceof Inet4Address&&a.isSiteLocalAddress()&&sameSubnet(a))addresses.add(a);}
   int port=r.get("probePort").getAsInt();for(int attempt=0;attempt<3;attempt++){for(var a:addresses)socket.send(new DatagramPacket(q,q.length,a,port));long until=System.currentTimeMillis()+400;while(System.currentTimeMillis()<until&&!addresses.isEmpty()){try{var p=new DatagramPacket(new byte[128],128);socket.receive(p);if(addresses.contains(p.getAddress())&&p.getPort()==port&&Arrays.equals(Arrays.copyOf(p.getData(),p.getLength()),expected))return p.getAddress().getHostAddress()+":"+r.get("port").getAsInt();}catch(SocketTimeoutException timeout){break;}}}
  }catch(Exception e){return null;}return null;
 }
 private static boolean sameSubnet(InetAddress remote)throws Exception{int r=java.nio.ByteBuffer.wrap(remote.getAddress()).getInt();for(var ni:Collections.list(NetworkInterface.getNetworkInterfaces()))if(ni.isUp())for(var a:ni.getInterfaceAddresses())if(a.getAddress() instanceof Inet4Address){int bits=a.getNetworkPrefixLength();if(bits<1||bits>32)continue;int mask=(int)(0xffffffffL<<(32-bits));if((java.nio.ByteBuffer.wrap(a.getAddress().getAddress()).getInt()&mask)==(r&mask))return true;}return false;}
 private static void connect(String address){if(!address.matches("(?:[a-z0-9.-]+\\.e4mc\\.link|\\d{1,3}(?:\\.\\d{1,3}){3}:\\d{1,5})")){message="Invalid connection address";return;}var mc=Minecraft.getInstance();message="Joining world…";ConnectScreen.startConnecting(new UnboxHomeScreen(false),mc,ServerAddress.parseString(address),new ServerData("Friend's world",address,ServerData.Type.OTHER),false,null);}
 private static void startRelay(){if(room==null||relayFailed)return;if(relayStarted>0){try{var session=link.e4mc.E4mcClient.session;if(session==null||session.state==link.e4mc.QuiclimeSession.State.STOPPED||session.failureCause!=null||(relay.isEmpty()&&System.currentTimeMillis()-relayStarted>60000)){relay="";relayFailed=true;message="Relay unavailable. Retry relay.";}}catch(LinkageError e){relayFailed=true;}return;}if(handler==null||group==null){message="Open the world again to enable internet sharing";return;}try{relayStarted=System.currentTimeMillis();message="Starting free relay…";link.e4mc.E4mcClient.session=new link.e4mc.QuiclimeSession(handler,group);link.e4mc.E4mcClient.session.startAsync();}catch(Throwable e){message="Relay failed to start";relayStarted=0;relayFailed=true;}}
 private static void kickUninvited(){var server=Minecraft.getInstance().getSingleplayerServer();if(server!=null)server.execute(()->{for(var player:new ArrayList<>(server.getPlayerList().getPlayers()))if(!allowed(player.getUUID()))player.connection.disconnect(net.minecraft.network.chat.Component.literal("World sharing ended."));});}
 public static void stop(){SocialLogin.clearHost();guarded=false;owner=null;INVITED.clear();room=null;relay="";relayStarted=0;pendingJoin="";if(discovery!=null){discovery.close();discovery=null;}handler=null;group=null;try{if(link.e4mc.E4mcClient.session!=null)link.e4mc.E4mcClient.session.stop();}catch(LinkageError ignored){}message="";}
}
