package dev.unbox.unpvp;

import java.io.*;
import java.lang.management.ManagementFactory;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.BlockPos;
import net.minecraft.world.*;
import org.lwjgl.opengl.GL11;

/** Opt-in load benchmark: new terrain, orderly save, then reopen the same isolated world. */
final class LoadProbe {
    private static int stage,ticks;private static boolean menu;private static long since;
    private static IntegratedServer closing;private static BlockPos marker;private static volatile boolean written;
    static void frame(){
        if(!menu&&Minecraft.getMinecraft().currentScreen instanceof UiScreen){menu=true;record("menu",ManagementFactory.getRuntimeMXBean().getUptime());}
    }
    static void world(){
        Minecraft mc=Minecraft.getMinecraft();if(mc.thePlayer==null||!mc.theWorld.isBlockLoaded(mc.thePlayer.getPosition()))return;
        if(stage==1||stage==5){record(stage==1?"new-world-frame":"saved-world-frame",(System.nanoTime()-since)/1000000);stage++;ticks=0;}
    }
    static void tick(){
        Minecraft mc=Minecraft.getMinecraft();
        if(stage==0&&menu){stage=1;since=System.nanoTime();mc.launchIntegratedServer("LoadLab","Unbox Load Lab",new WorldSettings(42,WorldSettings.GameType.CREATIVE,false,false,WorldType.DEFAULT));return;}
        if(stage==2&&++ticks>=60){stage=3;marker=mc.thePlayer.getPosition().up(3);mc.getIntegratedServer().addScheduledTask(()->{mc.getIntegratedServer().worldServerForDimension(0).setBlockState(marker,Blocks.gold_block.getDefaultState(),3);written=true;});}
        if(stage==3&&written){stage=4;System.out.println("[unpvp-load-test] saving and leaving world");closing=mc.getIntegratedServer();mc.theWorld.sendQuittingDisconnectingPacket();}
        if(stage==4&&WorldLoading.hasTerminated(closing)){
            // Reproduce the race deliberately: shutdown requested again after the server stopped.
            closing.initiateShutdown();mc.loadWorld(null);mc.displayGuiScreen(new HomeScreen(false));
            stage=5;since=System.nanoTime();mc.launchIntegratedServer("LoadLab","Unbox Load Lab",null);return;
        }
        if(stage==6&&++ticks>=60){
            if(mc.theWorld.getBlockState(marker).getBlock()!=Blocks.gold_block)throw new IllegalStateException("Load test marker did not survive save/reopen");
            if(GL11.glGetError()!=0)throw new IllegalStateException("GL error after world reload");
            System.out.println("[unpvp-load-test] PASS saved block restored, world rendered twice");stage=7;mc.shutdown();
        }
    }
    private static void record(String label,long ms){
        System.out.println("[unpvp-load-test] "+label+"-ms="+ms);
        File dir=new File(Minecraft.getMinecraft().mcDataDir,"load-probe");dir.mkdirs();
        try(PrintWriter out=new PrintWriter(new FileWriter(new File(dir,"times.csv"),true))){out.println(label+","+ms);}catch(IOException e){throw new IllegalStateException(e);}
    }
}
