package dev.unbox.client;

import java.util.*;
import net.minecraft.client.*;

/** Calls real loader tick callbacks before any Unbox cleanup can hide an opened screen. */
final class BuildingInputSmokeTest {
    static void verify(Minecraft mc,List<String> checks)throws Exception{
        var previous=mc.screen;var originalMode=mc.gameMode.getPlayerMode();var info=mc.getConnection().getPlayerInfo(mc.player.getUUID());var originalInfoMode=info.getGameMode();var setMode=info.getClass().getDeclaredMethod("setGameMode",net.minecraft.world.level.GameType.class);setMode.setAccessible(true);String saved=UnboxClient.CONFIG.getProperty("building");
        Class<?> bindings=Class.forName("nl.requios.effortlessbuilding.utilities.KeyBindings");var held=bindings.getMethod("isKeyDown",KeyMapping.class);
        var count=KeyMapping.class.getDeclaredField("clickCount");count.setAccessible(true);
        Class<?> owner=Class.forName(Platform.loader().equals("fabric")?"nl.requios.effortlessbuilding.EffortlessBuildingClient":"nl.requios.effortlessbuilding.NeoForgeClientSetup$GameEvents");
        var tick=Arrays.stream(owner.getDeclaredMethods()).filter(m->m.getParameterCount()==1&&(Platform.loader().equals("fabric")?m.getName().startsWith("lambda$onInitializeClient$")&&m.getParameterTypes()[0]==Minecraft.class:m.getName().equals("onClientTick"))).findFirst().orElseThrow();tick.setAccessible(true);
        try{
            for(boolean survival:new boolean[]{false,true}){
                var mode=survival?net.minecraft.world.level.GameType.SURVIVAL:net.minecraft.world.level.GameType.CREATIVE;mc.gameMode.setLocalMode(mode);setMode.invoke(info,mode);UnboxClient.set("building",survival);WorldFeatures.tick(mc);mc.setScreen(null);
                if(WorldFeatures.buildingClient())throw new IllegalStateException("Building test must be restricted");
                for(int attempt=0;attempt<20;attempt++){
                    if((boolean)held.invoke(null,new Object[]{null}))throw new IllegalStateException("Forbidden held building input passed");
                    for(String key:List.of("openModifiersScreen","undo","redo"))count.setInt(bindings.getField(key).get(null),3);
                    tick.invoke(null,Platform.loader().equals("fabric")?mc:null);
                    if(mc.screen!=null)throw new IllegalStateException("Forbidden building key created a screen before cleanup");
                }
                checks.add((survival?"Survival":"Disabled creative building")+" rejects held keys and queued menu/undo/redo before screen creation for 20 ticks");
            }
            mc.gameMode.setLocalMode(net.minecraft.world.level.GameType.CREATIVE);setMode.invoke(info,net.minecraft.world.level.GameType.CREATIVE);UnboxClient.set("building",true);WorldFeatures.tick(mc);
            if(!WorldFeatures.buildingClient())throw new IllegalStateException("Creative building must re-enable");
            for(String key:List.of("openModifiersScreen","undo","redo"))if(((KeyMapping)bindings.getField(key).get(null)).consumeClick())throw new IllegalStateException("Disabled building clicks replayed after enabling");
            count.setInt(bindings.getField("openModifiersScreen").get(null),1);tick.invoke(null,Platform.loader().equals("fabric")?mc:null);
            if(mc.screen==null||!mc.screen.getClass().getName().endsWith("ModifiersScreen"))throw new IllegalStateException("Enabled creative modifier shortcut failed");
            checks.add("Creative building opens its real modifiers screen after enabling, with no stale clicks");
        }finally{
            mc.setScreen(previous);mc.gameMode.setLocalMode(originalMode);setMode.invoke(info,originalInfoMode);if(saved==null)UnboxClient.CONFIG.remove("building");else UnboxClient.CONFIG.setProperty("building",saved);WorldFeatures.tick(mc);
        }
    }
}
