package dev.unbox.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.server.permissions.PermissionSet;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/** Explicitly invoked by the disposable-world harness; never runs as a normal gameplay tick. */
final class MapIntegrationChecks {
    private MapIntegrationChecks(){}

    /** Call on the client thread. Poll/chain the result; never join it on that thread. */
    static CompletableFuture<List<String>> run(Minecraft mc){
        var result=new CompletableFuture<List<String>>();
        if(!Boolean.getBoolean("unbox.survivalSmoke")||mc.getSingleplayerServer()==null||mc.player==null){
            result.completeExceptionally(new IllegalStateException("Map checks require the opt-in disposable integrated-world harness"));return result;
        }
        var checks=new ArrayList<String>();
        try{clientChecks(mc,checks);}catch(Throwable error){result.completeExceptionally(error);return result;}
        // Explicit root injection prevents this fixture from touching the user's persistent map library.
        Path root=mc.gameDirectory.toPath().resolve("logs/map-checks").resolve(UUID.randomUUID().toString());
        Thread.ofVirtual().name("unbox-map-integration-checks").start(()->{
            try{storageChecks(root,checks);result.complete(List.copyOf(checks));}
            catch(Throwable error){result.completeExceptionally(error);}
        });
        return result;
    }

    private static void clientChecks(Minecraft mc,List<String> checks){
        var previousScreen=mc.screen;var permissions=mc.player.permissions();
        var previousKey=InputConstants.getKey(UnboxClient.mapKey.saveString());
        String enabled=UnboxClient.CONFIG.getProperty("minimap"),deaths=UnboxClient.CONFIG.getProperty("minimap.deaths");
        var saved=new LinkedHashMap<>(WorldMap.DEATHS);
        try{
            UnboxClient.CONFIG.setProperty("minimap","true");UnboxClient.CONFIG.setProperty("minimap.deaths","true");
            WorldMap.tick(mc);check(WorldMap.terrain!=null,"stable world identity initializes terrain storage");
            // Seed after normal tick initialization so connection setup cannot erase the fixture.
            WorldMap.DEATHS.clear();UUID own=mc.player.getUUID(),peer=UUID.randomUUID();
            String dim=mc.level.dimension().identifier().toString(),otherDim=dim.equals("minecraft:the_nether")?"minecraft:overworld":"minecraft:the_nether";int x=mc.player.getBlockX(),y=mc.player.getBlockY(),z=mc.player.getBlockZ();
            WorldMap.death(new WorldMap.Death(own,"SameName",dim,x,y,z,10));
            WorldMap.death(new WorldMap.Death(peer,"SameName",dim,x+7,y,z,11));
            check(WorldMap.DEATHS.size()==2,"same-name player UUIDs retain separate death markers");
            WorldMap.death(new WorldMap.Death(own,"Renamed",otherDim,x+2,y,z+2,20));
            WorldMap.death(new WorldMap.Death(own,"Stale",dim,x,y,z,15));
            check(WorldMap.DEATHS.size()==2&&WorldMap.DEATHS.get(own).time()==20&&WorldMap.DEATHS.get(own).dimension().equals(otherDim),"latest UUID death replaces all older dimensions and ignores stale packets");
            WorldMap.death(new WorldMap.Death(own,"Renamed",dim,x,y,z,30));
            check(WorldMap.DEATHS.get(peer).time()==11,"one player's new death preserves teammate marker");
            checks.add("Death state keeps one latest marker per UUID across dimensions, preserves same-name teammates, and rejects stale updates");

            UnboxClient.mapKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_M));KeyMapping.resetMapping();
            WorldMap.open();check(mc.screen instanceof WorldMap.MapScreen,"M target opens full map screen");
            var screen=(WorldMap.MapScreen)mc.screen;
            check(screen.mapWidth==Math.max(32,screen.width-24)&&screen.mapHeight==Math.max(32,screen.height-89),"full map viewport uses the full available window aspect");
            WorldMap.tick(mc);
            double previousTarget=WorldMap.targetX;
            screen.mouseDragged(new MouseButtonEvent(screen.x+screen.mapWidth/2,screen.y+screen.mapHeight/2,new MouseButtonInfo(0,0)),.25,0);
            check(WorldMap.targetX<previousTarget,"subpixel dragging moves the map immediately without waiting for a terrain rescan");
            double wx=WorldMap.originX+screen.mapWidth*.7*WorldMap.spanX/screen.mapWidth;
            double wz=WorldMap.originZ+screen.mapHeight*.6*WorldMap.spanZ/screen.mapHeight;
            screen.mouseScrolled(screen.x+screen.mapWidth*.7,screen.y+screen.mapHeight*.6,0,1);
            check(Math.abs(wx-(WorldMap.originX+.7*WorldMap.spanX))<.0001&&Math.abs(wz-(WorldMap.originZ+.6*WorldMap.spanZ))<.0001,"zoom preserves the world point under the cursor");
            var pos=new net.minecraft.core.BlockPos(x,Math.min(mc.level.getMaxY()-2,y+30),z);
            var before=mc.level.getBlockState(pos);
            try{
                mc.level.setBlock(pos,net.minecraft.world.level.block.Blocks.RED_WOOL.defaultBlockState(),3);
                check(WorldMap.changedColumns.contains(MapTerrainStore.key(x,z)),"client block-update hook queues the edited column");
                WorldMap.tick(mc);int color=WorldMap.terrain.color(x,z);
                check(((color>>16)&255)>((color>>8)&255)*2,"new red block is captured within one client tick");
            }finally{mc.level.setBlock(pos,before,3);WorldMap.tick(mc);}
            check(ModOptions.MODS.stream().noneMatch(mod->mod.id().equals("step")),"Smooth Step is absent from the module catalog");
            checks.add("Subpixel map drag responds immediately, cursor-anchored zoom preserves coordinates, live block changes refresh within one tick, and Smooth Step is removed");
            WorldMap.open();screen=(WorldMap.MapScreen)mc.screen;WorldMap.tick(mc);
            int px=screen.x+(int)Math.round((x-WorldMap.originX)*screen.mapWidth/(double)WorldMap.spanX);
            int py=screen.y+(int)Math.round((z-WorldMap.originZ)*screen.mapHeight/(double)WorldMap.spanZ);
            mc.player.setPermissions(PermissionSet.NO_PERMISSIONS);
            check(!WorldMap.canTeleport(),"unprivileged player cannot teleport");
            screen.mouseClicked(new MouseButtonEvent(px,py,new MouseButtonInfo(0,0)),true);
            check(mc.screen==screen&&screen.notice.contains("requires"),"death double-click stays in map with a permission denial");
            mc.player.setPermissions(PermissionSet.ALL_PERMISSIONS);
            check(WorldMap.canTeleport(),"operator permission enables the client teleport gate");
            mc.player.setPermissions(permissions);
            checks.add("Death double-click denies unprivileged players; the client gate recognizes operator permission (no teleport command sent)");

            screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_M,0,0));check(mc.screen==null,"M closes the map");
            UnboxClient.mapKey.setKey(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_F8));KeyMapping.resetMapping();
            WorldMap.open();screen=(WorldMap.MapScreen)mc.screen;
            screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_M,0,0));check(mc.screen==screen,"old map key stops closing after rebind");
            screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_F8,0,0));check(mc.screen==null,"configured F8 closes the map");
            WorldMap.open();mc.screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE,0,0));check(mc.screen==null,"Escape closes the map");
            check(ClientHud.size("minimap",false)[1]==143,"minimap layout includes coordinates below its 128px map");
            checks.add("Full map opens, uses window aspect, closes with M/Escape, honors a rebound key, and reserves space below minimap for coordinates");
        }finally{
            mc.player.setPermissions(permissions);UnboxClient.mapKey.setKey(previousKey);KeyMapping.resetMapping();
            restore("minimap",enabled);restore("minimap.deaths",deaths);
            WorldMap.DEATHS.clear();WorldMap.DEATHS.putAll(saved);mc.setScreen(previousScreen);
        }
    }

    private static void storageChecks(Path root,List<String> checks)throws Exception{
        Files.createDirectories(root);String world=UUID.randomUUID().toString(),dim="minecraft:overworld";
        int x=-17,z=-33,first=0xff123456,neighbor=0xffabcdef,changed=0xff765432;
        MapTerrainStore writer=new MapTerrainStore(root,world,dim);
        writer.tick();writer.capture(x,z,first);writer.capture(x-1,z,neighbor);writer.close();MapTerrainStore.finishWrites();
        MapTerrainStore reader=new MapTerrainStore(root,world,dim);
        try{
            await(()->{reader.tick();return reader.color(x,z)==first&&reader.color(x-1,z)==neighbor;},"explored negative-coordinate tile did not survive close/reopen");
        }finally{reader.close();}
        // Reopen with an update before the queued read completes. Existing neighboring cells must survive.
        MapTerrainStore merge=new MapTerrainStore(root,world,dim);
        try{
            merge.tick();merge.capture(x,z,changed);
            await(()->{merge.tick();return merge.color(x,z)==changed&&merge.color(x-1,z)==neighbor;},"asynchronous disk read overwrote fresh terrain or lost an old neighbor");
        }finally{merge.close();}
        MapTerrainStore.finishWrites();MapTerrainStore reopened=new MapTerrainStore(root,world,dim);
        try{await(()->{reopened.tick();return reopened.color(x,z)==changed&&reopened.color(x-1,z)==neighbor;},"merged terrain did not persist");}finally{reopened.close();}
        // Disconnect before a disk read can be merged into the live cache: the writer must merge on disk too.
        MapTerrainStore quick=new MapTerrainStore(root,world,dim);quick.tick();quick.capture(x,z,first);quick.close();MapTerrainStore.finishWrites();
        MapTerrainStore afterQuick=new MapTerrainStore(root,world,dim);
        try{await(()->{afterQuick.tick();return afterQuick.color(x,z)==first&&afterQuick.color(x-1,z)==neighbor;},"close during pending read erased neighboring terrain");}finally{afterQuick.close();}
        checks.add("Terrain survives background flush and close/reopen at negative chunk coordinates; fresh captures win asynchronous reads without erasing old neighboring cells");
        MapTerrainStore otherWorld=new MapTerrainStore(root,UUID.randomUUID().toString(),dim),otherDimension=new MapTerrainStore(root,world,"minecraft:the_nether");
        try{
            otherWorld.tick();otherDimension.tick();otherWorld.color(x,z);otherDimension.color(x,z);MapTerrainStore.finishWrites();otherWorld.tick();otherDimension.tick();
            check(otherWorld.color(x,z)==0&&otherDimension.color(x,z)==0,"terrain leaked between world or dimension namespaces");
        }finally{otherWorld.close();otherDimension.close();}
        checks.add("Persisted terrain is isolated by world identity and dimension; fixture files stay under logs/map-checks");
    }
    private static void await(BooleanSupplier ready,String failure)throws Exception{
        long deadline=System.nanoTime()+5_000_000_000L;
        while(!ready.getAsBoolean()){if(System.nanoTime()>deadline)throw new IllegalStateException(failure);Thread.sleep(20);}
    }
    private static void restore(String key,String value){if(value==null)UnboxClient.CONFIG.remove(key);else UnboxClient.CONFIG.setProperty(key,value);}
    private static void check(boolean passed,String message){if(!passed)throw new IllegalStateException(message);}
}
