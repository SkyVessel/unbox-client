package dev.unbox.client;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.server.level.ServerPlayer;
import java.nio.file.Files;
import java.util.*;

/** Opt-in disposable-world integration checks. Inert during ordinary play. */
public final class SurvivalSmokeTest {
    private static volatile int phase;
    private static int ticks,bootTicks,mapOpened;
    private static final List<String> checks=Collections.synchronizedList(new ArrayList<>());
    private static final Properties saved=new Properties();
    private static GraveIntegrationChecks.Run grave;
    private SurvivalSmokeTest(){}
    public static void tick(Minecraft mc){
        boolean active=Boolean.getBoolean("unbox.survivalSmoke");
        if(!active&&!Boolean.getBoolean("unbox.uiSmoke"))return;
        mc.getFramerateLimitTracker().onInputReceived();
        if(mc.player==null){
            if(++bootTicks%100==0&&mc.screen!=null){
                System.out.println("[Unbox test] Startup screen: "+mc.screen.getClass().getName()+" / "+mc.screen.getTitle().getString()+" / "+mc.screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast).map(w->w.getMessage().getString()).toList());
                Screenshot.grab(mc.gameDirectory,"startup-state.png",mc.getMainRenderTarget(),1,c->{});
            }
            return;
        }
        if(!active||phase==99)return;
        try{
            if(++ticks>2400)throw new IllegalStateException("Survival integration timed out in phase "+phase);
            switch(phase){
                case 0->{if(ticks<100)return;saved.putAll(UnboxClient.CONFIG);phase=1;MapIntegrationChecks.run(mc).whenComplete((result,error)->mc.execute(()->{if(error!=null)finish(mc,error);else{checks.addAll(result);phase=2;}}));}
                case 2->{WorldMap.open();mapOpened=ticks;phase=14;}
                case 14->{if(ticks-mapOpened<40)return;Screenshot.grab(mc.gameDirectory,"survival-map.png",mc.getMainRenderTarget(),1,c->{});mc.setScreen(null);phase=3;server(mc,()->{grave=GraveIntegrationChecks.beginDeath(player(mc));phase=4;});}
                case 4->{if(!mc.player.isAlive()){phase=5;mc.player.respawn();}}
                case 5->{if(mc.player.isAlive()){phase=6;server(mc,()->{GraveIntegrationChecks.verifyRespawnAndBeginMining(player(mc),grave);phase=7;});}}
                case 7->{phase=8;server(mc,()->{if(GraveIntegrationChecks.advanceMining(player(mc),grave)){checks.addAll(grave.checks());GraveIntegrationChecks.finish(player(mc),grave);grave=GraveIntegrationChecks.beginKeepInventoryDeath(player(mc));phase=9;}else phase=7;});}
                case 9->{if(!mc.player.isAlive()){phase=10;mc.player.respawn();}}
                case 10->{if(mc.player.isAlive()){phase=11;server(mc,()->{GraveIntegrationChecks.verifyKeepInventoryRespawn(player(mc),grave);checks.addAll(grave.checks());GraveIntegrationChecks.finish(player(mc),grave);phase=12;});}}
                case 12->{phase=13;NativeModIntegrationChecks.begin(mc).whenComplete((result,error)->mc.execute(()->{if(error!=null)finish(mc,error);else{checks.addAll(result);finish(mc,null);}}));}
                case 13->{NativeModIntegrationChecks.tick(mc);}
            }
        }catch(Throwable error){finish(mc,error);}
    }
    private interface Checked {void run()throws Exception;}
    private static void server(Minecraft mc,Checked operation){mc.getSingleplayerServer().execute(()->{try{operation.run();}catch(Throwable e){finish(mc,e);}});}
    private static ServerPlayer player(Minecraft mc){return Objects.requireNonNull(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.getUser().getProfileId()));}
    private static synchronized void finish(Minecraft mc,Throwable error){
        if(phase==99)return;phase=99;
        var result=new LinkedHashMap<String,Object>();result.put("ok",error==null);result.put("loader",Platform.loader());result.put("checks",new ArrayList<>(checks));
        if(error!=null){result.put("error",error.toString());error.printStackTrace();}
        UnboxClient.CONFIG.clear();UnboxClient.CONFIG.putAll(saved);
        try{Files.writeString(mc.gameDirectory.toPath().resolve("logs/survival-smoke.json"),new Gson().toJson(result));}catch(Exception e){e.printStackTrace();}
        mc.execute(mc::stop);
    }
}
