package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Explicit disposable-world checks. Call begin and tick on the client thread; never join the future there. */
public final class NativeModIntegrationChecks {
    private static Run active;
    private NativeModIntegrationChecks(){}
    public static CompletableFuture<List<String>> begin(Minecraft mc){
        if(active!=null)return CompletableFuture.failedFuture(new IllegalStateException("Native integration checks already running"));
        if(!Boolean.getBoolean("unbox.survivalSmoke")||mc.getSingleplayerServer()==null||mc.player==null)
            return CompletableFuture.failedFuture(new IllegalStateException("Native checks require the opt-in disposable integrated world"));
        active=new Run(mc);return active.result;
    }
    public static void tick(Minecraft mc){if(active!=null)active.tick(mc);}
    @FunctionalInterface private interface Work{void run()throws Exception;}
    private static final class Run{
        final CompletableFuture<List<String>> result=new CompletableFuture<>();
        final Properties saved=new Properties();final List<String> checks=new ArrayList<>();
        final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();final List<ItemStack> inventory=new ArrayList<>(),sortBefore=new ArrayList<>();
        final Screen previousScreen;final UUID playerId;final Minecraft mc;
        CompletableFuture<Void> pending;int stage,wait,ticks;boolean cleaning,fixtureReady;
        ServerPlayer player;GameType mode;boolean originalShift;BlockPos ore,tree;Object treeConfig,oldBreakMode;Throwable failure;
        Run(Minecraft mc){this.mc=mc;previousScreen=mc.screen;playerId=mc.player.getUUID();saved.putAll(UnboxClient.CONFIG);mc.setScreen(null);}
        void tick(Minecraft minecraft){
            if(cleaning){if(pending!=null&&pending.isDone()){try{pending.join();}catch(Throwable e){if(failure==null)failure=e;else failure.addSuppressed(e);}complete();}return;}
            try{
                if(++ticks>900)throw new IllegalStateException("Native integration checks timed out at stage "+stage);
                if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
                if(wait>0){wait--;return;}
                switch(stage++){
                    case 0->{clientSettings();configureHost(false);server(this::prepare);wait=30;}
                    case 1->{server(()->{hostSettings(false);placeFixtures();breakFixtures();});wait=40;}
                    case 2->{server(()->{require(countOre()==3,"disabled VeinMiner preserves connected ores");require(countLogs()==3,"disabled FallingTree preserves upper logs");checks.add("Disabled native engines leave connected ore and tree blocks intact after real destroyBlock events");});configureHost(true);wait=30;}
                    case 3->{veinToggleChecks();server(()->{hostSettings(true);placeFixtures();breakFixtures();});wait=60;}
                    case 4->{server(()->{require(countOre()==(Platform.loaded("veinminer")?0:3),"VeinMiner connected ore result matches native availability; remaining="+countOre());require(countLogs()==0,"enabled FallingTree removes the tree fixture; remaining="+countLogs());checks.add(Platform.loaded("veinminer")?"Enabled native VeinMiner and FallingTree execute through ServerPlayer.gameMode.destroyBlock and registered loader events":"Enabled native FallingTree executes through registered loader events; unsupported VeinMiner remains inactive and preserves connected ores");});}
                    case 5->{configureHost(false);if(Platform.loaded("inventoryprofilesnext")){UnboxClient.CONFIG.setProperty("inventory","true");for(String key:List.of("refill","lockSlots","profiles"))UnboxClient.CONFIG.setProperty("inventory."+key,"false");SurvivalClientMods.sync();server(this::prepareInventory);wait=15;}else{require(Platform.loader().equals("neoforge")&&Platform.mod("minecraft").version().equals("26.1"),"IPN absent only on the explicitly unsupported native target");checks.add("NeoForge 26.1 explicitly reports unsupported IPN and does not load incompatible artifacts");stage=8;}}
                    case 6->{mc.setScreen(new InventoryScreen(mc.player));require(SurvivalClientMods.sortInventory(),"real IPN sortInventory dispatch");wait=40;}
                    case 7->{server(this::verifyInventory);}
                    case 8->cleanup(null);
                    default->throw new IllegalStateException("Unexpected native check stage");
                }
            }catch(Throwable error){cleanup(error);}
        }
        void server(Work work){pending=new CompletableFuture<>();CompletableFuture<Void> task=pending;mc.getSingleplayerServer().execute(()->{try{work.run();task.complete(null);}catch(Throwable e){task.completeExceptionally(e);}});}
        void veinToggleChecks()throws Exception{
            if(!Platform.loaded("veinminer_client"))return;
            var key=SurvivalHostMods.key();require(key!=null,"native toggle key exists");
            Object manager=singleton("de.miraculixx.veinminerClient.KeyBindManager");
            var input=com.mojang.blaze3d.platform.InputConstants.getKey(key.saveString());
            call(manager,"onDisconnect");
            net.minecraft.client.KeyMapping.set(input,true);net.minecraft.client.KeyMapping.click(input);call(manager,"tick");
            require((boolean)call(manager,"isPressed"),"first press activates native vein mode");
            net.minecraft.client.KeyMapping.set(input,false);call(manager,"tick");
            require((boolean)call(manager,"isPressed"),"native mining remains active after releasing key");
            net.minecraft.client.KeyMapping.set(input,true);net.minecraft.client.KeyMapping.click(input);call(manager,"tick");
            require(!(boolean)call(manager,"isPressed"),"second press disables native mining while key remains held");
            net.minecraft.client.KeyMapping.set(input,false);
            net.minecraft.client.KeyMapping.click(input);call(manager,"tick");
            UnboxClient.set("veinminer",false);call(manager,"tick");UnboxClient.set("veinminer",true);call(manager,"tick");
            require(!(boolean)call(manager,"isPressed"),"host disable clears the native toggled mode");
            var prior=mc.screen;mc.setScreen(new InventoryScreen(mc.player));
            try{net.minecraft.client.KeyMapping.click(input);call(manager,"tick");}finally{mc.setScreen(prior);}
            call(manager,"tick");require(!(boolean)call(manager,"isPressed"),"inventory key presses do not activate mining after closing");
            call(manager,"onDisconnect");
            checks.add("Real loaded VeinMiner mixins toggle on press, stay active on release, turn off on second press, reset on host disable, and discard inventory-screen clicks");
        }
        void clientSettings()throws Exception{
            require(Platform.loaded("itemphysiclite"),"ItemPhysic loaded");
            if(!Platform.loaded("veinminer")){
                require(Platform.loader().equals("neoforge")&&Platform.mod("minecraft").version().equals("26.1"),"VeinMiner absent only on the explicitly unsupported native target");
                require(!Platform.loaded("veinminer_client"),"unsupported VeinMiner does not leave its dependent Hotkey loaded");
                require(!SurvivalHostMods.available("veinminer")&&SurvivalHostMods.reason("veinminer").contains("unavailable"),"unsupported VeinMiner has an explicit availability reason");
                checks.add("NeoForge 26.1 explicitly reports unavailable VeinMiner and omits its incompatible engine and dependent Hotkey");
            }
            UnboxClient.CONFIG.setProperty("items","true");UnboxClient.CONFIG.setProperty("items.oldRotation","true");UnboxClient.CONFIG.setProperty("items.rotationSpeed","175");SurvivalClientMods.sync();
            Object item=Class.forName("team.creative.itemphysiclite.ItemPhysicLite").getField("CONFIG").get(null);
            require(item.getClass().getField("oldRotation").getBoolean(item),"ItemPhysic live rotation style");require(Math.abs(item.getClass().getField("rotateSpeed").getFloat(item)-1.75f)<.001,"ItemPhysic live speed");
            verifyItemSubmission();
            UnboxClient.CONFIG.setProperty("items","false");require(!SurvivalClientMods.itemsActive(),"ItemPhysic disabled gate");Method submit=Arrays.stream(Class.forName("team.creative.itemphysiclite.ItemPhysicLite").getMethods()).filter(m->m.getName().equals("submit")).findFirst().orElseThrow();require(Boolean.FALSE.equals(submit.invoke(null,new Object[submit.getParameterCount()])),"disabled ItemPhysic submit returns before accessing render arguments");
            checks.add("ItemPhysic upstream CONFIG receives live style/speed changes and the Unbox gate disables it");
            if(Platform.loaded("inventoryprofilesnext")){
                UnboxClient.CONFIG.setProperty("inventory","true");for(String key:List.of("refill","lockSlots","profiles","scrolling","highlight","buttons"))UnboxClient.CONFIG.setProperty("inventory."+key,"true");SurvivalClientMods.sync();
                for(String key:List.of("ENABLE_SORTING_AND_MOVING","ENABLE_AUTO_REFILL","ENABLE_LOCK_SLOTS","ENABLE_PROFILES","ITEM_SCROLLING","HIGHLIGHT_FOUSED_ITEMS"))require(ipn("Features",key),"IPN enables "+key);
                require(ipn("GuiSettings","ENABLE_INVENTORY_BUTTONS"),"IPN enables inventory buttons");
                UnboxClient.CONFIG.setProperty("inventory","false");SurvivalClientMods.sync();
                for(String key:List.of("ENABLE_SORTING_AND_MOVING","ENABLE_AUTO_REFILL","ENABLE_LOCK_SLOTS","ENABLE_PROFILES","ITEM_SCROLLING","HIGHLIGHT_FOUSED_ITEMS"))require(!ipn("Features",key),"IPN disables "+key);
                require(!ipn("GuiSettings","ENABLE_INVENTORY_BUTTONS"),"IPN hides inventory buttons");checks.add("IPN real upstream feature and GUI settings follow both enabled and disabled states");
            }
        }
        void verifyItemSubmission()throws Exception{
            require(SurvivalClientMods.itemsActive(),"ItemPhysic enabled gate");
            Method submit=Class.forName("team.creative.itemphysiclite.ItemPhysicLite").getMethod("submit",ItemEntityRenderState.class,PoseStack.class,SubmitNodeCollector.class,CameraRenderState.class,RandomSource.class);
            // Use the loaded renderer/model resolver and upstream extraction mixin, not a fabricated render state.
            // These isolated entities never enter the world, so they cannot change inventory or get picked up.
            for(var kind:List.of(Items.APPLE,Items.STONE)){
                ItemEntity entity=new ItemEntity(mc.level,mc.player.getX(),mc.player.getY(),mc.player.getZ(),new ItemStack(kind,3));entity.tickCount=20;
                ItemEntityRenderState state=(ItemEntityRenderState)mc.getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity,1);
                require(state.ageInTicks>=1&&!state.item.isEmpty(),"ItemPhysic fixture has an aged entity and a resolved item model");
                int[] submissions={0};
                SubmitNodeCollector collector=(SubmitNodeCollector)Proxy.newProxyInstance(SubmitNodeCollector.class.getClassLoader(),new Class<?>[]{SubmitNodeCollector.class},(proxy,method,args)->{
                    if(method.getName().equals("order"))return proxy;
                    if(method.getName().equals("submitItem")){
                        require(args[6] instanceof List<?> quads&&!quads.isEmpty(),"ItemPhysic submits nonempty baked model geometry");submissions[0]++;
                    }
                    if(method.getDeclaringClass()==Object.class)return switch(method.getName()){case "toString"->"NativeItemSubmissionCollector";case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];default->null;};
                    return null;
                });
                require(Boolean.TRUE.equals(submit.invoke(null,state,new PoseStack(),collector,new CameraRenderState(),RandomSource.create())),"enabled ItemPhysic handles the real extracted render state");
                require(submissions[0]>0,"enabled ItemPhysic reaches actual model submission for "+kind);
            }
            checks.add("Enabled ItemPhysic submits nonempty real APPLE and STONE model geometry through upstream extraction and rendering; both flat and block items are checked");
        }
        void configureHost(boolean enabled){
            for(String key:List.of("veinminer","fallingtree"))UnboxClient.CONFIG.setProperty(key,Boolean.toString(enabled));
            UnboxClient.CONFIG.setProperty("veinminer.maxChain","8");UnboxClient.CONFIG.setProperty("veinminer.cooldown","0");UnboxClient.CONFIG.setProperty("veinminer.mustSneak","false");UnboxClient.CONFIG.setProperty("veinminer.correctTool","true");
            UnboxClient.CONFIG.setProperty("fallingtree.maxSize","32");UnboxClient.CONFIG.setProperty("fallingtree.leaves","true");UnboxClient.CONFIG.setProperty("fallingtree.requireSneak","false");
            SurvivalHostMods.tick(mc);
        }
        void hostSettings(boolean enabled)throws Exception{
            require(SurvivalHostMods.available("fallingtree"),SurvivalHostMods.reason("fallingtree"));
            require(SurvivalHostMods.serverEnabled("veinminer")== (enabled&&Platform.loaded("veinminer")),"VeinMiner host gate follows enabled state and native availability");require(SurvivalHostMods.serverEnabled("fallingtree")==enabled,"FallingTree host gate="+enabled);
            if(Platform.loaded("veinminer")){
            require(SurvivalHostMods.available("veinminer"),SurvivalHostMods.reason("veinminer"));
            Object settings=call(singleton("de.miraculixx.veinminer.config.ConfigManager"),"getSettings");require(((Number)call(settings,"getMaxChain")).intValue()==8,"VeinMiner live maxChain");require(((Number)call(settings,"getCooldown")).intValue()==0,"VeinMiner live cooldown");require(!(boolean)call(settings,"getMustSneak"),"VeinMiner live sneak flag");require((boolean)call(settings,"getNeedCorrectTool"),"VeinMiner live tool flag");
            Object host=call(singleton("de.miraculixx.veinminer.command.ActiveHost"),"getHost");require((boolean)Class.forName("de.miraculixx.veinminer.command.VeinminerHost").getMethod("getActive").invoke(host)==enabled,"VeinMiner upstream host activation");
            }
            Object trees=call(treeConfig,"getTrees");require((boolean)call(trees,"isTreeBreaking")==enabled,"FallingTree live tree flag");require((boolean)call(trees,"isLeavesBreaking")==enabled,"FallingTree live leaves flag");require(((Number)call(trees,"getMaxSize")).intValue()==32,"FallingTree live maxSize");require(call(treeConfig,"getSneakMode").toString().equals("SNEAK_DISABLE"),"FallingTree live sneak mode");
        }
        void prepare()throws Exception{
            player=mc.getSingleplayerServer().getPlayerList().getPlayer(playerId);require(player!=null,"native fixture server player");mode=player.gameMode();originalShift=player.isShiftKeyDown();for(int i=0;i<player.getInventory().getContainerSize();i++)inventory.add(player.getInventory().getItem(i).copy());
            player.setGameMode(GameType.SURVIVAL);player.setShiftKeyDown(false);
            int y=Math.min(player.level().getMaxY()-12,Math.max(130,player.blockPosition().getY()+24));ore=new BlockPos(player.blockPosition().getX()+8,y,player.blockPosition().getZ());tree=ore.offset(0,0,6);
            // Only an empty, bounded fixture area is eligible; no block entity/state is overwritten.
            for(int x=-3;x<=4;x++)for(int z=-1;z<=9;z++)for(int h=-1;h<=6;h++){BlockPos pos=ore.offset(x,h,z);require(player.level().getBlockState(pos).isAir(),"fixture area must be empty: "+pos);blocks.put(pos,player.level().getBlockState(pos));}
            Object mod=Class.forName("fr.rakambda.fallingtree."+(Platform.loader().equals("neoforge")?"neoforge":"fabric")+".FallingTree").getMethod("getMod").invoke(null);treeConfig=call(mod,"getOwnConfiguration");Object trees=call(treeConfig,"getTrees");oldBreakMode=call(trees,"getBreakMode");Class<?> type=oldBreakMode.getClass();trees.getClass().getMethod("setBreakMode",type).invoke(trees,type.getField("INSTANTANEOUS").get(null));fixtureReady=true;
        }
        void placeFixtures(){
            for(var pos:blocks.keySet())player.level().setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            for(int i=0;i<4;i++)player.level().setBlock(ore.offset(i,0,0),Blocks.DIAMOND_ORE.defaultBlockState(),3);
            player.level().setBlock(tree.below(),Blocks.DIRT.defaultBlockState(),3);
            for(int h=0;h<4;h++)player.level().setBlock(tree.above(h),Blocks.OAK_LOG.defaultBlockState(),3);
            BlockState leaves=Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.DISTANCE,1).setValue(BlockStateProperties.PERSISTENT,false);
            for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)for(int h=3;h<=4;h++){BlockPos pos=tree.offset(x,h,z);if(player.level().getBlockState(pos).isAir())player.level().setBlock(pos,leaves,3);}
        }
        @SuppressWarnings("unchecked") void breakFixtures()throws Exception{
            player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_PICKAXE));
            // Exercise the documented server-only ore path, not synthetic ready-key state.
            if(Platform.loaded("veinminer")){
            Object router=singleton("de.miraculixx.veinminer.network.NetworkRouter");Map<UUID,Object> registered=(Map<UUID,Object>)call(router,"getRegisteredPlayers");boolean registeredBefore=registered.containsKey(playerId);Object registration=registered.remove(playerId);
            try{player.gameMode.destroyBlock(ore);}finally{if(registeredBefore)registered.put(playerId,registration);}
            }else player.gameMode.destroyBlock(ore);
            require(player.level().getBlockState(ore).isAir(),"real ore origin break");
            player.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.DIAMOND_AXE));player.gameMode.destroyBlock(tree);require(player.level().getBlockState(tree).isAir(),"real tree origin break");
        }
        int countOre(){int count=0;for(int i=0;i<4;i++)if(player.level().getBlockState(ore.offset(i,0,0)).is(Blocks.DIAMOND_ORE))count++;return count;}
        int countLogs(){int count=0;for(int i=0;i<4;i++)if(player.level().getBlockState(tree.above(i)).is(Blocks.OAK_LOG))count++;return count;}
        void prepareInventory(){
            player.getInventory().clearContent();sortBefore.clear();var kinds=new net.minecraft.world.item.Item[]{Items.DIRT,Items.DIAMOND,Items.APPLE,Items.STONE};
            for(int i=0;i<27;i++){ItemStack stack=new ItemStack(kinds[(i*3)%4],1+i%11);if(i%5==0)stack.set(DataComponents.CUSTOM_NAME,Component.literal("native-fixture-"+i));player.getInventory().setItem(i+9,stack);sortBefore.add(stack.copy());}
            player.getInventory().setChanged();player.inventoryMenu.broadcastFullState();
        }
        void verifyInventory(){
            boolean changed=false;for(int i=0;i<27;i++){ItemStack actual=player.getInventory().getItem(i+9),before=sortBefore.get(i);if(!ItemStack.isSameItemSameComponents(actual,before)||actual.getCount()!=before.getCount())changed=true;}
            require(changed,"IPN actually reorders/merges the deliberately unsorted inventory");
            int afterCount=0;for(int slot=0;slot<player.getInventory().getContainerSize();slot++)afterCount+=player.getInventory().getItem(slot).getCount();require(afterCount==sortBefore.stream().mapToInt(ItemStack::getCount).sum(),"IPN preserves total item count");
            for(ItemStack expected:sortBefore){int before=sortBefore.stream().filter(s->ItemStack.isSameItemSameComponents(s,expected)).mapToInt(ItemStack::getCount).sum(),after=0;for(int i=0;i<player.getInventory().getContainerSize();i++){ItemStack actual=player.getInventory().getItem(i);if(ItemStack.isSameItemSameComponents(expected,actual))after+=actual.getCount();}require(before==after,"IPN preserves component-specific stack counts");}
            require(player.containerMenu.getCarried().isEmpty(),"IPN leaves no carried stack");checks.add("Real IPN sortInventory changes server inventory order while preserving all components, item counts and an empty cursor");
        }
        void cleanup(Throwable error){
            if(cleaning)return;cleaning=true;failure=error;UnboxClient.CONFIG.clear();UnboxClient.CONFIG.putAll(saved);mc.setScreen(previousScreen);SurvivalClientMods.sync();
            server(()->{
                if(player!=null){if(mode!=null)player.setGameMode(mode);player.setShiftKeyDown(originalShift);if(!inventory.isEmpty()){player.getInventory().clearContent();for(int i=0;i<inventory.size();i++)player.getInventory().setItem(i,inventory.get(i));player.inventoryMenu.broadcastFullState();}}
                if(fixtureReady){for(var entry:blocks.entrySet())player.level().setBlock(entry.getKey(),entry.getValue(),3);player.level().getEntities(player,new AABB(net.minecraft.world.phys.Vec3.atLowerCornerOf(ore.offset(-3,-2,-2)),net.minecraft.world.phys.Vec3.atLowerCornerOf(ore.offset(6,8,11))),e->e instanceof ItemEntity).forEach(e->e.discard());Object trees=call(treeConfig,"getTrees");trees.getClass().getMethod("setBreakMode",oldBreakMode.getClass()).invoke(trees,oldBreakMode);}
                // Restore host-owned upstream values immediately, before reporting completion.
                Method vein=SurvivalHostMods.class.getDeclaredMethod("syncVein",boolean.class);vein.setAccessible(true);vein.invoke(null,true);Method treeSync=SurvivalHostMods.class.getDeclaredMethod("syncTree");treeSync.setAccessible(true);treeSync.invoke(null);
            });
        }
        void complete(){active=null;if(failure==null)result.complete(List.copyOf(checks));else result.completeExceptionally(failure);}
    }
    private static Object singleton(String type)throws Exception{return Class.forName(type).getField("INSTANCE").get(null);}
    private static Object call(Object owner,String name)throws Exception{return owner.getClass().getMethod(name).invoke(owner);}
    private static boolean ipn(String section,String key)throws Exception{return (boolean)call(call(singleton("org.anti_ad.mc.ipnext.config."+section),"get"+key),"getValue");}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
