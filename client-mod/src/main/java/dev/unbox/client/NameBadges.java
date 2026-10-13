package dev.unbox.client;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.*;
import net.minecraft.server.MinecraftServer;
/** Optional, server-observed capability. Vanilla players never receive a badge by name alone. */
public final class NameBadges {
 public record Packet(List<UUID> players) implements CustomPacketPayload {
  public static final Type<Packet> TYPE=new Type<>(Identifier.fromNamespaceAndPath("unbox","name_badges"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Packet> CODEC=CustomPacketPayload.codec((p,b)->{b.writeVarInt(p.players.size());for(var id:p.players)b.writeUUID(id);},b->{int n=b.readVarInt();if(n<0||n>256)throw new IllegalArgumentException("Invalid badge roster");var ids=new ArrayList<UUID>();for(int i=0;i<n;i++)ids.add(b.readUUID());return new Packet(ids);});
  public Type<Packet> type(){return TYPE;}
 }
 private static Set<UUID> players=Set.of();private static Object connection;private static List<UUID> sent=List.of();private static MinecraftServer server;private static int ticks;
 public static void receive(Packet packet){connection=Minecraft.getInstance().getConnection();players=Set.copyOf(packet.players());}
 public static boolean known(UUID id){return connection==Minecraft.getInstance().getConnection()&&players.contains(id);}
 public static Component prefix(Component name){return Component.empty().append(Component.literal("\ue000").withStyle(Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("unbox","name_badge"))).withColor(0xffffff))).append(Component.literal(" ")).append(name);}
 public static void tick(){var mc=Minecraft.getInstance();if(connection!=mc.getConnection()){connection=mc.getConnection();players=Set.of();}if(++ticks%20!=0)return;var s=mc.getSingleplayerServer();if(s!=server){server=s;sent=List.of();}if(s==null)return;
  s.execute(()->{var roster=s.getPlayerList().getPlayers().stream().filter(MapTransport::badgeCapable).map(p->p.getUUID()).limit(256).sorted().toList();if(roster.equals(sent))return;sent=roster;var packet=new Packet(roster);for(var p:s.getPlayerList().getPlayers())if(MapTransport.badgeCapable(p))MapTransport.sendBadge(p,packet);});
 }
}
