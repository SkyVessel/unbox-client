package dev.unbox.client;
import net.minecraft.client.Minecraft;

import java.nio.file.*;
import java.util.*;
import java.io.*;
/** Opt-in development benchmark. Never active in normal launcher sessions. */
public final class FrameBenchmark {
    private static final String RUN=System.getProperty("unbox.benchmark","");
    private static long worldStart,last,start;
    private static int inactive;
    private static boolean done;
    private static final ArrayList<Double> times=new ArrayList<>();
    public static void frame(){
        if(RUN.isEmpty()||done)return;
        var m=Minecraft.getInstance();if(m.player==null||m.level==null||m.screen!=null){last=0;return;}
        m.getFramerateLimitTracker().onInputReceived();
        long now=System.nanoTime();if(worldStart==0)worldStart=now;
        m.player.setYRot(135);m.player.setXRot(15);
        if(now-worldStart<30_000_000_000L){last=0;return;}
        if(start==0)start=now;
        if(last!=0){times.add((now-last)/1_000_000.0);if(!m.isWindowActive())inactive++;}last=now;
        if(now-start<45_000_000_000L)return;
        done=true;
        try{
            Path dir=Platform.gameDir().resolve("logs");Files.createDirectories(dir);
            try(var writer=Files.newBufferedWriter(dir.resolve("frame-times.csv"))){writer.write("frame_ms\n");for(double d:times)writer.write(d+"\n");}
            double total=times.stream().mapToDouble(d->d).sum();times.sort(Double::compare);
            int slow=Math.max(1,(int)Math.ceil(times.size()*.01));double slowMean=times.subList(times.size()-slow,times.size()).stream().mapToDouble(d->d).average().orElse(0);
            String report=String.format(Locale.ROOT,"{\"run\":\"%s\",\"samples\":%d,\"averageFps\":%.3f,\"onePercentLowFps\":%.3f,\"p95Ms\":%.3f,\"p99Ms\":%.3f,\"inactiveFrames\":%d,\"framebufferWidth\":%d,\"framebufferHeight\":%d,\"renderDistance\":%d,\"warmupSeconds\":30,\"sampleSeconds\":45}",RUN,times.size(),times.size()*1000/total,1000/slowMean,times.get((int)(times.size()*.95)),times.get((int)(times.size()*.99)),inactive,m.getWindow().getWidth(),m.getWindow().getHeight(),m.options.renderDistance().get());
            Files.writeString(dir.resolve("benchmark.json"),report);System.out.println("[Unbox benchmark] "+report);
        }catch(IOException e){throw new UncheckedIOException(e);}
        m.stop();
    }
}
