package dev.unbox.client;

import com.mojang.authlib.GameProfile;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.phys.AABB;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Explicit disposable-world helper. Caller owns client death-screen respawn and tick scheduling. */
public final class GraveIntegrationChecks {
    private GraveIntegrationChecks(){}
    public static final class Run {
        public final UUID owner;
        public final ResourceKey<Level> dimension;
        public final boolean keepInventory;
        private final boolean previousKeep,previousGravity;
        private final GameType previousMode;
        private final String previousEnabled,previousDistance;
        private final Set<BlockPos> previousGraves;
        private final List<ItemStack> expected=new ArrayList<>();
        private final List<String> checks=new ArrayList<>();
        private BlockPos grave;
        private UUID label;
        private List<UUID> model=List.of();
        private int miningStart=-1;
        private boolean completed;
        private Run(ServerPlayer player,boolean keep){
            owner=player.getUUID();dimension=player.level().dimension();keepInventory=keep;
            previousKeep=player.level().getGameRules().get(GameRules.KEEP_INVENTORY);
            previousGravity=player.isNoGravity();previousMode=player.gameMode();
            previousEnabled=UnboxClient.CONFIG.getProperty("gravestones");
            previousDistance=UnboxClient.CONFIG.getProperty("gravestones.nameDistance");
            previousGraves=new HashSet<>(GraveStorage.get(player.level()).graves.keySet());
        }
        public List<String> checks(){return List.copyOf(checks);}
        public BlockPos gravePosition(){return grave;}
        public boolean completed(){return completed;}
    }

