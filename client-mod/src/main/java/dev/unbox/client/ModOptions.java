package dev.unbox.client;

import java.util.*;

/** One settings catalogue for controls, defaults, and module-scoped reset. */
public final class ModOptions {
    public record Mod(String id,String title,String group,String hint) {}
    public record Option(String key,String label,String kind,String fallback,int min,int max,String[] choices) {}
    public static final List<Mod> MODS=List.of(
        new Mod("minimap","Minimap","HUD","Terrain and death markers"),new Mod("ping","Latency","HUD","Server round-trip latency"),new Mod("fps","FPS","HUD","Frame rate"),new Mod("cps","CPS","HUD","Left and right clicks"),
        new Mod("keystrokes","Keystrokes","HUD","Movement and mouse feedback"),new Mod("coordinates","Coordinates","HUD","Position and direction"),new Mod("armor","Armor Status","HUD","Equipment durability"),
        new Mod("crosshair","Crosshair","Visuals","Shape and color"),new Mod("particles","Particles","Visuals","Effect visibility"),
        new Mod("sprint","Auto Sprint","Controls","Sprint while moving forward"),new Mod("zoom","Zoom","Controls","A closer look"),
        new Mod("chat","Chat","Visuals","Readability and layout"),new Mod("freelook","Freelook","Controls","Look around independently"),
        new Mod("cape","OptiFine Cape","Visuals","Display owned capes"),new Mod("performance","Performance","System","Installed engines"));
    public static Mod mod(String id){return MODS.stream().filter(m->m.id.equals(id)).findFirst().orElseThrow();}
    static Option slider(String k,String l,int v,int min,int max){return new Option(k,l,"slider",""+v,min,max,null);}
    static Option toggle(String k,String l,boolean v){return new Option(k,l,"toggle",""+v,0,0,null);}
    static Option choice(String k,String l,String v,String... c){return new Option(k,l,"choice",v,0,0,c);}
    static Option color(String k,String l,String v){return new Option(k,l,"color",v,0,0,null);}
    public static List<Option> options(String id,String tab){
        List<Option> o=new ArrayList<>();
        if(tab.equals("Visibility")&&mod(id).group.equals("HUD"))return List.of(toggle(id+".locked","Lock position",false),toggle(id+".inChat","Show in chat",true),toggle(id+".inDebug","Show in debug screen",false));
        if(id.equals("armor")&&tab.equals("Equipment"))return List.of(toggle("armor.helmet","Helmet",true),toggle("armor.chestplate","Chestplate",true),toggle("armor.leggings","Leggings",true),toggle("armor.boots","Boots",true),toggle("armor.hand","Main hand",true),toggle("armor.offhand","Offhand",false),toggle("armor.empty","Show empty slots",false));
        if(id.equals("minimap")&&tab.equals("Appearance"))return List.of(slider("minimap.scale","Scale %",100,50,200));
        if(tab.equals("Appearance")&&mod(id).group.equals("HUD")){
            o.add(slider(id+".scale","Scale %",100,50,200));o.add(color(id+".color","Text color","#F4F6F8"));
            o.add(toggle(id+".shadow","Text shadow",true));if(!id.equals("fps")&&!id.equals("cps")&&!id.equals("keystrokes")){o.add(toggle(id+".background","Background",true));
            o.add(color(id+".backgroundColor","Background color","#191E24"));o.add(slider(id+".opacity","Background opacity %",60,0,100));
            o.add(slider(id+".padding","Padding",4,0,12));o.add(toggle(id+".rounded","Rounded corners",true));}if(id.equals("keystrokes")){o.add(color("keystrokes.idleColor","Key background","#191E24"));o.add(slider("keystrokes.opacity","Key opacity %",40,0,100));o.add(color("keystrokes.pressColor","Pressed background","#FFFFFF"));o.add(color("keystrokes.pressText","Pressed text","#171D23"));o.add(color("keystrokes.borderColor","Border color","#FFFFFF"));o.add(toggle("keystrokes.border","Key border",true));}return o;
        }
        if(id.equals("crosshair")&&tab.equals("Behavior")){
            o.add(slider("crosshair.scale","Scale %",100,50,200));o.add(slider("crosshair.rotation","Rotation",0,-180,180));
            o.add(toggle("crosshair.target","Target color",false));o.add(color("crosshair.targetColor","Target color","#FF7979"));
            o.add(toggle("crosshair.inDebug","Show in debug screen",false));o.add(toggle("crosshair.thirdPerson","Show in third person",false));
            o.add(toggle("crosshair.spectator","Show in spectator",false));return o;
        }
        if(id.equals("particles")&&tab.equals("Types")){
            o.add(choice("particles.family","Particle type","Critical","Critical","Smoke","Flames","Portal","Explosions","Block","Potion","Water","Enchant","Hearts","Lava","Snow","Clouds","Totem"));
            String family=particleFamily();o.add(toggle("particles."+family,"Show this type",true));
            o.add(slider("particles."+family+".density","Type density %",100,0,100));o.add(slider("particles."+family+".size","Size %",100,25,200));
            o.add(slider("particles."+family+".opacity","Opacity %",100,0,100));o.add(slider("particles."+family+".lifetime","Lifetime %",100,25,200));o.add(toggle("particles."+family+".tint","Custom color",false));
            o.add(color("particles."+family+".color","Particle color","#FFFFFF"));return o;
        }
        if(id.equals("crosshair")&&tab.equals("Presets"))return o;
        if(id.equals("crosshair")&&tab.equals("Canvas")){o.add(choice("crosshair.mirror","Mirror","Off","Off","Horizontal","Both"));return o;}
        switch(id){
            case "minimap" -> {o.add(slider("minimap.zoom","Blocks per pixel",2,1,8));o.add(toggle("minimap.deaths","Death markers",true));o.add(slider("minimap.budget","Maximum samples per tick",512,128,1024));}
            case "ping" -> o.add(toggle("ping.label","Show ms label",true));
            case "fps" -> {o.add(toggle("fps.label","Show FPS label",true));o.add(choice("fps.interval","Refresh interval","250 ms","100 ms","250 ms","500 ms","1000 ms"));}
            case "keystrokes" -> {o.add(toggle("keystrokes.movement","Movement keys",true));o.add(toggle("keystrokes.mouse","Mouse buttons",true));o.add(toggle("keystrokes.space","Jump key",true));o.add(toggle("keystrokes.cps","Show CPS",true));o.add(toggle("keystrokes.arrows","Use arrows",false));o.add(slider("keystrokes.size","Key size",24,18,36));o.add(slider("keystrokes.gap","Key spacing",3,0,8));o.add(slider("keystrokes.fade","Release fade (ms)",180,0,500));}
            case "cps" -> {o.add(slider("cps.width","Button width",48,36,80));o.add(slider("cps.height","Button height",32,28,48));o.add(slider("cps.spacing","Button spacing",4,0,16));o.add(color("cps.pressText","Pressed text color","#FFFFFF"));o.add(choice("cps.mode","Mouse buttons","Both","Both","Left","Right"));o.add(toggle("cps.feedback","Click feedback",true));o.add(color("cps.pressColor","Pressed color","#FFFFFF"));o.add(slider("cps.pressOpacity","Pressed opacity %",35,10,100));o.add(slider("cps.fade","Fade duration (ms)",180,50,500));}
            case "coordinates" -> {o.add(toggle("coordinates.direction","Show direction",true));o.add(toggle("coordinates.labels","Show XYZ labels",true));o.add(choice("coordinates.layout","Layout","Stacked","Stacked","Single line"));o.add(slider("coordinates.decimals","Decimal places",0,0,2));o.add(toggle("coordinates.biome","Show biome",false));o.add(toggle("coordinates.dimension","Show dimension",false));o.add(toggle("coordinates.nether","Nether / Overworld position",false));}
            case "armor" -> {o.add(choice("armor.layout","Layout","Vertical","Vertical","Horizontal"));o.add(choice("armor.format","Durability","Remaining","Remaining","Percentage","Both","Remaining / Max","Off"));o.add(toggle("armor.names","Item names",false));o.add(toggle("armor.count","Stack count",true));o.add(slider("armor.spacing","Slot spacing",4,0,12));o.add(toggle("armor.reverse","Reverse order",false));o.add(toggle("armor.bar","Durability bars",true));o.add(toggle("armor.dynamic","Dynamic durability color",true));o.add(slider("armor.warning","Warning below %",20,0,50));o.add(color("armor.warningColor","Warning color","#EF7078"));}
            case "crosshair" -> {o.add(choice("crosshair.shape","Shape","Cross","Cross","Dot","T","Custom"));o.add(color("crosshair.color","Color","#FFFFFF"));o.add(slider("crosshair.size","Arm length",5,2,20));o.add(slider("crosshair.gap","Center gap",2,0,10));o.add(slider("crosshair.thickness","Thickness",1,1,5));o.add(toggle("crosshair.dot","Center dot",false));o.add(slider("crosshair.dotSize","Dot size",1,1,7));o.add(toggle("crosshair.outline","Outline",true));o.add(slider("crosshair.outlineWidth","Outline thickness",1,1,3));o.add(color("crosshair.outlineColor","Outline color","#000000"));o.add(slider("crosshair.opacity","Opacity %",100,10,100));o.add(toggle("crosshair.attack","Attack cooldown",true));}
            case "particles" -> {o.add(slider("particles.density","Density %",100,0,100));for(String title:List.of("Critical","Smoke","Flames","Portal","Explosions","Block","Potion","Water","Enchant","Hearts","Lava","Snow","Clouds","Totem"))o.add(toggle("particles."+family(title),title,true));}
            case "sprint" -> o.add(toggle("sprint.indicator","Show sprint status",true));
            case "freelook" -> {o.add(toggle("freelook.invertX","Invert horizontal",false));o.add(toggle("freelook.invertY","Invert vertical",false));o.add(toggle("freelook.smooth","Smooth camera",false));}
            case "zoom" -> {o.add(slider("zoom.factor","Magnification",3,2,12));o.add(choice("zoom.mode","Activation","Hold","Hold","Toggle"));o.add(toggle("zoom.smooth","Smooth transition",true));o.add(slider("zoom.duration","Transition time (ms)",170,50,500));o.add(toggle("zoom.scroll","Scroll to zoom",true));o.add(toggle("zoom.adaptive","Adjust mouse sensitivity",true));}
            case "chat" -> {o.add(color("chat.color","Background color","#000000"));o.add(slider("chat.opacity","Background opacity %",50,0,100));o.add(slider("chat.scale","Scale %",100,50,100));o.add(slider("chat.width","Width %",100,20,100));o.add(slider("chat.lines","Visible lines (maximum)",20,1,40));o.add(slider("chat.focused","Open chat height %",100,20,100));o.add(slider("chat.unfocused","Closed chat height %",44,10,100));o.add(slider("chat.spacing","Line spacing %",0,0,100));o.add(slider("chat.textOpacity","Text opacity %",100,10,100));}
            case "cape" -> o.add(toggle("cape.selfOnly","Only my cape",false));
            default -> {}
        }
        return o;
    }
    public static String particleFamily(){return family(ClientHud.value("particles.family","Critical"));}
    public static String family(String title){return switch(title){case "Smoke"->"smoke";case "Flames"->"flame";case "Portal"->"portal";case "Explosions"->"explosion";case "Block"->"block";case "Potion"->"potion";case "Water"->"water";case "Enchant"->"enchant";case "Hearts"->"heart";case "Lava"->"lava";case "Snow"->"snow";case "Clouds"->"cloud";case "Totem"->"totem";default->"crit";};}
    public static List<String> tabs(String id){return mod(id).group.equals("HUD")?id.equals("armor")?List.of("General","Equipment","Appearance","Visibility"):List.of("General","Appearance","Visibility"):id.equals("crosshair")?List.of("General","Presets","Canvas","Behavior"):id.equals("particles")?List.of("General","Types"):List.of("General");}
    public static boolean available(Option o){String k=o.key();if(k.equals("zoom.duration"))return ClientHud.flag("zoom.smooth",true);if(k.equals("crosshair.outlineWidth")||k.equals("crosshair.outlineColor"))return ClientHud.flag("crosshair.outline",true);if(k.equals("crosshair.dotSize"))return ClientHud.flag("crosshair.dot",false)||ClientHud.value("crosshair.shape","Cross").equals("Dot");String shape=ClientHud.value("crosshair.shape","Cross");if(k.equals("crosshair.size")||k.equals("crosshair.gap"))return !shape.equals("Custom")&&!shape.equals("Dot");if(k.equals("crosshair.thickness"))return !shape.equals("Custom");if(k.equals("crosshair.dot"))return !shape.equals("Custom")&&!shape.equals("Dot");if((k.endsWith(".backgroundColor")||k.endsWith(".opacity")||k.endsWith(".rounded"))&&ClientHud.IDS.stream().anyMatch(id->k.startsWith(id+".")))return ClientHud.flag(k.substring(0,k.indexOf('.'))+".background",true);if(k.startsWith("cps.")&&!k.equals("cps.feedback")&&!k.equals("cps.mode")&&!k.equals("cps.scale")&&(k.contains("press")||k.endsWith("fade")))return ClientHud.flag("cps.feedback",true);if(k.equals("crosshair.targetColor"))return ClientHud.flag("crosshair.target",false);if(k.startsWith("particles.")&&k.endsWith(".color"))return ClientHud.flag(k.replace(".color",".tint"),false);return true;}
    public static String value(Option o){return UnboxClient.CONFIG.getProperty(o.key,o.fallback);}
    public static void reset(String id){
        // Reset every setting in this module, including unselected particle types and the canvas.
        UnboxClient.CONFIG.keySet().removeIf(k->k.toString().startsWith(id+".")&&!k.equals(id+".x")&&!k.equals(id+".y"));
        UnboxClient.save();
    }
}
