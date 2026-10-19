package dev.unbox.client;

import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in wall-time instrumentation. Nested groups are inclusive, not an additive CPU breakdown. */
public final class PerformanceMonitor {
    public enum Group {
        CLIENT_TICK("Game client tick"), RENDER("Game render submission"), EXTRACT("Game render extraction"), SERVER("Integrated server tick"),
        SOCIAL("Unbox social / room"), FILES("Shared files (worker elapsed)"), SETTINGS("Unbox integration settings"), GAMEPLAY("Unbox gameplay / camera"), MAP("Unbox map update"),
        HUD("Unbox HUD (includes map)"), MAP_DRAW("Unbox map drawing"), SELF("Profiler snapshot / HUD");
        final String label;Group(String label){this.label=label;}
    }
    private static final ExecutorService IO=Executors.newSingleThreadExecutor(Thread.ofVirtual().name("unbox-performance-export",0).factory());
    private static final com.sun.management.OperatingSystemMXBean OS=ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean b?b:null;
    private static volatile boolean running;
    private static volatile long started;
    private static long stopped,lastFrame,lastSnapshot,gcStart;
    private static PerformanceSamples frames=new PerformanceSamples();
    private static volatile PerformanceSamples[] groups=freshGroups();
    private static Map<String,Object> snapshot=Map.of();
    private static List<String> lines=List.of("Start a recording to inspect performance.");
    static volatile String exportStatus="";
    static boolean overlay;
    private PerformanceMonitor(){}
    private static PerformanceSamples[] freshGroups(){var a=new PerformanceSamples[Group.values().length];Arrays.setAll(a,i->new PerformanceSamples());return a;}
    public static boolean running(){return running;}
    public static long begin(){return running?System.nanoTime():0;}
    public static void end(Group group,long start){if(start!=0&&running&&start>=started)groups[group.ordinal()].add(System.nanoTime()-start);}
    static void start(){running=false;groups=freshGroups();frames=new PerformanceSamples();snapshot=Map.of();lastFrame=lastSnapshot=stopped=0;gcStart=gcTime();started=System.nanoTime();running=true;exportStatus="";refresh();}
    static void stop(){if(!running)return;stopped=System.nanoTime();running=false;lastFrame=0;refresh();}
    public static void frame(){
        if(!running)return;
        long now=System.nanoTime();var mc=Minecraft.getInstance();
        // Menu, focus-loss and pause frames are excluded from gameplay frame statistics.
        if(mc.level==null||mc.screen!=null||!mc.isWindowActive()||mc.isPaused()){lastFrame=0;}else{if(lastFrame!=0)frames.add(now-lastFrame);lastFrame=now;}
        if(now-lastSnapshot>=1_000_000_000L)refresh();
    }
    private static long gcTime(){long sum=0;for(var gc:ManagementFactory.getGarbageCollectorMXBeans())sum+=Math.max(0,gc.getCollectionTime());return sum;}
    static List<String> lines(){return lines;}
    private static void refresh(){
        long stamp=begin(),now=System.nanoTime();lastSnapshot=now;
        double seconds=Math.max(.001,((running?now:stopped)-started)/1e9);
        var f=frames.snapshot();Runtime rt=Runtime.getRuntime();long heap=(rt.totalMemory()-rt.freeMemory())/1048576,max=rt.maxMemory()/1048576;
        double cpu=OS==null?-1:OS.getProcessCpuLoad();long gc=Math.max(0,gcTime()-gcStart);
        List<String> text=new ArrayList<>();text.add((running?"RECORDING":"STOPPED")+String.format(Locale.ROOT,"  %.0f s | %d gameplay frames",seconds,f.calls()));
        text.add(String.format(Locale.ROOT,"FPS %.1f | frame avg %.2f ms | p95 %.2f | p99 %.2f",f.averageMs()==0?0:1000/f.averageMs(),f.averageMs(),f.p95Ms(),f.p99Ms()));
        text.add("Heap "+heap+" / "+max+" MiB | GC "+gc+" ms | process CPU "+(cpu<0?"N/A":String.format(Locale.ROOT,"%.1f%%",cpu*100)));
        Map<String,Object> report=new LinkedHashMap<>(),timings=new LinkedHashMap<>();
        for(Group g:Group.values()){var sample=groups[g.ordinal()].snapshot();timings.put(g.name(),sample);text.add(String.format(Locale.ROOT,"%s: %.2f ms/s | avg %.3f ms",g.label,sample.totalMs()/seconds,sample.averageMs()));}
        text.add("Inclusive wall time; groups overlap. GPU / per-mod memory: unavailable.");
        text.add("Percentiles: latest 4096 calls. CPU: share of all logical processors.");
        text.add("Frame samples exclude menus, pause and unfocused windows.");
        text.add("Unbox rows cover our hooks; native mod work remains in Game totals. Use A/B.");
        var mc=Minecraft.getInstance();report.put("capturedAt",Instant.now().toString());report.put("game",net.minecraft.SharedConstants.getCurrentVersion().id());report.put("loader",Platform.loader());report.put("java",System.getProperty("java.version"));report.put("os",System.getProperty("os.name")+" "+System.getProperty("os.arch"));report.put("logicalProcessors",rt.availableProcessors());report.put("seconds",seconds);report.put("frames",f);report.put("groups",timings);report.put("heapUsedMiB",heap);report.put("heapMaxMiB",max);report.put("gcMillis",gc);report.put("processCpuFraction",cpu);
        report.put("window",List.of(mc.getWindow().getWidth(),mc.getWindow().getHeight()));report.put("renderDistance",mc.options.renderDistance().get());report.put("vsync",mc.options.enableVsync().get());report.put("fpsLimit",mc.options.framerateLimit().get());
        Map<String,Boolean> mods=new TreeMap<>(),configured=new TreeMap<>();for(var mod:ModOptions.MODS){String id=mod.id();if(id.equals("performance"))continue;configured.put(id,UnboxClient.configured(id));boolean active=id.equals("gravestones")?GraveKeeper.hostEnabled():WorldFeatures.manages(id)||id.equals("veinminer")||id.equals("fallingtree")?SurvivalHostMods.hostEnabled(id):UnboxClient.enabled(id);mods.put(id,active);}report.put("effectiveModules",mods);report.put("configuredModules",configured);
        report.put("measurementNotes","Inclusive elapsed wall time, groups overlap; no GPU or per-mod memory attribution. Percentiles use last 4096 samples. Frames exclude menus, pause and focus loss. Module states are at snapshot time; start a new recording after changing settings.");
        snapshot=Collections.unmodifiableMap(report);lines=List.copyOf(text);end(Group.SELF,stamp);
    }
    static void export(){
        if(snapshot.isEmpty()){exportStatus="Record a sample first.";return;}if(running)refresh();
        String data=new GsonBuilder().setPrettyPrinting().create().toJson(snapshot);
        exportStatus="Saving report...";
        IO.execute(()->{try{Path dir=Platform.gameDir().resolve("logs/unbox-performance");Files.createDirectories(dir);Path path=dir.resolve("capture-"+UUID.randomUUID()+".json");Files.writeString(path,data);exportStatus="Saved in logs/unbox-performance";}catch(Exception e){exportStatus="Export failed: "+e.getClass().getSimpleName();}});
    }
    static void draw(GuiGraphicsExtractor g){
        if(!running||!overlay||Minecraft.getInstance().screen!=null)return;
        long stamp=begin();g.pose().pushMatrix();float scale=Math.min(1f,g.guiWidth()/510f);g.pose().scale(scale);
        for(int i=0;i<Math.min(3,lines.size());i++)PanelStyle.hudLabel(g,lines.get(i),8,8+i*12,0xffa9c8ff,true);
        g.pose().popMatrix();end(Group.SELF,stamp);
    }
}
