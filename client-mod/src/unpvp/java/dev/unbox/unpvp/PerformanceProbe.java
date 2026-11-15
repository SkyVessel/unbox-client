package dev.unbox.unpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ScreenShotHelper;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.WorldType;
import net.minecraft.world.WorldServer;
import java.io.*;
import java.util.*;

/** Opt-in isolated benchmark. Never runs for normal launches. */
final class PerformanceProbe {
    private static boolean launched,fixtureRequested;private static volatile boolean fixtureReady;
    private static int scene;private static long since,last;private static final List<Long> samples=new ArrayList<>();
    static void tick(){
        Minecraft mc=Minecraft.getMinecraft();
        if(!launched&&mc.currentScreen instanceof UiScreen){launched=true;mc.launchIntegratedServer("PerformanceLab","Unbox Performance Lab",new WorldSettings(42,GameType.CREATIVE,false,false,WorldType.DEFAULT));return;}
        if(mc.thePlayer==null||mc.theWorld==null)return;
        if(!fixtureRequested){fixtureRequested=true;mc.getIntegratedServer().addScheduledTask(()->{
            WorldServer w=mc.getIntegratedServer().worldServerForDimension(0);w.getGameRules().setOrCreateGameRule("doMobSpawning","false");w.getGameRules().setOrCreateGameRule("doDaylightCycle","false");w.setWorldTime(6000);
            for(int x=-18;x<=18;x++)for(int z=-18;z<=18;z++)w.setBlockState(new BlockPos(x,100,z),Blocks.stone.getDefaultState(),2);
            for(int x=-12;x<=12;x++)for(int y=101;y<=108;y++)w.setBlockState(new BlockPos(x,y,0),Blocks.stonebrick.getDefaultState(),2);
            for(int x=-10;x<=10;x+=2)for(int z=2;z<=14;z+=2){w.setBlockState(new BlockPos(x,101,z),Blocks.chest.getDefaultState(),2);EntityArmorStand e=new EntityArmorStand(w,x+.5,102,z+.5);w.spawnEntityInWorld(e);}
            mc.getIntegratedServer().getConfigurationManager().getPlayerByUsername("UIPreview").setPositionAndUpdate(0,105,-12);fixtureReady=true;
        });return;}
        if(!fixtureReady)return;
        if(mc.currentScreen!=null&&!(mc.currentScreen instanceof UiScreen))mc.displayGuiScreen(null);
        mc.gameSettings.pauseOnLostFocus=false;mc.gameSettings.limitFramerate=260;mc.gameSettings.enableVsync=false;
        if(since==0){since=System.nanoTime();mc.displayGuiScreen(null);}
        mc.setIngameNotInFocus();mc.thePlayer.rotationYaw=mc.thePlayer.prevRotationYaw=scene==0?180:0;mc.thePlayer.rotationPitch=mc.thePlayer.prevRotationPitch=scene==0?18:4;
    }
    static void frame(){
        if(since==0)return;long now=System.nanoTime(),elapsed=now-since;
        if(last!=0&&elapsed>12_000_000_000L)samples.add(now-last);last=now;
        if(elapsed<24_000_000_000L)return;
        Minecraft mc=Minecraft.getMinecraft();String name=scene==0?"terrain":scene==1?"occluded-entities":"mods-ui";
        try{
            File dir=new File(mc.mcDataDir,"performance-probe");dir.mkdirs();ScreenShotHelper.saveScreenshot(dir,name+".png",mc.displayWidth,mc.displayHeight,mc.getFramebuffer());
            long total=0;for(long n:samples)total+=n;Collections.sort(samples);int n=samples.size();
            try(PrintWriter out=new PrintWriter(new FileWriter(new File(dir,"frames.csv"),true))){out.printf(Locale.ROOT,"%s,%d,%.3f,%.3f,%.3f,%.3f%n",name,n,n*1e9/total,samples.get(n/2)/1e6,samples.get((int)(n*.95))/1e6,samples.get((int)(n*.99))/1e6);}
            System.out.println("[unpvp-benchmark] "+name+" frames="+n+" fps="+(n*1e9/total));
        }catch(Exception e){e.printStackTrace();mc.shutdown();return;}
        samples.clear();last=0;since=now;scene++;
        if(scene==2){try{java.lang.reflect.Constructor<?> c=Class.forName("dev.unbox.unpvp.VectorModsScreen").getDeclaredConstructor(net.minecraft.client.gui.GuiScreen.class);c.setAccessible(true);mc.displayGuiScreen((net.minecraft.client.gui.GuiScreen)c.newInstance(new HomeScreen(true)));}catch(ClassNotFoundException e){mc.displayGuiScreen(new ModsScreen(new HomeScreen(true)));}catch(Exception e){throw new IllegalStateException(e);}}
        if(scene==3){System.out.println("[unpvp-benchmark] PASS");mc.shutdown();since=0;}
    }
}
