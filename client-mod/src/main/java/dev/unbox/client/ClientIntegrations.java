package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;

/** Small adapters to separately distributed upstream mods; Unbox owns only the exposed settings. */
public final class ClientIntegrations {
    private static final Map<String,String> errors=new java.util.concurrent.ConcurrentHashMap<>();
    private static int ticks;
    private static final Map<String,Long> revisions=new HashMap<>(),retryAt=new HashMap<>();
    private static final Map<String,Boolean> states=new HashMap<>();
    private static boolean buildingWasActive;
    public static final List<String> IDS=List.of("mousetweaks","shulker","effectbars","bookshelf","resources","spatial","building","variants");
    private ClientIntegrations(){}
    public static String engine(String id){return switch(id){
        case "mousetweaks"->"mousetweaks";case "shulker"->"easyshulkerboxes";
        case "effectbars"->Platform.loader().equals("fabric")?"status-effect-bars":"status_effect_bars";
        case "bookshelf"->"chiseledbookshelfvisualizer";case "resources"->"resourcify";
        case "spatial"->Platform.loader().equals("fabric")?"spatial-gui":"spatial_gui";
        case "building"->"effortlessbuilding";case "variants"->"colourscroller";default->"";};}
    public static boolean available(String id){return engine(id).isEmpty()||Platform.loaded(engine(id))&&!errors.containsKey(id);}
    public static String reason(String id){return errors.getOrDefault(id,"Launch with Unbox to install "+engine(id));}
    public static boolean active(String id){if(!available(id))return false;return id.equals("building")?WorldFeatures.buildingClient():id.equals("shulker")?SurvivalHostMods.hostEnabled(id):UnboxClient.enabled(id);}
    public static KeyMapping key(String id){
        if(id.equals("variants"))return CreativeVariants.key;
        if(id.equals("ghast"))return HappyGhastRecall.key;
        if(id.equals("building"))for(KeyMapping key:Minecraft.getInstance().options.keyMappings)
            if(key.getName().equals("key.effortlessbuilding.open_radial_menu"))return key;
        return null;
    }
    public static void sync(){
        if(ticks++%5!=0)return;
        for(String id:IDS){
            if(errors.containsKey(id)&&System.nanoTime()<retryAt.getOrDefault(id,0L))continue;
            if(id.equals("spatial")||errors.containsKey(id)||!Objects.equals(revisions.get(id),UnboxClient.revision())||!Objects.equals(states.get(id),active(id)))refresh(id);
        }
    }
    public static void refresh(String id){
        if(!Platform.loaded(engine(id))||errors.containsKey(id)&&System.nanoTime()<retryAt.getOrDefault(id,0L))return;
        try{apply(id);if(errors.remove(id)!=null){revisions.remove(id);states.remove(id);}else{revisions.put(id,UnboxClient.revision());states.put(id,active(id));}}catch(ReflectiveOperationException|LinkageError|RuntimeException e){
            errors.put(id,"Integration failed; retrying in 5 seconds");retryAt.put(id,System.nanoTime()+5_000_000_000L);
            System.err.println("[Unbox] "+id+" integration: "+e);
        }
    }
    private static Object field(Object obj,String name)throws ReflectiveOperationException{return obj.getClass().getField(name).get(obj);}
    private static void set(Object obj,String name,Object value)throws ReflectiveOperationException{obj.getClass().getField(name).set(obj,value);}
    private static Object singleton(String type,String name)throws ReflectiveOperationException{return Class.forName(type).getField(name).get(null);}
    private static boolean flag(String name,boolean fallback){return ClientHud.flag(name,fallback);}
    private static int number(String name,int fallback,int min,int max){return UnboxClient.number(name,fallback,min,max);}
    private static void bool(Object obj,String name,boolean value)throws ReflectiveOperationException{obj.getClass().getMethod("set"+name,boolean.class).invoke(obj,value);}
    private static void choice(Object obj,String field,String value)throws ReflectiveOperationException{
        var f=obj.getClass().getField(field);for(Object v:f.getType().getEnumConstants())if(((Enum<?>)v).name().equals(value)){f.set(obj,v);return;}
        throw new IllegalArgumentException("Unsupported "+field+": "+value);
    }
    private static Object autoConfig(String type)throws ReflectiveOperationException{
        Object holder=Class.forName("me.shedaniel.autoconfig.AutoConfig").getMethod("getConfigHolder",Class.class).invoke(null,Class.forName(type));
        return Class.forName("me.shedaniel.autoconfig.ConfigHolder").getMethod("getConfig").invoke(holder);
    }
    private static void apply(String id)throws ReflectiveOperationException{
        boolean on=active(id);
        switch(id){
            case "mousetweaks"->{
                Object c=singleton("yalter.mousetweaks.Main","config");
                set(c,"rmbTweak",on&&flag("mousetweaks.rightDrag",true));
                set(c,"lmbTweakWithItem",on&&flag("mousetweaks.leftDrag",true));
                set(c,"lmbTweakWithoutItem",on&&flag("mousetweaks.collect",true));
                set(c,"wheelTweak",on&&flag("mousetweaks.scroll",true));
            }
            case "shulker"->{
                Object holder=singleton("fuzs.iteminteractions.common.impl.ItemInteractions","CONFIG");
                Object c=Class.forName("fuzs.puzzleslib.common.api.config.v3.ConfigHolder").getMethod("get",Class.class).invoke(holder,Class.forName("fuzs.iteminteractions.common.impl.config.ClientConfig"));
                choice(c,"itemStorageTooltip",on?ClientHud.value("shulker.preview","SHIFT"):"NEVER");
                set(c,"itemStorageContentsCount",on&&flag("shulker.count",true));
                set(c,"itemStorageIndicator",on&&flag("shulker.indicator",true));
                set(c,"nestedItemTooltips",on&&flag("shulker.nested",true));
                set(c,"reverseSingleItemScrolling",flag("shulker.reverseScroll",false));
            }
            case "effectbars"->{
                Object c=singleton("io.github.a5b84.statuseffectbars.StatusEffectBars","config");
                for(String layout:List.of("hudLayout","inventoryLayout")){
                    Object l=field(c,layout);set(l,"enabled",on&&flag("effectbars."+layout,true));
                    set(l,"thickness",number("effectbars.thickness",2,1,6));
                }
                set(c,"backgroundColor",(number("effectbars.opacity",50,0,100)*255/100<<24)|(ClientHud.color("effectbars.color","#000000")&0xffffff));
            }
            case "bookshelf"->{Object c=autoConfig("net.anvian.chiseledbookshelfvisualizer.client.config.ClientConfig");set(c,"scale",number("bookshelf.scale",100,50,200)/100.0);set(c,"useRoman",flag("bookshelf.roman",true));}
            case "resources"->{
                Object companion=singleton("dev.dediamondpro.resourcify.config.Config","Companion");Object c=companion.getClass().getMethod("getInstance").invoke(companion);
                for(String kind:List.of("ResourcePacks","DataPacks","ShaderPacks","Worlds"))bool(c,kind+"Enabled",on&&flag("resources."+kind,true));
                bool(c,"FullResThumbnail",flag("resources.fullImages",false));bool(c,"GifsDisabled",!flag("resources.animate",true));
                bool(c,"OpenLinkInResourcify",on&&flag("resources.openLinks",true));
            }
            case "spatial"->SpatialSettings.sync();
            case "building"->{
                if(!on&&buildingWasActive){
                    Class.forName("nl.requios.effortlessbuilding.buildpipeline.BuildPipelineClient").getMethod("cancelCurrentSequence").invoke(null);
                    var mc=Minecraft.getInstance();if(mc.screen!=null&&mc.screen.getClass().getName().startsWith("nl.requios.effortlessbuilding.screen."))mc.setScreen(null);
                }
                buildingWasActive=on;
                Object c=singleton("nl.requios.effortlessbuilding.config.ClientConfig","INSTANCE");
                c.getClass().getMethod("setPreviewBlockTransparency",float.class).invoke(c,number("building.opacity",50,10,100)/100f);
                c.getClass().getMethod("setMaxBlockPreviews",int.class).invoke(c,number("building.previews",1000,50,5000));
                bool(c,"ProtectTileEntities",flag("building.protect",true));
            }
            default->{}
        }
    }
    public static String openResources(Screen parent){
        if(!active("resources"))return "Enable Resourcify first";
        // The vanilla pack screen supplies Resourcify's native browse button and a reliable return path.
        Minecraft mc=Minecraft.getInstance();
        mc.setScreen(new net.minecraft.client.gui.screens.packs.PackSelectionScreen(mc.getResourcePackRepository(),repository->{mc.options.updateResourcePacks(repository);mc.setScreen(parent);},mc.getResourcePackDirectory(),net.minecraft.network.chat.Component.translatable("resourcePack.title")));
        return "";
    }
}
