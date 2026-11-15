package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Creates a fresh test world only under the repository's isolated cache profiles. */
final class PortWorldBootstrap {
    private static boolean started;
    static void tick(Minecraft mc){
        if(started||!Boolean.getBoolean("unbox.createPortWorld")||mc.getOverlay()!=null||!(mc.screen instanceof UnboxHomeScreen))return;
        if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().toString().contains("/.cache/"))
            throw new IllegalStateException("Backport world creation requires an isolated cache profile");
        started=true;
        String name=System.getProperty("unbox.portWorld","port-world");
        if(!name.matches("port-world(?:-[a-f0-9]{12})?"))throw new IllegalArgumentException("Invalid port test world name");
        if(java.nio.file.Files.exists(mc.gameDirectory.toPath().resolve("saves/"+name+"/level.dat"))){
            mc.createWorldOpenFlows().openWorld(name,()->mc.setScreen(new UnboxHomeScreen(false)));
            return;
        }
        var data=WorldDataConfiguration.DEFAULT;
        var settings=new LevelSettings("Unbox Port Verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(data.enabledFeatures()),data);
        mc.createWorldOpenFlows().createFreshLevel(name,settings,new WorldOptions(10112111L,false,false),WorldPresets::createFlatWorldDimensions,new UnboxHomeScreen(false));
    }
}
