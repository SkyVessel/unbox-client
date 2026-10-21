package dev.unbox.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

/** Two-process disposable-world test. Enable only with -Dunbox.gravePersistence=write|read. */
public final class GravePersistenceSmokeTest {
    private static final String MODE=System.getProperty("unbox.gravePersistence","");
    private static volatile int phase;
    private static int ticks,bootTicks,visualStart;
    private static final Properties saved=new Properties();
    private static final List<String> checks=Collections.synchronizedList(new ArrayList<>());
    private static GraveIntegrationChecks.Run run;
    private GravePersistenceSmokeTest(){}

    public static void tick(Minecraft mc){
        if((!MODE.equals("write")&&!MODE.equals("read"))||phase==99)return;
        mc.getFramerateLimitTracker().onInputReceived();
        if(mc.player==null||mc.getSingleplayerServer()==null){
            if(++bootTicks>2400)finish(mc,new IllegalStateException("Grave persistence startup timed out"));
            return;
        }
        try{
            if(++ticks>2400)throw new IllegalStateException("Grave persistence "+MODE+" timed out in phase "+phase);
            switch(phase){
                case 0->{
                    if(ticks<100)return;
                    // An interrupted previous fixture may have saved a dead player; normal respawn first.
                    if(!mc.player.isAlive()){mc.player.respawn();return;}
                    saved.putAll(UnboxClient.CONFIG);phase=1;
                    server(mc,()->{
                        if(MODE.equals("write")){run=GraveIntegrationChecks.beginDeath(player(mc));phase=2;}
                        else{run=GraveIntegrationChecks.readPersistenceManifest(player(mc),manifest(mc));phase=10;}
                    });
                }
                case 2->{if(!mc.player.isAlive()){phase=3;mc.player.respawn();}}
                case 3->{if(mc.player.isAlive()){
                    phase=5;server(mc,()->{
                        var player=player(mc);var level=player.level().getServer().getLevel(run.dimension);var pos=run.gravePosition();
                        // Consistent lighting for the model review screenshot in this disposable world.
                        var server=mc.getSingleplayerServer();
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"time set day");
                        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(),"weather clear");
                        player.teleportTo(level,pos.getX()+.5,pos.getY(),pos.getZ()+3.0,Set.of(),180,18,true);
                        player.setNoGravity(true);player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                        level.getChunkAt(pos);phase=4;
                    });
                }}
                case 4->{
                    phase=6;server(mc,()->{
                        ServerPlayer player=player(mc);
                        if(!GraveIntegrationChecks.reloadedWorldReady(player,run)){phase=4;return;}
                        GraveIntegrationChecks.verifyReloadedWorld(player,run);
                        visualStart=ticks;phase=7;
                    });
                }
                case 7->{
                    if(ticks-visualStart<40)return;
                    var pos=run.gravePosition();
                    if(!GraveModel.isGraveAnchor(mc.level,pos)
                        || Float.compare(mc.level.getBlockState(pos).getDestroySpeed(mc.level,pos),1.5f)!=0)
                        throw new IllegalStateException("Client grave metadata or stone mining hardness did not synchronize");
                    GraveIntegrationChecks.verifyModelShape(mc.level,pos);
                    checks.add("Client received marker metadata; stone hardness 1.5 and upper-tablet selection/collision match the model, ordinary slabs stay unchanged");
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory,"grave-model.png",mc.getMainRenderTarget(),1,c->{});
                    visualStart=ticks;phase=70;
                }
                case 70->{if(ticks-visualStart<20)return;mc.level.destroyBlockProgress(-7654321,run.gravePosition(),2);visualStart=ticks;phase=71;}
                case 71->{if(ticks-visualStart<20)return;net.minecraft.client.Screenshot.grab(mc.gameDirectory,"grave-cracks-2.png",mc.getMainRenderTarget(),1,c->{});mc.level.destroyBlockProgress(-7654321,run.gravePosition(),7);visualStart=ticks;phase=72;}
                case 72->{if(ticks-visualStart<20)return;net.minecraft.client.Screenshot.grab(mc.gameDirectory,"grave-cracks-7.png",mc.getMainRenderTarget(),1,c->{});mc.level.destroyBlockProgress(-7654321,run.gravePosition(),-1);visualStart=ticks;phase=73;}
                case 73->{
                    if(ticks-visualStart<20)return;
                    net.minecraft.client.Screenshot.grab(mc.gameDirectory,"grave-cracks-cleared.png",mc.getMainRenderTarget(),1,c->{});
                    checks.add("Rendered grave crack stages 2 and 7, then cleared progress; screenshots captured for visual comparison");
                    phase=8;server(mc,()->{
                        ServerPlayer player=player(mc);
                        GraveIntegrationChecks.finish(player,run);
                        // Full vanilla save includes chunks, entities, player data and dimension SavedData.
                        player.level().getServer().saveEverything(true,true,true);
                        GraveIntegrationChecks.writePersistenceManifest(player,run,manifest(mc));
                        checks.addAll(run.checks());
                        checks.add("Full vanilla save completed with invisible anchor, three model displays and nameplate unclaimed; pre-death component expectations written to manifest");
                        finish(mc,null);
                    });
                }
                case 10->{
                    phase=11;server(mc,()->{
                        if(!GraveIntegrationChecks.reloadedWorldReady(player(mc),run)){phase=10;return;}
                        GraveIntegrationChecks.verifyReloadedWorld(player(mc),run);
                        checks.add("Second JVM loaded persisted anchor, the same three model display UUIDs, text-display entity and complete stored stacks from the same world");
                        GraveIntegrationChecks.verifyRespawnAndBeginMining(player(mc),run);phase=20;
                    });
                }
                case 20->{
                    phase=21;server(mc,()->{
                        if(!GraveIntegrationChecks.advanceMining(player(mc),run)){phase=20;return;}
                        checks.addAll(run.checks());GraveIntegrationChecks.finish(player(mc),run);
                        player(mc).level().getServer().saveEverything(true,true,true);
                        checks.add("After restart the owner mined and recovered every expected stack; cleared grave state was saved normally");
                        finish(mc,null);
                    });
                }
            }
        }catch(Throwable error){finish(mc,error);}
    }
    private interface Checked {void run()throws Exception;}
    private static void server(Minecraft mc,Checked operation){
        mc.getSingleplayerServer().execute(()->{try{operation.run();}catch(Throwable error){finish(mc,error);}});
    }
    private static ServerPlayer player(Minecraft mc){
        return Objects.requireNonNull(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.getUser().getProfileId()),"fixture server player");
    }
    private static Path manifest(Minecraft mc){return mc.gameDirectory.toPath().resolve("logs/grave-persistence-manifest.json");}
    private static synchronized void finish(Minecraft mc,Throwable error){
        if(phase==99)return;int lastPhase=phase;phase=99;
        var result=new LinkedHashMap<String,Object>();result.put("ok",error==null);result.put("mode",MODE);
        result.put("loader",Platform.loader());result.put("pid",ProcessHandle.current().pid());result.put("phase",lastPhase);
        result.put("manifest",manifest(mc).toAbsolutePath().toString());result.put("checks",new ArrayList<>(checks));
        if(run!=null&&run.gravePosition()!=null)result.put("grave",run.gravePosition().toShortString());
        if(error!=null){result.put("error",error.toString());error.printStackTrace();}
        if(!saved.isEmpty()){UnboxClient.CONFIG.clear();UnboxClient.CONFIG.putAll(saved);}
        try{
            Path output=mc.gameDirectory.toPath().resolve("logs/grave-persistence-"+MODE+".json");
            Files.createDirectories(output.getParent());Files.writeString(output,new Gson().toJson(result));
        }catch(Exception writeError){writeError.printStackTrace();}
        // The runner must wait for process exit; the report alone is not proof of a completed shutdown.
        mc.execute(mc::stop);
    }
}
