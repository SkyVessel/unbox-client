package dev.unbox.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.world.item.*;

/** Exercises the competing IPN drag handler on real slots, with its own setting forced on. */
final class MouseGestureSmokeTest {
    @SuppressWarnings("unchecked")
    static void verify(Minecraft mc,List<String> checks)throws Exception{
        var previous=mc.screen;var menu=mc.player.containerMenu;var mode=mc.gameMode.getPlayerMode();var carried=mc.player.containerMenu.getCarried().copy();var inventory=mc.player.getInventory();var saved=new ArrayList<ItemStack>();
        for(int i=0;i<inventory.getContainerSize();i++)saved.add(inventory.getItem(i).copy());
        Class<?> input=Class.forName("org.anti_ad.mc.common.input.GlobalInputHandler"),codes=Class.forName("org.anti_ad.mc.common.input.KeyCodes");
        Set<Integer> keys=(Set<Integer>)input.getMethod("getPressedKeys").invoke(input.getField("INSTANCE").get(null));var oldKeys=new HashSet<>(keys);
        Class<?> tracer=Class.forName("org.anti_ad.mc.ipnext.event.MouseTracer");var oldPoints=new HashMap<String,Integer>();
        try{
            UnboxClient.set("inventory",true);UnboxClient.set("mousetweaks",false);SurvivalClientMods.sync();
            if(!SurvivalClientMods.inventoryActive())throw new IllegalStateException("Competing inventory engine must stay active");
            mc.gameMode.setLocalMode(net.minecraft.world.level.GameType.SURVIVAL);mc.player.containerMenu=mc.player.inventoryMenu;mc.player.containerMenu.setCarried(ItemStack.EMPTY);
            Object tweaks=Class.forName("org.anti_ad.mc.ipnext.config.Tweaks").getField("INSTANCE").get(null),swipe=tweaks.getClass().getMethod("getCONTAINER_SWIPE_MOVING_ITEMS").invoke(tweaks);
            swipe.getClass().getMethod("setValue",boolean.class).invoke(swipe,true);
            for(int attempt=0;attempt<2;attempt++){
                for(int i=0;i<inventory.getContainerSize();i++)inventory.setItem(i,ItemStack.EMPTY);
                inventory.setItem(9,new ItemStack(Items.STONE,8));var screen=new InventoryScreen(mc.player);mc.setScreen(screen);
                if(mc.screen!=screen)throw new IllegalStateException("Expected a survival inventory fixture");
                var slot=screen.getMenu().slots.stream().filter(s->s.container==inventory&&s.getContainerSlot()==9).findFirst().orElseThrow();
                var left=AbstractContainerScreen.class.getDeclaredField("leftPos");var top=AbstractContainerScreen.class.getDeclaredField("topPos");left.setAccessible(true);top.setAccessible(true);
                var hovered=AbstractContainerScreen.class.getDeclaredField("hoveredSlot");hovered.setAccessible(true);hovered.set(screen,slot);
                Class<?> clicker=Class.forName("org.anti_ad.mc.ipnext.inventory.ContainerClicker");clicker.getMethod("shiftClick",int.class).invoke(clicker.getField("INSTANCE").get(null),slot.index);
                if(!inventory.getItem(9).isEmpty())throw new IllegalStateException("Positive control: inventory fixture cannot move slot "+slot.index);
                for(int i=0;i<inventory.getContainerSize();i++)inventory.setItem(i,ItemStack.EMPTY);inventory.setItem(9,new ItemStack(Items.STONE,8));
                int x=left.getInt(screen)+slot.x+8,y=top.getInt(screen)+slot.y+8;
                for(String name:List.of("x","lastX","y","lastY")){var f=tracer.getDeclaredField(name);f.setAccessible(true);oldPoints.putIfAbsent(name,f.getInt(null));f.setInt(null,name.equals("x")?x+12:name.equals("lastX")?x-12:y);}
                keys.clear();keys.add(codes.getField("MOUSE_BUTTON_1").getInt(null));keys.add(codes.getField("KEY_LEFT_SHIFT").getInt(null));
                if(!(boolean)input.getMethod("shiftAnd",int[].class).invoke(input.getField("INSTANCE").get(null),new int[]{codes.getField("MOUSE_BUTTON_1").getInt(null)}))throw new IllegalStateException("Shift-drag fixture is not pressed");
                Class<?> misc=Class.forName("org.anti_ad.mc.ipnext.event.MiscHandler");
                Class<?> function=Class.forName("kotlin.jvm.functions.Function3");var action=misc.getDeclaredMethod("slotAction",function);action.setAccessible(true);var seen=new ArrayList<Object>();
                Object probe=java.lang.reflect.Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(o,m,a)->{if(m.getName().equals("invoke"))seen.add(a[0]);return null;});
                action.invoke(misc.getField("INSTANCE").get(null),probe);
                if(!seen.contains(slot))throw new IllegalStateException("Swipe fixture does not intersect target slot");
                misc.getMethod("swipeMoving").invoke(misc.getField("INSTANCE").get(null));
                if(inventory.getItem(9).getCount()!=8)throw new IllegalStateException("Disabled Mouse Tweaks still permits IPN swipe-moving actual items");
                checks.add("Disabled Mouse Tweaks prevents IPN dragging real items after opening inventory #"+attempt);
            }
        }finally{
            keys.clear();keys.addAll(oldKeys);for(var entry:oldPoints.entrySet()){var f=tracer.getDeclaredField(entry.getKey());f.setAccessible(true);f.setInt(null,entry.getValue());}
            for(int i=0;i<saved.size();i++)inventory.setItem(i,saved.get(i));mc.player.containerMenu=menu;menu.setCarried(carried);mc.gameMode.setLocalMode(mode);mc.setScreen(previous);UnboxClient.set("inventory",false);SurvivalClientMods.sync();
        }
    }
}
