package dev.unbox.unpvp;
import com.google.gson.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentText;
/** Modern badge identity/equipment rules, with a private-world capability roster for 1.8.9. */
public final class NameBadges {
 public static final String CHANNEL="UNBOX|BADGES";
 private static Set<UUID> roster=Collections.emptySet(),cloud=Collections.emptySet();private static Object connection;private static int ticks;private static boolean scoped;
 public static int glyphs;public static final String[] LOGOS={"badge","unbox-creator-crown","unbox-fluttering-cash"};
 public static boolean currentView(){JsonObject v=SocialBridge.view;Minecraft mc=Minecraft.getMinecraft();try{return SocialBridge.flag(v,"connected")&&System.currentTimeMillis()-v.get("updated").getAsLong()<60000&&SocialBridge.text(v.getAsJsonObject("self"),"uuid").replace("-","").equalsIgnoreCase(mc.getSession().getProfile().getId().toString().replace("-",""));}catch(Exception e){return false;}}
 public static boolean eligible(){return currentView()&&"microsoft".equals(SocialBridge.text(SocialBridge.view.getAsJsonObject("self"),"kind"));}
 public static String bundled(JsonObject value){String id=SocialBridge.text(value,"id");return id.equals(LOGOS[1])||id.equals(LOGOS[2])?id:LOGOS[0];}
 public static String logo(UUID id){if(currentView())try{JsonObject all=SocialBridge.view.getAsJsonObject("playerLogos");JsonElement value=all.get(id.toString().replace("-",""));if(value!=null&&value.isJsonObject())return bundled(value.getAsJsonObject());}catch(Exception ignored){}return LOGOS[0];}
 public static boolean known(UUID id){Minecraft mc=Minecraft.getMinecraft();if(mc.thePlayer!=null&&mc.thePlayer.getUniqueID().equals(id))return true;return connection==mc.getNetHandler()&&(roster.contains(id)||currentView()&&cloud.contains(id));}
 public static void begin(EntityLivingBase e){scoped=e instanceof net.minecraft.entity.player.EntityPlayer&&known(e.getUniqueID());}public static void end(){scoped=false;}public static boolean fontScope(){return scoped;}
 public static int glyph(char c){return fontScope()&&c>=0xe000&&c<=0xe002?c-0xe000:-1;}
 public static IChatComponent prefix(EntityLivingBase e,IChatComponent name){if(!(e instanceof net.minecraft.entity.player.EntityPlayer)||!known(e.getUniqueID()))return name;int index=Arrays.asList(LOGOS).indexOf(logo(e.getUniqueID()));return new ChatComponentText("\u00a7r\u00a7f"+(char)(0xe000+Math.max(0,index))+" \u00a7r"+name.getFormattedText());}
 public static boolean ownVisible(EntityLivingBase e){Minecraft mc=Minecraft.getMinecraft();return (mc.getRenderViewEntity()!=e||mc.gameSettings.thirdPersonView!=0)&&mc.currentScreen==null&&!mc.gameSettings.hideGUI&&e.isEntityAlive()&&!e.isInvisible()&&!((net.minecraft.entity.player.EntityPlayer)e).isSpectator();}
 public static void receive(PacketBuffer buffer){try{int n=buffer.readVarIntFromBuffer();if(n<0||n>2048||buffer.readableBytes()!=n*16)return;Set<UUID> ids=new HashSet<>();for(int i=0;i<n;i++)ids.add(new UUID(buffer.readLong(),buffer.readLong()));connection=Minecraft.getMinecraft().getNetHandler();roster=ids;}catch(Exception ignored){}}
 static void tick(){Minecraft mc=Minecraft.getMinecraft();if(connection!=mc.getNetHandler()){connection=mc.getNetHandler();roster=Collections.emptySet();cloud=Collections.emptySet();}if(++ticks%20!=0)return;Set<UUID> found=new HashSet<>();if(currentView())try{for(JsonElement id:SocialBridge.view.getAsJsonArray("badges"))found.add(SocialBridge.uuid(id.getAsString()));}catch(Exception ignored){}cloud=found;
  final net.minecraft.server.integrated.IntegratedServer server=mc.getIntegratedServer();if(server==null||!SocialBridge.guarded()||ticks%100!=0)return;server.addScheduledTask(()->{List<EntityPlayerMP> players=new ArrayList<>(server.getConfigurationManager().getPlayerList());List<UUID> ids=new ArrayList<>();for(EntityPlayerMP p:players)if(SocialBridge.allowed(p.getUniqueID())&&ids.size()<2048)ids.add(p.getUniqueID());for(EntityPlayerMP p:players)if(ids.contains(p.getUniqueID())){PacketBuffer b=new PacketBuffer(io.netty.buffer.Unpooled.buffer());b.writeVarIntToBuffer(ids.size());for(UUID id:ids){b.writeLong(id.getMostSignificantBits());b.writeLong(id.getLeastSignificantBits());}p.playerNetServerHandler.sendPacket(new S3FPacketCustomPayload(CHANNEL,b));}});
 }
}
