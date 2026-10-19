package net.anware.tmc.colourscroller;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import java.util.*;

/** MIT. Adapted from ColourScroller's single-stack cycle; see LICENSE and UPSTREAM. */
public final class ColourScroller {
    private static final Map<Item,List<Item>> SETS=new HashMap<>();
    private static boolean ready;
    private static void addSet(String... names){
        List<Item> items=new ArrayList<>();
        for(String name:names){Item item=BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name));if(item!=null&&item!=Items.AIR&&!items.contains(item))items.add(item);}
        if(items.size()<2)return;
        List<Item> set=List.copyOf(items);for(Item item:items)SETS.putIfAbsent(item,set);
    }
    private static void initialize(){
        if(ready)return;ready=true;
        for(String wood:List.of("oak","spruce","birch","jungle","acacia","dark_oak","mangrove","cherry","pale_oak","crimson","warped","bamboo")){
            boolean nether=wood.equals("crimson")||wood.equals("warped");
            String log=wood+(nether?"_stem":"_log"),all=wood+(nether?"_hyphae":"_wood");
            addSet(log,all,"stripped_"+log,"stripped_"+all,wood+"_block","stripped_"+wood+"_block",wood+"_planks",wood+"_stairs",wood+"_slab",wood+"_fence",wood+"_fence_gate",wood+"_door",wood+"_trapdoor",wood+"_button",wood+"_pressure_plate");
        }
        // Derive shape groups from real registry entries, preserving each material prefix.
        for(Item item:BuiltInRegistries.ITEM){
            Identifier id=BuiltInRegistries.ITEM.getKey(item);if(!id.getNamespace().equals("minecraft"))continue;
            String name=id.getPath();if(!name.endsWith("_stairs")&&!name.endsWith("_slab"))continue;
            String base=name.substring(0,name.lastIndexOf('_'));
            addSet(base,base+"s",base+"_block",base+"_stairs",base+"_slab",base+"_wall");
        }
    }
    public static ItemStack cycle(Player player,ItemStack current,int shift,boolean includeStripped){
        if(player==null||!player.isCreative()||current.isEmpty()||shift==0)return ItemStack.EMPTY;
        initialize();List<Item> list=SETS.get(current.getItem());if(list==null)return ItemStack.EMPTY;
        int index=list.indexOf(current.getItem());
        for(int n=1;n<=list.size();n++){
            Item item=list.get(Math.floorMod(index+Integer.signum(shift)*n,list.size()));
            if(!includeStripped&&BuiltInRegistries.ITEM.getKey(item).getPath().startsWith("stripped_"))continue;
            ItemStack result=new ItemStack(item,current.getCount());
            // Preserve user annotations, not the old block model/state or container contents.
            result.copyFrom(DataComponents.CUSTOM_NAME,current);result.copyFrom(DataComponents.LORE,current);
            return result;
        }
        return ItemStack.EMPTY;
    }
}
