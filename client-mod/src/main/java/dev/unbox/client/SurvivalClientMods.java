package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import java.lang.reflect.*;
import java.util.*;

/** Adapters for the exact upstream versions in survival-mods-lock.json. */
public final class SurvivalClientMods {
    private record BooleanSetting(Object value,Method get,Method set) {
        void apply(boolean wanted)throws ReflectiveOperationException{
            if((boolean)get.invoke(value)!=wanted)set.invoke(value,wanted);
        }
    }
    private static final Map<String,BooleanSetting> inventorySettings=new HashMap<>();
    private static final Map<String,String> errors=new HashMap<>();
    private static Object itemConfig;private static Field rotation,speed;
    private static boolean itemsResolved,inventoryResolved;
    private static int ticks;
    private SurvivalClientMods(){}

    public static String engine(String module){return switch(module){case "items"->"itemphysiclite";case "inventory"->"inventoryprofilesnext";default->"";};}
    public static boolean available(String module){String id=engine(module);return id.isEmpty()||Platform.loaded(id)&&!errors.containsKey(module);}
    public static String reason(String module){
        if(errors.containsKey(module))return errors.get(module);
        var game=Platform.mod("minecraft");
        if(module.equals("inventory")&&Platform.loader().equals("neoforge")&&game!=null&&game.version().equals("26.1")&&!Platform.loaded("inventoryprofilesnext"))return "Inventory Profiles Next requires Minecraft 26.1.2 on NeoForge";
        return available(module)?"":"Launch with Unbox to install "+(module.equals("items")?"ItemPhysic Lite":"Inventory Profiles Next");
    }
    public static boolean inventoryActive(){return available("inventory")&&UnboxClient.enabled("inventory")&&Minecraft.getInstance().player!=null;}
    public static boolean itemsActive(){return available("items")&&UnboxClient.enabled("items");}
    public static void sync(){
        if(Platform.loaded("itemphysiclite")&&!errors.containsKey("items"))try{
            if(!itemsResolved){Class<?> mod=Class.forName("team.creative.itemphysiclite.ItemPhysicLite");itemConfig=mod.getField("CONFIG").get(null);rotation=itemConfig.getClass().getField("oldRotation");speed=itemConfig.getClass().getField("rotateSpeed");itemsResolved=true;}
            rotation.setBoolean(itemConfig,ClientHud.flag("items.oldRotation",false));
            speed.setFloat(itemConfig,UnboxClient.number("items.rotationSpeed",100,0,300)/100f);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){failed("items",e);}
        if(Platform.loaded("inventoryprofilesnext")&&!errors.containsKey("inventory"))try{
            boolean on=inventoryActive();
            // Check every tick: upstream hotkeys cannot re-enable a feature in a restricted session.
            apply("Features","ENABLE_SORTING_AND_MOVING",on);
            apply("Features","ENABLE_AUTO_REFILL",on&&ClientHud.flag("inventory.refill",false));
            apply("Features","ENABLE_LOCK_SLOTS",on&&ClientHud.flag("inventory.lockSlots",false));
            apply("Features","ENABLE_PROFILES",on&&ClientHud.flag("inventory.profiles",false));
            // Mouse Tweaks exclusively owns enhanced mouse gestures.
            apply("Features","ITEM_SCROLLING",false);
            apply("Tweaks","CONTAINER_SWIPE_MOVING_ITEMS",false);
            apply("Features","HIGHLIGHT_FOUSED_ITEMS",on&&ClientHud.flag("inventory.highlight",true));
            apply("Features","VILLAGER_TRADING_ENABLE",false);
            apply("GuiSettings","ENABLE_INVENTORY_BUTTONS",on&&ClientHud.flag("inventory.buttons",true));
            apply("GuiSettings","INVENTORY_OVERLAY_BUTTONS_VISIBLE",on&&ClientHud.flag("inventory.buttons",true));
            apply("GuiSettings","ENABLE_PROFILES_UI",on&&ClientHud.flag("inventory.profiles",false));
            if(!inventoryResolved||++ticks%20==0){
                apply("ModSettings","ALWAYS_INCLUDE_HOTBAR",ClientHud.flag("inventory.includeHotbar",false));
                apply("GuiSettings","SHOW_BUTTON_TOOLTIPS",ClientHud.flag("inventory.tooltips",true));
                inventoryResolved=true;
            }
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){failed("inventory",e);}
    }
    private static Object singleton(String name)throws ReflectiveOperationException{return Class.forName(name).getField("INSTANCE").get(null);}
    private static void apply(String section,String key,boolean value)throws ReflectiveOperationException{
        String name=section+"."+key;BooleanSetting setting=inventorySettings.get(name);
        if(setting==null){Object owner=singleton("org.anti_ad.mc.ipnext.config."+section);Object option=owner.getClass().getMethod("get"+key).invoke(owner);setting=new BooleanSetting(option,option.getClass().getMethod("getValue"),option.getClass().getMethod("setValue",boolean.class));inventorySettings.put(name,setting);}
        setting.apply(value);
    }
    /** Native advanced page supplies upstream keybind, sorting-rule and per-container editors. */
    public static String openInventorySettings(Screen parent){
        if(!available("inventory"))return reason("inventory");
        try{
            Object settings=singleton("org.anti_ad.mc.ipnext.config.ConfigScreenSettings");
            Class<?> type=Class.forName("org.anti_ad.mc.common.gui.screen.ConfigScreenBase");
            Object screen=type.getConstructor(Class.forName("org.anti_ad.mc.common.gui.screen.BaseConfigScreenSettings")).newInstance(settings);
            type.getMethod("setParent",Screen.class).invoke(screen,parent);
            var sections=(List<?>)settings.getClass().getMethod("getConfigDeclarations").invoke(settings);
            for(int i=0;i<sections.size();i++)if(sections.get(i).getClass().getSimpleName().equals("Hotkeys")){type.getMethod("setSelectedIndex",int.class).invoke(screen,i);break;}
            Minecraft.getInstance().setScreen((Screen)screen);return "";
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){failed("inventory",e);return reason("inventory");}
    }
    static boolean sortInventory(){
        if(!inventoryActive())return false;
        try{
            sync();if(!inventoryActive())return false;
            var mc=Minecraft.getInstance();if(mc.player==null||!mc.player.containerMenu.getCarried().isEmpty())return false;
            Object actions=singleton("org.anti_ad.mc.ipnext.inventory.GeneralInventoryActions");
            actions.getClass().getMethod("doSort",net.minecraft.world.inventory.AbstractContainerMenu.class,boolean.class,boolean.class).invoke(actions,mc.player.containerMenu,false,false);
            return true;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){failed("inventory",e);return false;}
    }
    private static void failed(String module,Throwable e){
        errors.put(module,"Integration unavailable; check the installed mod version");
        System.err.println("[Unbox] "+module+" integration disabled: "+e);
    }
}
