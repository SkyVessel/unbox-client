package dev.unbox.unpvp;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.*;
import net.minecraft.util.ChatComponentText;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;

/** Local TCP fixture using the real vanilla connection factory and play handler. No public login. */
final class ConnectionProbe {
    static void run()throws Exception{
        Minecraft mc=Minecraft.getMinecraft();CountDownLatch ready=new CountDownLatch(1),done=new CountDownLatch(1);long[] roundtrip={0};Throwable[] error={null};
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){
            Thread server=new Thread(()->{try(Socket peer=listener.accept()){
                peer.setSoTimeout(3000);peer.setTcpNoDelay(true);if(!ready.await(3,TimeUnit.SECONDS))throw new IOException("Client not ready");
                // Uncompressed PLAY KeepAlive, key 1234. The fixture performs no authentication.
                long begin=System.nanoTime();peer.getOutputStream().write(new byte[]{3,0,(byte)0xd2,9});peer.getOutputStream().flush();
                InputStream in=peer.getInputStream();int size=varint(in);if(size!=3||varint(in)!=0||varint(in)!=1234)throw new IOException("Unexpected keepalive response");roundtrip[0]=System.nanoTime()-begin;
            }catch(Throwable e){error[0]=e;}finally{done.countDown();}},"Unbox loopback peer");server.setDaemon(true);server.start();
            NetworkManager connection=NetworkManager.createNetworkManagerAndConnect(InetAddress.getByName("127.0.0.1"),listener.getLocalPort(),false);
            try{
                long activeDeadline=System.nanoTime()+2_000_000_000L;while(connection.hasNoChannel()&&System.nanoTime()<activeDeadline)Thread.sleep(5);
                connection.setNetHandler(new NetHandlerPlayClient(mc,null,connection,mc.getSession().getProfile()));connection.setConnectionState(EnumConnectionState.PLAY);
                Channel channel=(Channel)net.minecraftforge.fml.relauncher.ReflectionHelper.findField(NetworkManager.class,"channel","field_150746_k").get(connection);
                CombatProbe.check(Boolean.TRUE.equals(channel.config().getOption(ChannelOption.TCP_NODELAY)),"Real vanilla TCP factory enables TCP_NODELAY");
                CombatProbe.check(((InetSocketAddress)channel.remoteAddress()).getPort()==listener.getLocalPort()&&channel.pipeline().names().stream().noneMatch(n->n.toLowerCase().contains("proxy")),"TCP connection reaches requested peer without proxy handler");
                UiState.set("performance.connectionDiagnostics",true);ConnectionDiagnostics.tick();
                java.lang.reflect.Field n=ConnectionDiagnostics.class.getDeclaredField("network");n.setAccessible(true);n.set(null,connection);
                java.lang.reflect.Method describe=ConnectionDiagnostics.class.getDeclaredMethod("describe",NetworkManager.class);describe.setAccessible(true);java.lang.reflect.Field c=ConnectionDiagnostics.class.getDeclaredField("connection");c.setAccessible(true);c.set(null,describe.invoke(null,connection));
                ready.countDown();Thread.sleep(300); // Deliberately stall only the game thread.
                CombatProbe.check(done.getCount()==0&&error[0]==null,"KeepAlive replies over TCP while the game thread is deliberately stalled");
                System.out.println("[unpvp-network-probe] loopbackKeepaliveRoundtripMs="+roundtrip[0]/1e6+" mainThreadStallMs=300");
                ConnectionDiagnostics.save();
                ConnectionDiagnostics.tick();
                net.minecraft.network.play.server.S12PacketEntityVelocity velocity=new net.minecraft.network.play.server.S12PacketEntityVelocity(mc.thePlayer.getEntityId(),0,0,0);
                ConnectionDiagnostics.received(mc.getNetHandler().getNetworkManager(),velocity);Thread.sleep(20);
                double vx=mc.thePlayer.motionX,vy=mc.thePlayer.motionY,vz=mc.thePlayer.motionZ;mc.getNetHandler().handleEntityVelocity(velocity);mc.thePlayer.motionX=vx;mc.thePlayer.motionY=vy;mc.thePlayer.motionZ=vz;
                java.lang.reflect.Field q=ConnectionDiagnostics.class.getDeclaredField("queues");q.setAccessible(true);java.util.List<?> queues=(java.util.List<?>)q.get(null);
                CombatProbe.check(queues.size()==1&&((Double)queues.get(0))>=20,"Entity handler timing records an injected 20 ms pre-handler delay");
            }finally{UiState.set("performance.connectionDiagnostics",false);ConnectionDiagnostics.tick();connection.closeChannel(new ChatComponentText("Local timing test complete"));}
            server.join(3500);if(error[0]!=null)throw new IllegalStateException(error[0]);
        }
    }
    private static int varint(InputStream in)throws IOException{int value=0;for(int shift=0;shift<35;shift+=7){int b=in.read();if(b<0)throw new EOFException();value|=(b&127)<<shift;if((b&128)==0)return value;}throw new IOException("Varint too long");}
}
