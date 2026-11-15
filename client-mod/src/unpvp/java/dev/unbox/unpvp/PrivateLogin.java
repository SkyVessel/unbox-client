package dev.unbox.unpvp;

import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.login.client.*;
import net.minecraft.network.login.server.S01PacketEncryptionRequest;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.NetHandlerLoginServer;
import net.minecraft.util.CryptManager;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Invitation-bound mutual proof over vanilla RSA/AES login. Public server authentication is untouched. */
public final class PrivateLogin {
 public static final class Grant {
  final String person,name,secret,room;final UUID uuid;final long expires;
  Grant(String p,UUID u,String n,String s,String r,long e){person=p;uuid=u;name=n;secret=s;room=r;expires=e;}
 }
 private static final class Pending {final Grant grant;final String nonce,key;Pending(Grant g,String n,String k){grant=g;nonce=n;key=k;}}
 private static final Map<String,Grant> GRANTS=new ConcurrentHashMap<>();
 private static final Map<NetworkManager,Integer> CLAIMS=Collections.synchronizedMap(new WeakHashMap<NetworkManager,Integer>());
 private static final Map<NetHandlerLoginServer,Pending> PENDING=Collections.synchronizedMap(new WeakHashMap<NetHandlerLoginServer,Pending>());
 private static volatile Grant joining;
 private static final Map<NetworkManager,Grant> CLIENTS=Collections.synchronizedMap(new WeakHashMap<NetworkManager,Grant>());
 public static void track(NetworkManager n){Grant g=joining;if(g!=null)CLIENTS.put(n,g);}
 private static final Set<NetworkManager> VERIFIED=Collections.newSetFromMap(new WeakHashMap<NetworkManager,Boolean>());
 private static final SecureRandom RANDOM=new SecureRandom();
 private static final java.lang.reflect.Field STATE=net.minecraftforge.fml.relauncher.ReflectionHelper.findField(NetHandlerLoginServer.class,"currentLoginState","field_147328_g");
 public static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
 private static byte[] unhex(String s){if(s==null||!s.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Invalid invitation");byte[] b=new byte[32];for(int i=0;i<32;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;}
 public static byte[] proof(String secret,String side,String room,String person,String nonce,String key)throws Exception{Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(unhex(secret),"HmacSHA256"));return m.doFinal(("unbox-189-v2|"+side+"|"+room+"|"+person+"|"+nonce+"|"+key).getBytes(StandardCharsets.UTF_8));}
 private static String hash(PublicKey k)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(k.getEncoded()));}
 public static String grant(String person,UUID uuid,String name,String room){if(!person.matches("[a-f0-9-]{36}")||!name.matches("[A-Za-z0-9_]{1,16}"))throw new IllegalArgumentException();byte[] secret=new byte[32];boolean collision;do{RANDOM.nextBytes(secret);int hint=hint(hex(secret));collision=false;for(Grant existing:GRANTS.values())if(hint(existing.secret)==hint){collision=true;break;}}while(collision);Grant g=new Grant(person,uuid,name,hex(secret),room,System.currentTimeMillis()+300000);GRANTS.put(person,g);return g.secret;}
 public static JsonObject secrets(){JsonObject o=new JsonObject();for(Grant g:GRANTS.values())o.addProperty(g.person,g.secret);return o;}
 public static void retain(Set<UUID> ids){GRANTS.values().removeIf(g->!ids.contains(g.uuid));}
 public static void clearHost(){GRANTS.clear();PENDING.clear();CLAIMS.clear();}
 public static void cancelJoin(){joining=null;synchronized(CLIENTS){for(NetworkManager n:CLIENTS.keySet())n.closeChannel(new net.minecraft.util.ChatComponentText("Join cancelled"));CLIENTS.clear();}synchronized(VERIFIED){VERIFIED.clear();}}
 public static void prepare(JsonObject invite){JsonObject r=invite.getAsJsonObject("room");String secret=SocialBridge.text(invite,"join_secret");unhex(secret);if(!"forge".equals(SocialBridge.text(r,"loader"))||!"1.8.9".equals(SocialBridge.text(r,"minecraft"))||r.get("protocol").getAsInt()!=2)throw new IllegalArgumentException("Incompatible invitation");Minecraft mc=Minecraft.getMinecraft();joining=new Grant(SocialBridge.text(invite,"receiver"),mc.getSession().getProfile().getId(),mc.getSession().getUsername(),secret,SocialBridge.text(r,"id"),invite.get("expires").getAsLong());}
 // 1.8 has no login query packet. A 16-bit routing hint uses the advisory handshake port;
 // the socket destination and hostname are unchanged (required by e4mc). This is NOT authorization.
 // The host still requires the invitation-bound mutual HMAC and encrypted stream below.
 private static int hint(String secret){try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(unhex(secret));return ((digest[0]&255)<<8)|(digest[1]&255);}catch(Exception e){throw new IllegalArgumentException(e);}}
 public static int portClaim(int actualPort){Grant g=joining;return g!=null&&g.expires>System.currentTimeMillis()?hint(g.secret):actualPort;}
 public static void claim(NetworkManager n,int hint){if(SocialBridge.guarded())CLAIMS.put(n,hint);}

 private static void state(NetHandlerLoginServer h,String name)throws Exception{for(Object v:STATE.getType().getEnumConstants())if(((Enum<?>)v).name().equals(name)){STATE.set(h,v);return;}throw new IllegalStateException();}
 public static boolean begin(NetHandlerLoginServer h,MinecraftServer server,C00PacketLoginStart packet){if(!SocialBridge.guarded()||h.networkManager.isLocalChannel())return false;
  try{Integer tag=CLAIMS.remove(h.networkManager);Grant g=null;if(tag!=null)for(Grant candidate:GRANTS.values())if(hint(candidate.secret)==tag){g=candidate;break;}String name=packet.getProfile().getName();if(g==null||g.expires<=System.currentTimeMillis()||!SocialBridge.allowed(g.uuid)||!name.matches("[A-Za-z0-9_]{1,16}")||(!g.name.equals("OfflinePlayer")&&!g.name.equals(name))||name.equalsIgnoreCase(server.getServerOwner()))throw new IllegalArgumentException();
   byte[] nonce=new byte[16];RANDOM.nextBytes(nonce);String key=hash(server.getKeyPair().getPublic());Pending q=new Pending(new Grant(g.person,g.uuid,name,g.secret,g.room,g.expires),hex(nonce),key);PENDING.put(h,q);state(h,"KEY");byte[] token=new byte[48];System.arraycopy(nonce,0,token,0,16);System.arraycopy(proof(g.secret,"host",g.room,g.person,q.nonce,key),0,token,16,32);h.networkManager.sendPacket(new S01PacketEncryptionRequest("UNBOX2",server.getKeyPair().getPublic(),token));
  }catch(Exception e){if(!System.getProperty("unpvp.test.social","").isEmpty())e.printStackTrace();PENDING.remove(h);h.closeConnection("A valid Unbox invitation is required. Use a different name from the host.");}return true;
 }
 public static GameProfile finish(NetHandlerLoginServer h,MinecraftServer server,C01PacketEncryptionResponse packet){Pending q=PENDING.remove(h);try{Grant live=q==null?null:GRANTS.get(q.grant.person);if(q==null||live==null||!live.secret.equals(q.grant.secret)||q.grant.expires<=System.currentTimeMillis()||!SocialBridge.allowed(q.grant.uuid)||!MessageDigest.isEqual(packet.getVerifyToken(server.getKeyPair().getPrivate()),proof(q.grant.secret,"guest",q.grant.room,q.grant.person,q.nonce,q.key)))throw new IllegalArgumentException();SecretKey secret=packet.getSecretKey(server.getKeyPair().getPrivate());if(secret==null||secret.getEncoded().length!=16)throw new IllegalArgumentException();h.networkManager.enableEncryption(secret);state(h,"READY_TO_ACCEPT");return new GameProfile(q.grant.uuid,q.grant.name);}catch(Exception e){h.closeConnection("Invalid or expired Unbox invitation.");return null;}}
 public static boolean acceptClient(NetworkManager connection){boolean expected=CLIENTS.remove(connection)!=null;if(!expected&&!SocialBridge.joining())return true;synchronized(VERIFIED){if(SocialBridge.joining()&&VERIFIED.remove(connection)&&connection.getIsencrypted())return true;}connection.closeChannel(new net.minecraft.util.ChatComponentText("Could not verify your friend's private world."));return false;}
 public static boolean client(NetworkManager connection,S01PacketEncryptionRequest packet){Grant g=CLIENTS.get(connection);if(g==null)return false;
  try{byte[] token=packet.getVerifyToken();if(g.expires<=System.currentTimeMillis()||!packet.getServerId().equals("UNBOX2")||token.length!=48)throw new IllegalArgumentException();String nonce=hex(Arrays.copyOf(token,16)),key=hash(packet.getPublicKey());if(!MessageDigest.isEqual(Arrays.copyOfRange(token,16,48),proof(g.secret,"host",g.room,g.person,nonce,key)))throw new IllegalArgumentException();final SecretKey shared=CryptManager.createNewSharedKey();connection.sendPacket(new C01PacketEncryptionResponse(shared,packet.getPublicKey(),proof(g.secret,"guest",g.room,g.person,nonce,key)),future->{if(future.isSuccess()){connection.enableEncryption(shared);synchronized(VERIFIED){VERIFIED.add(connection);}}});joining=null;
  }catch(Exception e){joining=null;connection.closeChannel(new net.minecraft.util.ChatComponentText("Could not verify your friend's private world. Request a new invitation."));}return true;
 }
}
