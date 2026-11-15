package dev.unbox.unpvp;

import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;

/** Adapts VoxLink to Unbox's existing private invitation mailbox, without changing its UI. */
final class VoxLinkNetwork {
 static final String VERSION="voxlink-v1";
 private static final Map<String,VoxLinkTransport.Session> hosts=new ConcurrentHashMap<>();
 private static final Map<String,String> attempts=new ConcurrentHashMap<>();
 private static final Map<String,JsonObject> offers=new ConcurrentHashMap<>();
 private static volatile VoxLinkTransport.Session guest;
 static JsonArray signals(){JsonArray array=new JsonArray();offers.values().stream().limit(16).forEach(v->array.add(SocialBridge.copy(v)));return array;}
 private static void publish(String id,String attempt,boolean host,JsonObject offer){JsonObject signal=new JsonObject();signal.addProperty("id",id);signal.addProperty("attempt",attempt);signal.addProperty("side",host?"host":"guest");signal.add("offer",offer);offers.compute(id,(ignored,previous)->{VoxLinkTransport.Session session=host?hosts.get(id):guest;return session!=null&&session.invite.equals(id)&&session.attempt.equals(attempt)&&session.negotiating()?signal:previous;});}
 private static JsonObject remote(String id,String attempt,String side){for(JsonObject invite:SocialBridge.rows("invites"))if(SocialBridge.text(invite,"id").equals(id)){
  JsonObject room=invite.getAsJsonObject("room");if(room.has("transport")&&room.get("transport").isJsonObject()){JsonObject t=room.getAsJsonObject("transport");if(SocialBridge.text(t,"attempt").equals(attempt)&&t.has(side)&&t.get(side).isJsonObject())return SocialBridge.copy(t.getAsJsonObject(side));}
 }return null;}
 static CompletableFuture<VoxLinkTransport.Route> join(JsonObject invite){cancelJoin();JsonObject room=invite.getAsJsonObject("room");if(!VERSION.equals(SocialBridge.text(room,"network")))return CompletableFuture.completedFuture(null);
  String id=SocialBridge.text(invite,"id"),attempt=UUID.randomUUID().toString();
  VoxLinkTransport.Session session=session(id,attempt,SocialBridge.text(invite,"join_secret"),false,0,()->remote(id,attempt,"host"),offer->publish(id,attempt,false,offer),event->{VoxLinkTransport.Session current=guest;if(current!=null&&current.attempt.equals(attempt))SocialBridge.message="Trying a direct connection…";});
  guest=session;session.start();return session.result;
 }
 private static VoxLinkTransport.Session session(String id,String attempt,String secret,boolean host,int port,java.util.function.Supplier<JsonObject> remote,java.util.function.Consumer<JsonObject> publish,java.util.function.Consumer<String> diagnostic){
  // Existing isolated game fixtures may exercise the entire bridge without depending on the test machine's NAT.
  if(Boolean.getBoolean("unbox.voxlinkFixture")&&!System.getProperty("unpvp.test.social","").isEmpty())return new VoxLinkTransport.Session(id,attempt,secret,host,port,remote,publish,diagnostic,true,Collections.emptyList());
  return new VoxLinkTransport.Session(id,attempt,secret,host,port,remote,publish,diagnostic);
 }
 static void tick(JsonObject ownRoom){
  hosts.entrySet().removeIf(e->{if(e.getValue().idle()){e.getValue().close();offers.remove(e.getKey());return true;}return false;});
  VoxLinkTransport.Session current=guest;if(current!=null&&current.idle()){guest=null;current.close();offers.remove(current.invite);}
  if(ownRoom==null)return;
  JsonObject secrets=PrivateLogin.secrets();long now=System.currentTimeMillis();Set<String> live=new HashSet<>();
  for(JsonObject invite:SocialBridge.rows("invites"))try{
   if(!SocialBridge.text(invite,"sender").equals(SocialBridge.text(SocialBridge.view.getAsJsonObject("self"),"id"))||invite.get("expires").getAsLong()<=now)continue;
   String id=SocialBridge.text(invite,"id"),person=SocialBridge.text(invite,"receiver");JsonObject room=invite.getAsJsonObject("room");
   if(!SocialBridge.text(room,"id").equals(SocialBridge.text(ownRoom,"id"))||!secrets.has(person))continue;live.add(id);
   if(!room.has("transport")||!room.get("transport").isJsonObject())continue;JsonObject transport=room.getAsJsonObject("transport");
   if(!transport.has("guest")||transport.get("created").getAsLong()<now-40000)continue;String attempt=SocialBridge.text(transport,"attempt");UUID.fromString(attempt);
   VoxLinkTransport.Session old=hosts.get(id);if(attempt.equals(attempts.get(id)))continue;if(old!=null)old.close();if(hosts.size()>=15&&!hosts.containsKey(id))continue;
   VoxLinkTransport.Session session=session(id,attempt,secrets.get(person).getAsString(),true,ownRoom.get("port").getAsInt(),()->remote(id,attempt,"guest"),offer->publish(id,attempt,true,offer),event->{});hosts.put(id,session);attempts.put(id,attempt);session.start();
  }catch(RuntimeException ignored){}
  // Expiry stops negotiations; already authenticated players may finish the existing session.
  hosts.forEach((id,s)->{if(!live.contains(id)){s.retire();offers.remove(id);}});
 }
 static void cancelJoin(){VoxLinkTransport.Session s=guest;guest=null;if(s!=null){s.close();offers.remove(s.invite);}}
 static void stopHosting(){hosts.values().forEach(VoxLinkTransport.Session::close);hosts.keySet().forEach(offers::remove);hosts.clear();attempts.clear();}
}