    public static Run beginDeath(ServerPlayer player){return begin(player,false);}
    public static Run beginKeepInventoryDeath(ServerPlayer player){return begin(player,true);}
    private static Run begin(ServerPlayer player,boolean keep){
        fixtureThread(player);
        require(player.isAlive(),"begin requires a live fixture player");
        Run run=new Run(player,keep);
        verifyPlantPlacement(player,run);
        UnboxClient.CONFIG.setProperty("gravestones","true");
        UnboxClient.CONFIG.setProperty("gravestones.nameDistance","32");
        player.level().getGameRules().set(GameRules.KEEP_INVENTORY,keep,player.level().getServer());
        player.setGameMode(GameType.SURVIVAL);
        player.getInventory().clearContent();player.containerMenu.setCarried(ItemStack.EMPTY);
        ItemStack diamonds=named(new ItemStack(Items.DIAMOND,7),"Grave fixture diamonds");
        ItemStack armor=named(new ItemStack(Items.DIAMOND_CHESTPLATE),"Grave fixture armor");armor.setDamageValue(23);
        ItemStack cursed=named(new ItemStack(Items.DIAMOND_PICKAXE),"Grave fixture cursed pickaxe");cursed.setDamageValue(47);
        cursed.enchant(player.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.VANISHING_CURSE),1);
        player.getInventory().setItem(9,diamonds);player.getInventory().setItem(10,cursed);
        player.setItemSlot(EquipmentSlot.CHEST,armor);
        player.setItemSlot(EquipmentSlot.OFFHAND,named(new ItemStack(Items.GOLDEN_APPLE,3),"Grave fixture offhand"));
        if(!keep)player.containerMenu.setCarried(named(new ItemStack(Items.EMERALD,5),"Grave fixture cursor"));
        run.expected.addAll(ownedStacks(player));
        player.getInventory().setChanged();player.containerMenu.broadcastFullState();
        player.setHealth(player.getMaxHealth());
        require(player.hurtServer(player.level(),player.damageSources().genericKill(),Float.MAX_VALUE),"lethal damage was accepted");
        require(!player.isAlive(),"real ServerPlayer damage reached death");
        var storage=GraveStorage.get(player.level());
        var created=storage.graves.values().stream().filter(g->!run.previousGraves.contains(g.pos())).toList();
        if(keep){
            require(created.isEmpty(),"keepInventory death creates no grave");
            sameStacks(run.expected,ownedStacks(player),"keepInventory retains inventory and components at death");
            run.checks.add("Actual keepInventory death created no grave and retained all inventory components");
        }else{
            require(created.size()==1,"actual death creates exactly one stone grave");
            var grave=created.getFirst();run.grave=grave.pos();run.label=grave.label();
            require(grave.owner().equals(run.owner),"grave ownership stores player UUID");
            sameStacks(run.expected,grave.items(),"grave preserves counts, names, damage, curse, armor, offhand and cursor");
            require(player.getInventory().isEmpty()&&player.containerMenu.getCarried().isEmpty(),"death inventory clears only after grave storage commit");
            verifyStored(player,run);
            verifyLegacyModelMigration(player,run);
            run.checks.add("Actual lethal damage captured full component-bearing inventory, equipment, cursor and Vanishing item exactly once");
        }
        return run;
    }

    private static void verifyPlantPlacement(ServerPlayer player,Run run){
        var level=player.level();var originalPosition=player.position();var pos=player.blockPosition().above(6);
        var saved=new java.util.LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        for(int dy=-1;dy<=2;dy++)saved.put(pos.above(dy),level.getBlockState(pos.above(dy)));
        try{
            level.setBlockAndUpdate(pos.below(),Blocks.GRASS_BLOCK.defaultBlockState());
            level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(pos.above(2),Blocks.AIR.defaultBlockState());
            player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+.5);
            for(var plant:List.of(Blocks.SHORT_GRASS,Blocks.FERN,Blocks.TALL_GRASS,Blocks.LARGE_FERN)){
                level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(pos,plant.defaultBlockState());
                boolean tall=plant==Blocks.TALL_GRASS||plant==Blocks.LARGE_FERN;
                level.setBlockAndUpdate(pos.above(),tall?plant.defaultBlockState().setValue(
                    net.minecraft.world.level.block.state.properties.BlockStateProperties.DOUBLE_BLOCK_HALF,
                    net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER):Blocks.AIR.defaultBlockState());
                require(pos.equals(GraveKeeper.emptyPlacement(level,player)),"grave replaces "+plant+" at ground level instead of searching above it");
            }
            level.setBlockAndUpdate(pos.above(),Blocks.AIR.defaultBlockState());
            for(var solid:List.of(Blocks.STONE,Blocks.GRASS_BLOCK,Blocks.CHEST,Blocks.WATER,Blocks.LAVA)){
                level.setBlockAndUpdate(pos,solid.defaultBlockState());
                require(!pos.equals(GraveKeeper.emptyPlacement(level,player)),"grave does not overwrite "+solid);
            }
            run.checks.add("Ground-level placement replaces short/tall grass and ferns; stone, soil, chests and fluids remain protected");
        }finally{
            for(var entry:saved.entrySet())level.setBlock(entry.getKey(),entry.getValue(),net.minecraft.world.level.block.Block.UPDATE_ALL);
            player.setPos(originalPosition);
        }
    }

    /** Call after the actual client respawn packet has produced a new living ServerPlayer. */
    public static void verifyRespawnAndBeginMining(ServerPlayer player,Run run)throws Exception{
        fixtureThread(player);require(!run.keepInventory,"use keepInventory verification for this run");
        require(player.isAlive()&&player.getUUID().equals(run.owner),"same UUID has respawned");
        ServerLevel level=player.level().getServer().getLevel(run.dimension);require(level!=null,"death level exists");
        player.teleportTo(level,run.grave.getX()+.5,run.grave.getY(),run.grave.getZ()+3.0,Set.of(),0,0,true);
        player.setNoGravity(true);player.setOnGround(true);
        verifyStored(player,run);verifySavedDataReload(player,run);
        var stranger=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),player.getGameProfile().name()),ClientInformation.createDefault());
        require(!stranger.gameMode.destroyBlock(run.grave),"same-name non-owner cannot mine grave through game mode mixin");
        require(GraveKeeper.isGrave(level,run.grave),"denied break keeps stored grave");
        require(!level.destroyBlock(run.grave,true),"generic block destruction cannot drop or erase grave");
        require(!level.setBlockAndUpdate(run.grave,Blocks.AIR.defaultBlockState()),"replacement cannot erase grave");
        require(!PistonBaseBlock.isPushable(level.getBlockState(run.grave),level,run.grave,Direction.EAST,false,Direction.EAST),"piston cannot move grave");
        var label=level.getEntity(run.label);require(label instanceof Display.TextDisplay,"nameplate exists after respawn");
        require(((Component)read(label,"getText")).getString().equals(player.getGameProfile().name()),"nameplate contains deceased profile name");
        UnboxClient.CONFIG.setProperty("gravestones.nameDistance","64");GraveKeeper.updateLabel((Display)label);
        require(Math.abs((float)readDisplay((Display)label,"getViewRange")-1f)<.0001f,"existing label receives configured 64-block view range");
        run.checks.add("Non-owner UUID, replacement, generic destruction and piston are denied; existing nameplate responds to settings");

        player.getInventory().clearContent();player.containerMenu.setCarried(ItemStack.EMPTY);
        player.setItemSlot(EquipmentSlot.MAINHAND,named(new ItemStack(Items.DIAMOND_PICKAXE),"Grave fixture mining tool"));
        float actual=level.getBlockState(run.grave).getDestroyProgress(player,level,run.grave);
        float stone=Blocks.STONE.defaultBlockState().getDestroyProgress(player,level,run.grave);
        require(actual>0&&actual<.7f&&Float.compare(actual,stone)==0,"grave uses vanilla stone mining hardness and tool progress");
        require(player.isWithinBlockInteractionRange(run.grave,1),"grave is within native mining reach");
        require(!player.blockActionRestricted(level,run.grave,player.gameMode.getGameModeForPlayer())
            &&level.mayInteract(player,run.grave)
            &&player.getMainHandItem().canDestroyBlock(level.getBlockState(run.grave),level,run.grave,player),"owner and tool pass native grave mining permissions");
        // Disabling future creation must not strand existing stored items.
        UnboxClient.CONFIG.setProperty("gravestones","false");
        run.miningStart=level.getServer().getTickCount();
        player.gameMode.handleBlockBreakAction(run.grave,ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,Direction.UP,level.getMaxY(),1);
        player.gameMode.handleBlockBreakAction(run.grave,ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,Direction.UP,level.getMaxY(),2);
        require(miningField(player,"hasDelayedDestroy").equals(true),"early STOP entered native delayed mining");
        require(GraveKeeper.isGrave(level,run.grave),"early stop cannot instantly mine a stone grave");
    }

    /** Poll on the server thread, at most once per real tick; never manually advances gameMode.tick. */
    public static boolean advanceMining(ServerPlayer player,Run run){
        fixtureThread(player);require(run.miningStart>=0,"mining must be started first");
        if(run.completed)return true;
        var level=player.level();int elapsed=level.getServer().getTickCount()-run.miningStart;
        if(elapsed>=300)require(false,"native stone mining timer timed out; "+miningState(player));
        if(GraveKeeper.isGrave(level,run.grave))return false;
        require(elapsed>0,"grave was not mined instantly");
        require(level.getBlockState(run.grave).isAir(),"completed mining removes the invisible anchor");
        require(run.model.stream().allMatch(id->level.getEntity(id)==null||level.getEntity(id).isRemoved()),"completed mining removes every model display");
        require(level.getEntity(run.label)==null||level.getEntity(run.label).isRemoved(),"completed mining removes nameplate");
        var drops=recoveredDrops(level,run.grave,run.owner);
        sameStacks(run.expected,drops.stream().map(ItemEntity::getItem).toList(),"mining drops exact stored contents, no stone or duplicates");
        require(drops.stream().noneMatch(ItemEntity::isNoGravity),"new recovered items have normal gravity");
        int before=drops.stream().mapToInt(e->e.getItem().getCount()).sum();
        var stranger=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),player.getGameProfile().name()),ClientInformation.createDefault());
        drops.forEach(e->e.playerTouch(stranger));
        require(before==drops.stream().mapToInt(e->e.getItem().getCount()).sum(),"same-name non-owner cannot pick up recovered items");
        require(!GraveKeeper.mine(player,run.grave),"second claim cannot duplicate contents");
        // Drive the normal owner pickup method, leaving the server-authoritative inventory result visible.
        for(var drop:drops){drop.setNoPickUpDelay();drop.playerTouch(player);}
        sameStacks(run.expected,ownedStacks(player).stream().filter(s->!s.getHoverName().getString().equals("Grave fixture mining tool")).toList(),"owner receives exact component-bearing stacks");
        require(drops.stream().noneMatch(ItemEntity::isAlive),"all recovered stacks were claimed");
        run.checks.add("Native server mining timer completed in "+elapsed+" ticks; all stored stacks dropped with gravity, owner retrieved them, anchor, model and label were removed");
        run.completed=true;return true;
    }

    private static Object miningField(ServerPlayer player,String name){
        try{var field=net.minecraft.server.level.ServerPlayerGameMode.class.getDeclaredField(name);field.setAccessible(true);return field.get(player.gameMode);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot inspect fixture mining state "+name,e);}
    }
    private static String miningState(ServerPlayer player){
        return "ticks="+miningField(player,"gameTicks")+", delayed="+miningField(player,"hasDelayedDestroy")+", start="+miningField(player,"delayedTickStart")+", pos="+miningField(player,"delayedDestroyPos");
    }

    public static void verifyKeepInventoryRespawn(ServerPlayer player,Run run){
        fixtureThread(player);require(run.keepInventory&&player.isAlive(),"keepInventory fixture respawned");
        require(player.getUUID().equals(run.owner),"respawn keeps owner UUID");
        sameStacks(run.expected,ownedStacks(player),"actual keepInventory respawn retains exact stacks and components");
        require(GraveStorage.get(player.level()).graves.keySet().equals(run.previousGraves),"keepInventory creates no delayed grave");
        run.checks.add("Actual keepInventory respawn retained inventory, armor, offhand, damage, names and curse");run.completed=true;
    }

    /** Fresh SavedDataStorage cache reads the real dimension data file after Minecraft saves it. */
    public static void verifySavedDataReload(ServerPlayer player,Run run)throws Exception{
        fixtureThread(player);ServerLevel level=player.level().getServer().getLevel(run.dimension);
        require(level!=null&&run.grave!=null,"stored grave available for disk round trip");
        level.getDataStorage().saveAndJoin();
        var directory=DimensionType.getStorageFolder(run.dimension,level.getServer().getWorldPath(LevelResource.ROOT)).resolve("data");
        try(var reopened=new SavedDataStorage(directory,DataFixers.getDataFixer(),level.registryAccess())){
            var reloaded=reopened.get(GraveStorage.TYPE);require(reloaded!=null,"fresh SavedDataStorage read persisted dimension file");
            var grave=reloaded.graves.get(run.grave);require(grave!=null,"grave survives saved data cache reload");
            require(grave.owner().equals(run.owner)&&grave.label().equals(run.label),"saved UUID owner and label round-trip");
            require(grave.model().equals(run.model),"saved model UUIDs round-trip without replacement");
            sameStacks(run.expected,grave.items(),"disk reload retains complete ItemStack components");
        }
        run.checks.add("Fresh SavedDataStorage instance reloaded actual dimension file with exact UUIDs and ItemStack components (not a full world restart)");
    }

    /** The manifest records the pre-death expectation, independently of the saved grave contents. */
    public static void writePersistenceManifest(ServerPlayer player,Run run,Path path)throws Exception{
        fixtureThread(player);require(!run.keepInventory&&run.grave!=null,"only an unclaimed death grave may be checkpointed");
        verifyStored(player,run);
        var ops=RegistryOps.create(JsonOps.INSTANCE,player.registryAccess());
        var expected=new GraveStorage.Grave(run.grave,run.owner,player.getGameProfile().name(),run.label,run.expected,run.model);
        var json=new JsonObject();json.addProperty("format",1);json.addProperty("writePid",ProcessHandle.current().pid());
        json.addProperty("world",player.level().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString());
        json.addProperty("dimension",run.dimension.identifier().toString());
        json.add("expected",GraveStorage.Grave.CODEC.encodeStart(ops,expected).getOrThrow());
        Files.createDirectories(path.getParent());Files.writeString(path,json.toString());
    }

    /** Only reads the prior process's manifest, then requests its dimension/chunk for normal loading. */
    public static Run readPersistenceManifest(ServerPlayer player,Path path)throws Exception{
        fixtureThread(player);var json=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        require(json.get("format").getAsInt()==1,"supported grave manifest format");
        require(json.get("writePid").getAsLong()!=ProcessHandle.current().pid(),"persistence read runs in a different JVM process");
        require(json.get("world").getAsString().equals(player.level().getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().toString()),"read process opened the same fixture world");
        var dimension=ResourceKey.create(Registries.DIMENSION,Identifier.parse(json.get("dimension").getAsString()));
        var level=player.level().getServer().getLevel(dimension);require(level!=null,"checkpoint dimension exists after restart");
        var ops=RegistryOps.create(JsonOps.INSTANCE,player.registryAccess());
        var expected=GraveStorage.Grave.CODEC.parse(ops,json.get("expected")).getOrThrow();
        require(player.getUUID().equals(expected.owner()),"restarted fixture retains the same owner UUID");
        player.teleportTo(level,expected.pos().getX()+.5,expected.pos().getY(),expected.pos().getZ()+3.0,Set.of(),0,0,true);
        var run=new Run(player,false);run.grave=expected.pos();run.label=expected.label();run.model=expected.model();
        player.setGameMode(GameType.SURVIVAL);
        expected.items().forEach(stack->run.expected.add(stack.copy()));
        level.getChunkAt(run.grave);
        run.checks.add("Manifest originated in a different JVM and identifies the same fixture world, dimension and owner UUID");
        return run;
    }
    public static boolean reloadedWorldReady(ServerPlayer player,Run run){
        fixtureThread(player);var level=player.level().getServer().getLevel(run.dimension);
        return level!=null&&level.getEntity(run.label) instanceof Display.TextDisplay
            &&run.model.stream().allMatch(id->level.getEntity(id) instanceof Display);
    }

    /** Also usable after a caller-driven full save/close/reopen, once the grave chunk's entities load. */
    public static void verifyReloadedWorld(ServerPlayer player,Run run){fixtureThread(player);verifyStored(player,run);}
    public static void finish(ServerPlayer player,Run run){
        fixtureThread(player);var level=player.level().getServer().getLevel(run.dimension);
        if(level!=null)level.getGameRules().set(GameRules.KEEP_INVENTORY,run.previousKeep,level.getServer());
        restore("gravestones",run.previousEnabled);restore("gravestones.nameDistance",run.previousDistance);
        player.setNoGravity(run.previousGravity);player.setGameMode(run.previousMode);
    }
    private static void verifyStored(ServerPlayer player,Run run){
        ServerLevel level=player.level().getServer().getLevel(run.dimension);require(level!=null,"grave dimension exists");
        level.getChunkAt(run.grave);var grave=GraveStorage.get(level).graves.get(run.grave);
        require(grave!=null&&level.getBlockState(run.grave).is(Blocks.STRUCTURE_VOID),"stored grave uses an invisible vanilla anchor");
        require(grave.model().size()==GraveModel.PART_COUNT&&new HashSet<>(grave.model()).size()==GraveModel.PART_COUNT,"model has three distinct vanilla display UUIDs");
        if(run.model.isEmpty())run.model=grave.model();
        else require(run.model.equals(grave.model()),"model UUIDs stay stable across load and repeated ensure calls");
        require(grave.model().stream().allMatch(id->level.getEntity(id) instanceof Display),"all saved model parts are loaded vanilla displays");
        require(GraveModel.isGraveAnchor(level,run.grave),"model is linked to authoritative grave anchor");
        require(grave.model().stream().filter(id->level.getEntity(id) instanceof Display.BlockDisplay).count()==1,"exactly one stone rectangle and no plinth or cap");
        require(grave.model().stream().filter(id->level.getEntity(id) instanceof Display.TextDisplay text &&
            ((dev.unbox.client.mixin.GraveTextDisplayAccess)text).unbox$getText().getString().equals("R.I.P")).count()==2,"both faces carry the exact R.I.P inscription");
        verifyModelShape(level,run.grave);
        sameStacks(run.expected,grave.items(),"stored contents remain exact");
        require(level.getEntity(run.label) instanceof Display.TextDisplay,"stored grave has a loaded text display");
        require(recoveredDrops(level,run.grave,run.owner).isEmpty(),"unmined grave exposes no loot entities");
    }
    /** Works on either side: real block-state shape calls must match the visible tablet, including empty sides. */
    static void verifyModelShape(Level level,BlockPos pos){
        var state=level.getBlockState(pos);
        var front=new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+.75,pos.getZ()+2);
        var back=new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+.75,pos.getZ()-1);
        var shapes=List.of(state.getShape(level,pos),state.getCollisionShape(level,pos),
            state.getCollisionShape(level,pos,net.minecraft.world.phys.shapes.CollisionContext.empty()));
        for(var shape:shapes){
            require(shape.bounds().equals(new AABB(0,0,.375,1,1,.625)),"tablet is exactly one block wide/high and one quarter block thick");
            require(shape.clip(new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()+2,pos.getZ()+.1),
                new net.minecraft.world.phys.Vec3(pos.getX()+.5,pos.getY()-1,pos.getZ()+.1),pos)==null,"no base protrudes in front of the thin tablet");
            var hit=shape.clip(front,back,pos);
            require(hit!=null&&hit.getBlockPos().equals(pos),"upper tablet is selectable and solid at the grave anchor");
            require(shape.clip(front.add(-.6,0,0),back.add(-.6,0,0),pos)==null,"empty space beside tablet does not block or select");
            require(shape.clip(front.add(0,.18,0),back.add(0,.18,0),pos)!=null,"full-height rectangle is selectable near its top");
        }
        require(!level.noCollision(new AABB(pos.getX()+.4,pos.getY()+.6,pos.getZ()+.4,pos.getX()+.6,pos.getY()+.9,pos.getZ()+.6)),"world collision traversal includes the upper tablet");
        require(level.noCollision(new AABB(pos.getX()+.4,pos.getY()+.1,pos.getZ()+.05,pos.getX()+.6,pos.getY()+.4,pos.getZ()+.2)),"world collision traversal leaves the former plinth space empty");
        var ordinary=Blocks.STONE_SLAB.defaultBlockState();var other=pos.offset(3,0,0);
        require(ordinary.getShape(level,other).clip(front.add(3,0,0),back.add(3,0,0),other)==null,"ordinary slabs keep their original half-height selection");
        require(Float.compare(ordinary.getDestroySpeed(level,other),2f)==0,"ordinary slabs keep their original hardness");
    }
    /** Reconstructs the old schema in this disposable grave, then exercises the actual load migration. */
    private static void verifyLegacyModelMigration(ServerPlayer player,Run run){
        var level=player.level();var storage=GraveStorage.get(level);var current=storage.graves.get(run.grave);
        var ops=RegistryOps.create(JsonOps.INSTANCE,player.registryAccess());
        var legacyJson=GraveStorage.Grave.CODEC.encodeStart(ops,current).getOrThrow().getAsJsonObject();
        legacyJson.remove("model");
        var legacy=GraveStorage.Grave.CODEC.parse(ops,legacyJson).getOrThrow();
        require(legacy.model().isEmpty(),"old grave schema decodes without model UUIDs");
        GraveModel.remove(level,current);
        storage.graves.put(run.grave,legacy);storage.removing.add(run.grave);
        try{require(level.setBlockAndUpdate(run.grave,Blocks.STONE.defaultBlockState()),"fixture restores legacy stone anchor");}
        finally{storage.removing.remove(run.grave);}
        var label=(Display)level.getEntity(run.label);label.setPos(run.grave.getX()+.5,run.grave.getY()+1.3,run.grave.getZ()+.5);
        GraveModel.ensure(level,label);run.model=List.of();verifyStored(player,run);
        var migrated=storage.graves.get(run.grave);
        GraveModel.ensure(level,label);GraveKeeper.updateLabel(label);
        require(storage.graves.get(run.grave).model().equals(migrated.model()),"repeat model update never duplicates displays");
        require(level.getEntitiesOfClass(Display.class,new AABB(run.grave).inflate(.1),e->e.entityTags().contains(GraveModel.PART)).size()==GraveModel.PART_COUNT,"legacy migration leaves exactly one model");
        sameStacks(run.expected,migrated.items(),"legacy migration retains exact items and components");
        require(migrated.owner().equals(run.owner)&&migrated.label().equals(run.label),"legacy migration retains owner and label identity");
        run.checks.add("Old grave schema migrated STONE to a thin R.I.P tablet; repeated updates kept stable UUIDs and exact contents");
        // The immediately previous release persisted six pieces on a stone-slab base.
        var oldParts=new ArrayList<UUID>();
        for(int i=0;i<6;i++){
            var part=new Display.BlockDisplay(net.minecraft.world.entity.EntityType.BLOCK_DISPLAY,level);
            part.setPos(run.grave.getX()+.5,run.grave.getY(),run.grave.getZ()+.5);part.addTag(GraveModel.PART);
            require(level.addFreshEntity(part),"create previous-model migration fixture");oldParts.add(part.getUUID());
        }
        GraveModel.remove(level,migrated);
        storage.graves.put(run.grave,new GraveStorage.Grave(run.grave,run.owner,migrated.name(),run.label,migrated.items(),oldParts));
        storage.removing.add(run.grave);
        try{require(level.setBlockAndUpdate(run.grave,Blocks.STONE_SLAB.defaultBlockState()),"restore previous-model slab fixture");}
        finally{storage.removing.remove(run.grave);}
        GraveModel.ensure(level,label);run.model=List.of();verifyStored(player,run);
        require(oldParts.stream().allMatch(id->level.getEntity(id)==null||level.getEntity(id).isRemoved()),"conversion removes all six old model entities");
        run.checks.add("Previous slab-and-six-parts grave converted to the thin tablet without losing contents or leaving its base/model behind");
    }
    private static List<ItemEntity> recoveredDrops(ServerLevel level,BlockPos pos,UUID owner){
        return level.getEntities((net.minecraft.world.entity.Entity)null,new AABB(pos).inflate(5),e->e instanceof ItemEntity&&e.entityTags().contains("unbox.grave_recovered")&&e.entityTags().contains("unbox.owner."+owner)).stream().map(ItemEntity.class::cast).toList();
    }
    private static List<ItemStack> ownedStacks(ServerPlayer player){
        var result=new ArrayList<ItemStack>();for(int i=0;i<player.getInventory().getContainerSize();i++){var stack=player.getInventory().getItem(i);if(!stack.isEmpty())result.add(stack.copy());}
        if(!player.containerMenu.getCarried().isEmpty())result.add(player.containerMenu.getCarried().copy());return result;
    }
    private static void sameStacks(List<ItemStack> expected,List<ItemStack> actual,String message){
        var remaining=new ArrayList<ItemStack>();for(var stack:actual)if(!stack.isEmpty())remaining.add(stack.copy());
        for(var stack:expected){int count=stack.getCount();for(var candidate:remaining){if(ItemStack.isSameItemSameComponents(stack,candidate)){int take=Math.min(count,candidate.getCount());count-=take;candidate.shrink(take);}}require(count==0,message+" (missing "+stack+")");}
        require(remaining.stream().allMatch(ItemStack::isEmpty),message+" (unexpected remaining stacks)");
    }
    private static ItemStack named(ItemStack stack,String name){stack.set(DataComponents.CUSTOM_NAME,Component.literal(name));return stack;}
    private static Object read(Object target,String name)throws Exception{Method method=target.getClass().getDeclaredMethod(name);method.setAccessible(true);return method.invoke(target);}
    private static Object readDisplay(Display target,String name)throws Exception{Method method=Display.class.getDeclaredMethod(name);method.setAccessible(true);return method.invoke(target);}
    private static void restore(String key,String value){if(value==null)UnboxClient.CONFIG.remove(key);else UnboxClient.CONFIG.setProperty(key,value);}
    private static void fixtureThread(ServerPlayer player){
        String persistence=System.getProperty("unbox.gravePersistence","");
        require(Boolean.getBoolean("unbox.survivalSmoke")||Boolean.getBoolean("unbox.survivalIntegration")||persistence.equals("write")||persistence.equals("read"),"explicit isolated survival fixture flag is required");
        require(player.level().getServer().isSameThread(),"grave checks must run on server thread");
        require(player.level().getServer().isSingleplayer(),"grave checks require integrated disposable world");
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("Grave integration: "+message);}
}
