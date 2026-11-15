package dev.unbox.unpvp;

import com.google.gson.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;

/** Version-specific Unbox UI and PvP preferences. */
final class UiState {
    static final Properties VALUES=new Properties();
    static final List<Module> MODULES=new ArrayList<>();
    static final List<String> HUD=Arrays.asList("fps","cps","coordinates","armor","keystrokes","ping","potions","scoreboard");
    static final String PREVIEW="Personal PvP settings";
    static String message="";
    static void load(){
        Path file=file();
        try{if(Files.isRegularFile(file))try(InputStream in=Files.newInputStream(file)){VALUES.load(in);}}catch(IOException e){message="Could not load UI preferences";}
        try(Reader r=new InputStreamReader(UiState.class.getResourceAsStream("/assets/unbox/ui/catalog.json"),"UTF-8")){
            for(JsonElement e:new JsonParser().parse(r).getAsJsonArray())MODULES.add(new Module(e.getAsJsonObject()));
        }catch(IOException e){throw new IllegalStateException(e);}
        PvpSettings.refresh();
    }
    private static Path file(){return Minecraft.getMinecraft().mcDataDir.toPath().resolve("config/unpvp-ui.properties");}
    static void save(){PvpSettings.refresh();try{Path p=file();Files.createDirectories(p.getParent());Path tmp=p.resolveSibling("unpvp-ui.properties.tmp");try(OutputStream out=Files.newOutputStream(tmp)){VALUES.store(out,"Unbox HUD and UI preferences");}Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}catch(IOException e){message="Could not save UI preferences";}}
    static String get(String k,String fallback){return VALUES.getProperty(k,fallback);}
    static void set(String k,Object value){VALUES.setProperty(k,String.valueOf(value));if(k.indexOf('.')<0)PvpSettings.refresh();}
    static boolean flag(String k,boolean fallback){return Boolean.parseBoolean(get(k,String.valueOf(fallback)));}
    static int number(String k,int fallback,int min,int max){try{return clamp(Integer.parseInt(get(k,""+fallback)),min,max);}catch(NumberFormatException e){return clamp(fallback,min,max);}}
    static int color(String k,String fallback){try{return 0xff000000|Integer.parseInt(get(k,fallback).replace("#",""),16);}catch(NumberFormatException e){return 0xffffffff;}}
    static int clamp(int n,int a,int b){return Math.max(a,Math.min(b,n));}
    static float clamp(float n,float a,float b){return Math.max(a,Math.min(b,n));}
    static Module module(String id){for(Module m:MODULES)if(m.id.equals(id))return m;throw new IllegalArgumentException(id);}
    static boolean on(String id){return flag(id,LiveHud.IDS.contains(id));}
    static void reset(String id){for(Object k:new ArrayList<Object>(VALUES.keySet()))if(k.toString().startsWith(id+".")&&!k.equals(id+".x")&&!k.equals(id+".y"))VALUES.remove(k);save();}
    static final class Module {
        final String id,title,group,hint;final LinkedHashMap<String,List<Option>> tabs=new LinkedHashMap<>();
        Module(JsonObject j){id=j.get("id").getAsString();title=j.get("title").getAsString();group=j.get("group").getAsString();hint=j.get("hint").getAsString();for(Map.Entry<String,JsonElement> e:j.getAsJsonObject("tabs").entrySet()){List<Option> options=new ArrayList<>();for(JsonElement v:e.getValue().getAsJsonArray())options.add(new Option(v.getAsJsonObject()));tabs.put(e.getKey(),options);}}
    }
    static final class Option {
        final String key,label,kind,fallback;final int min,max;final String[] choices;
        Option(JsonObject j){key=j.get("key").getAsString();label=j.get("label").getAsString();kind=j.get("kind").getAsString();fallback=j.get("fallback").getAsString();min=j.get("min").getAsInt();max=j.get("max").getAsInt();List<String> values=new ArrayList<>();if(j.has("choices"))for(JsonElement e:j.getAsJsonArray("choices"))values.add(e.getAsString());choices=values.toArray(new String[0]);}
        Option(String key,String label,int value,int min,int max){this.key=key;this.label=label;kind="slider";fallback=""+value;this.min=min;this.max=max;choices=new String[0];}
        String value(){return get(key,fallback);}
        boolean available(){
            if(key.equals("zoom.duration"))return flag("zoom.smooth",true);
            if(key.startsWith("crosshair.outline")&&!key.equals("crosshair.outline"))return flag("crosshair.outline",true);
            if(key.equals("crosshair.targetColor"))return flag("crosshair.target",false);
            String shape=get("crosshair.shape","Cross");
            if(key.equals("crosshair.dotSize"))return flag("crosshair.dot",false)||shape.equals("Dot");
            if(key.equals("crosshair.size")||key.equals("crosshair.gap")||key.equals("crosshair.dot"))return !shape.equals("Custom")&&!shape.equals("Dot");
            if(key.equals("crosshair.thickness"))return !shape.equals("Custom");
            if(key.equals("spatial.firstInventory")||key.equals("spatial.firstContainers"))return !flag("spatial.autoCamera",true);
            String id=key.contains(".")?key.substring(0,key.indexOf('.')):key;
            if(HUD.contains(id)&&(key.endsWith(".opacity")||key.endsWith(".rounded")||key.endsWith(".backgroundColor")))return flag(id+".background",false);
            if(key.equals("keystrokes.idleColor")||key.startsWith("scoreboard.titleOpacity")||key.equals("scoreboard.titleBackground")||key.equals("chat.opacity")||key.equals("chat.color"))return flag(id+".background",false);
            if(key.startsWith("cps.")&&(key.contains("press")||key.endsWith("fade")))return flag("cps.feedback",true);
            return true;
        }
    }
}
