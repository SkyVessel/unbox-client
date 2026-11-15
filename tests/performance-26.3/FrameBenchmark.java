package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import java.nio.file.*;
import java.util.*;
import java.lang.management.ManagementFactory;

/** Only injected into disposable benchmark JARs. Never changes player position or rotation. */
public final class FrameBenchmark {
    static final boolean COMPAT=Boolean.getBoolean("unbox.compatOnly");
    static final int WARM=COMPAT?2:8,SAMPLE=COMPAT?2:15;
    static int phase=-1;static long entered,last,loadStart;static boolean done;
    static final List<Double> frames=new ArrayList<>();static final List<Object> reports=new ArrayList<>();
    static final Map<String,Object> output=new LinkedHashMap<>();
    static final String[] phases={"title","world","quick","mods","mods-scroll","settings"};
    static long now(){return System.nanoTime();}
    static double seconds(long start){return (now()-start)/1e9;}
    static void mapProof(Minecraft mc)throws Exception{
        var pos=new net.minecraft.core.BlockPos.MutableBlockPos();int x=mc.player.getBlockX()-16,z=mc.player.getBlockZ()-16;
        for(int i=0;i<2;i++)for(int dz=0;dz<32;dz++)for(int dx=0;dx<32;dx++)WorldMap.sample(mc,x+dx,z+dz,pos);
        long start=now();for(int i=0;i<10;i++)for(int dz=0;dz<32;dz++)for(int dx=0;dx<32;dx++)WorldMap.sample(mc,x+dx,z+dz,pos);
        output.put("map10240SamplesMs",(now()-start)/1e6);
        var digest=java.security.MessageDigest.getInstance("SHA-256");var bytes=java.nio.ByteBuffer.allocate(4096);
        for(int dz=0;dz<32;dz++)for(int dx=0;dx<32;dx++)bytes.putInt(WorldMap.terrain.color(x+dx,z+dz));
        output.put("mapColorSha256",java.util.HexFormat.of().formatHex(digest.digest(bytes.array())));
    }
    public static void frame(){
        if(!Boolean.getBoolean("unbox.fixedViewBench")||done)return;
        var mc=Minecraft.getInstance();
        try{
            mc.getFramerateLimitTracker().onInputReceived();
            if(mc.gui.overlay()!=null)return;
            if(phase==-1){
                if(!(mc.gui.screen() instanceof UnboxHomeScreen home)||!home.surfaceReady())return;
                output.put("startupToMenuMs",ManagementFactory.getRuntimeMXBean().getUptime());
                long clicked=Long.getLong("unbox.benchmarkClickedAt",0L);
                if(clicked>0)output.put("buttonToMenuMs",System.currentTimeMillis()-clicked);
                output.put("framebuffer",List.of(mc.getWindow().getWidth(),mc.getWindow().getHeight()));
                output.put("renderDistance",mc.options.renderDistance().get());output.put("configuredFpsLimit",mc.options.framerateLimit().get());output.put("vsync",mc.options.enableVsync().get());
                output.put("warmupSeconds",WARM);output.put("sampleSeconds",SAMPLE);output.put("compatibilityOnly",COMPAT);
                if(Boolean.getBoolean("unbox.startupOnly")){
                    done=true;Path dir=mc.gameDirectory.toPath().resolve("bench");Files.createDirectories(dir);
                    Files.writeString(dir.resolve("results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(output));mc.stop();return;
                }
                if(Boolean.getBoolean("unbox.createFixture")){
                    phase=-3;mc.createWorldOpenFlows().createFreshLevel("Benchmark",new LevelSettings("Unbox isolated performance fixture",GameType.CREATIVE,new LevelSettings.DifficultySettings(net.minecraft.world.Difficulty.PEACEFUL,false,false),true,WorldDataConfiguration.DEFAULT),new WorldOptions(20261112L,true,false),WorldPresets::createNormalWorldDimensions,home);return;
                }
                if(Boolean.getBoolean("unbox.worldOnly")){phase=-2;loadStart=now();mc.createWorldOpenFlows().openWorld("Benchmark",()->{throw new IllegalStateException("Fixture load cancelled");});return;}
                phase=0;entered=now();return;
            }
            if(phase==-3){if(mc.player==null||mc.gui.screen()!=null)return;if(entered==0)entered=now();if(seconds(entered)<35)return;done=true;Files.writeString(mc.gameDirectory.toPath().resolve("fixture-ready.txt"),"seed=20261112; fixed default spawn; no camera or movement input\n");mc.stop();return;}
            if(phase==-2){if(mc.player==null||mc.gui.screen()!=null)return;output.put("openWorldToPlayableMs",(now()-loadStart)/1e6);output.put("position",List.of(mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot()));phase=1;entered=now();return;}
            if(mc.gui.screen() instanceof SolidScreen s&&!s.surfaceReady()){last=0;return;}
            if(phase==4&&mc.gui.screen() instanceof VectorModsScreen mods){double destination=(Math.sin(seconds(entered)*.65)+1)*.5*Math.max(0,mods.cardPeriod()-mods.viewport.gridHeight());mods.mouseScrolled(mc.getWindow().getGuiScaledWidth()/2.,mc.getWindow().getGuiScaledHeight()/2.,0,(mods.scrollTarget()-destination)/112);}
            if(seconds(entered)<WARM){last=0;return;}
            long stamp=now();if(last!=0)frames.add((stamp-last)/1e6);last=stamp;
            if(seconds(entered)<WARM+SAMPLE)return;
            double sum=frames.stream().mapToDouble(d->d).sum();var sorted=new ArrayList<>(frames);sorted.sort(Double::compare);
            var report=new LinkedHashMap<String,Object>();report.put("phase",phases[phase]);report.put("frames",frames.size());report.put("fps",frames.size()*1000/sum);report.put("p95Ms",sorted.get((int)(sorted.size()*.95)));report.put("p99Ms",sorted.get((int)(sorted.size()*.99)));report.put("windowActive",mc.isWindowActive());report.put("effectiveFpsLimit",mc.getFramerateLimitTracker().getFramerateLimit());reports.add(report);
            Path dir=mc.gameDirectory.toPath().resolve("bench");Files.createDirectories(dir);Files.writeString(dir.resolve(phases[phase]+".csv"),"frame_ms\n"+String.join("\n",frames.stream().map(Object::toString).toList()));
            if(phase==0){phase=-2;loadStart=now();mc.createWorldOpenFlows().openWorld("Benchmark",()->{throw new IllegalStateException("Fixture load cancelled");});}
            else if(phase==1){mapProof(mc);if(Boolean.getBoolean("unbox.worldOnly")){done=true;output.put("phases",reports);Files.writeString(dir.resolve("results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(output));mc.stop();return;}phase=2;mc.gui.setScreen(new UnboxHomeScreen(true));}
            else if(phase==2){phase=3;mc.gui.setScreen(new VectorModsScreen(new UnboxHomeScreen(true)));}
            else if(phase==3){phase=4;}
            else if(phase==4){phase=5;var screen=new UnboxScreen(new VectorModsScreen(new UnboxHomeScreen(true)));mc.gui.setScreen(screen);screen.showDetail("minimap");}
            else{done=true;output.put("phases",reports);Files.writeString(dir.resolve("results.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(output));mc.stop();}
            entered=now();last=0;frames.clear();
        }catch(Throwable e){done=true;e.printStackTrace();try{Files.writeString(mc.gameDirectory.toPath().resolve("bench-failure.txt"),e.toString());}catch(Exception ignored){}mc.stop();}
    }
}
