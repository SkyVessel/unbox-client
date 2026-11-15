package dev.unbox.client;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;
import java.awt.Color;
import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;

/** Version-pinned adapters. Values belong to upstream configs, never a second Unbox copy. */
public final class PvpSettings {
    private record Binding(ModOptions.Option option,Supplier<String> read,Consumer<String> write,String section) {}
    private static final Map<String,Binding> BINDINGS=new LinkedHashMap<>();
    private static final Map<String,Runnable> SAVERS=new HashMap<>();
    private static final Map<String,String> ERRORS=new HashMap<>();
    private static final Set<String> READY=new HashSet<>(),DIRTY=new LinkedHashSet<>();
    private static final Map<String,Object> ICONS=new HashMap<>();
    private static final Map<String,Runnable> ICON_RESETS=new HashMap<>();
    private static final String PREFIX="pvpconfig.";
    public static boolean supports(String id){return PvpModules.contains(id)&&!id.equals("eatingpack");}
    public static boolean owns(String key){return key.startsWith(PREFIX);}
    private static String module(String key){return key.substring(PREFIX.length()).split("\\.",2)[0];}
    public static String value(String key,String fallback){if(!owns(key))return fallback;ensure(module(key));Binding b=BINDINGS.get(key);return b==null?fallback:b.read.get();}
    public static void write(String key,String value){ensure(module(key));Binding b=BINDINGS.get(key);if(b==null)return;try{b.write.accept(value);DIRTY.add(module(key));ERRORS.remove(module(key));}catch(RuntimeException e){ERRORS.put(module(key),"Could not apply setting: "+cause(e));}}
    public static void flush(){for(String id:List.copyOf(DIRTY))try{SAVERS.getOrDefault(id,()->{}).run();DIRTY.remove(id);ERRORS.remove(id);}catch(RuntimeException e){ERRORS.put(id,"Could not save settings: "+cause(e));}}
    public static String notice(String id){ensure(id);return ERRORS.getOrDefault(id,Platform.loaded(id)?"Changes are saved automatically.":"Settings will apply after enabling and restarting.");}
    public static List<String> tabs(String id){ensure(id);var result=new ArrayList<String>();result.add("General");for(String s:List.of("Appearance","Keys"))if(BINDINGS.values().stream().anyMatch(b->module(b.option.key()).equals(id)&&b.section.equals(s)))result.add(s);return result;}
    public static List<ModOptions.Option> options(String id,String section){ensure(id);return BINDINGS.values().stream().filter(b->module(b.option.key()).equals(id)&&b.section.equals(section)).map(Binding::option).toList();}
    public static void reset(String id){ensure(id);for(var b:BINDINGS.values())if(module(b.option.key()).equals(id)&&!b.option.kind().equals("icon"))write(b.option.key(),b.option.fallback());ICON_RESETS.forEach((key,reset)->{if(module(key).equals(id))reset.run();});flush();}
    public static void resetOption(ModOptions.Option o){write(o.key(),o.fallback());flush();}
    public static void openIcon(String key,Screen parent){Minecraft.getInstance().setScreen(new PvpIconScreen(key,parent));}
    static Object icon(String key){return ICONS.get(key);}
    static void iconChanged(String key){DIRTY.add(module(key));}
    static Object call(Object owner,String name,Object... args){
        Class<?> type=owner instanceof Class<?> c?c:owner.getClass();
        for(Method m:type.getMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            boolean compatible=true;for(int i=0;i<args.length;i++)if(args[i]!=null&&!boxed(m.getParameterTypes()[i]).isInstance(args[i]))compatible=false;
            if(!compatible)continue;try{m.setAccessible(true);return m.invoke(owner instanceof Class<?>?null:owner,args);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }throw new IllegalArgumentException(type.getName()+"."+name);
    }
    private static Class<?> boxed(Class<?> c){if(!c.isPrimitive())return c;return c==boolean.class?Boolean.class:c==int.class?Integer.class:c==float.class?Float.class:c==double.class?Double.class:c==long.class?Long.class:c;}
    private static Class<?> type(String name){try{return Class.forName(name);}catch(ClassNotFoundException e){throw new IllegalStateException(e);}}
    private static Object get(Object owner,String name){try{Class<?> c=owner instanceof Class<?> t?t:owner.getClass();Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(owner instanceof Class<?>?null:owner);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static Object create(Class<?> c){try{var ctor=c.getDeclaredConstructor();ctor.setAccessible(true);return ctor.newInstance();}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static String cause(Throwable e){while(e.getCause()!=null)e=e.getCause();return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
    private static String label(String text){String s=text.replaceAll("([a-z0-9])([A-Z])","$1 $2").replace('_',' ').replace('.',' ');return s.isEmpty()?s:Character.toUpperCase(s.charAt(0))+s.substring(1);}
    private static String hex(int color){return String.format("#%06X",color&0xffffff);}
    private static int rgb(String s){return Integer.parseInt(s.replace("#",""),16);}
    private static void add(String id,String path,String label,String kind,String fallback,int min,int max,String[] choices,Supplier<String> read,Consumer<String> write,String section){String key=PREFIX+id+"."+path;BINDINGS.put(key,new Binding(new ModOptions.Option(key,label,kind,fallback,min,max,choices),read,write,section));}
    private static void ensure(String id){
        if(!supports(id)||!READY.add(id))return;
        try{
            switch(id){
                case "crosshairaddons","shieldfixes"->walksy(id);
                case "bettertotemcounter"->{Object c=call(type("com.dotshow.bettertotemcounter.Config"),"get");object(id,c,create(c.getClass()),()->call(c,"save"));}
                case "appleskin"->{Object c=get(type("squeek.appleskin.ModConfig"),"INSTANCE");object(id,c,create(c.getClass()),()->call(c,"save"));}
                case "combat-hitboxes"->{Object c=call(type("me.sootysplash.box.Config"),"getInstance");object(id,c,create(c.getClass()),()->call(c,"save"));}
                case "bactromod"->{Class<?> t=type("de.daniel.bactromod.config.Config");Object c=call(t,"load");object(id,c,create(c.getClass()),()->call(t,"save",c));}
                case "shulkerboxtooltip"->{call(type("com.misterpemodder.shulkerboxtooltip.impl.PluginManager"),"loadColors");Object c=get(type("com.misterpemodder.shulkerboxtooltip.ShulkerBoxTooltip"),"savedConfig");object(id,c,create(c.getClass()),()->call(type("com.misterpemodder.shulkerboxtooltip.impl.config.ConfigurationHandler"),"saveToFile",c));shulkerColors(id,get(get(c,"colors"),"colors"));}
                case "elytrapitch"->{Object holder=call(type("me.shedaniel.autoconfig.AutoConfig"),"getConfigHolder",type("fatalflare.elytrapitch.ModConfig"));Object c=call(holder,"getConfig");object(id,c,create(c.getClass()),()->call(holder,"save"));}
                case "shieldreturn"->{Object c=call(type("com.shieldreturn.ShieldReturnConfig"),"get");object(id,c,create(c.getClass()),()->call(c,"save"));add(id,"background","HUD background","toggle","false",0,0,null,()->""+ClientHud.flag("pvp.shieldreturn.background",false),v->UnboxClient.set("pvp.shieldreturn.background",v),"Appearance");}
                case "hitcolor"->{Object holder=get(type("ru.harimasa.config.HCScreen"),"CONFIG"),c=call(holder,"instance");object(id,c,create(c.getClass()),()->call(holder,"save"));}
                case "betterhitreg"->hitreg(id);
                case "handtweaks","naturalmotionblur"->{if(Platform.loaded(id)){Class<?> t=type(id.equals("handtweaks")?"com.handtweaks.config.AuraHandConfig":"net.natural.motionblur.config.ConfigManager");Object c=id.equals("handtweaks")?get(t,"instance"):call(t,"getConfig");object(id,c,create(c.getClass()),()->call(t,id.equals("handtweaks")?"save":"saveConfig"));}else offline(id);}
                // These projects expose no configurable values beyond their key / JAR enablement.
                case "statuseffecttimer","elytraswapperplus","lookbehind"->{}
                default->throw new IllegalArgumentException("No adapter for "+id);
            }
            keys(id);
        }catch(RuntimeException e){ERRORS.put(id,"Settings unavailable: "+cause(e));System.err.println("[Unbox PvP settings] "+id+": "+e);}
    }
    private static void object(String id,Object current,Object defaults,Runnable save){SAVERS.put(id,save);fields(id,"",current,defaults,0);}
    private static void fields(String id,String path,Object current,Object defaults,int depth){
        if(depth>3)return;
        for(Class<?> t=current.getClass();t!=Object.class;t=t.getSuperclass())for(Field f:t.getDeclaredFields()){
            if(Modifier.isStatic(f.getModifiers())||Modifier.isFinal(f.getModifiers())||f.isSynthetic()||Modifier.isTransient(f.getModifiers()))continue;
            try{f.setAccessible(true);Object v=f.get(current),d=f.get(defaults);if(v==null)continue;String name=path+f.getName();
                if(v instanceof Boolean||v instanceof Number||v instanceof String||v instanceof Enum<?>||v instanceof Color){
                    bind(id,name,v,d,()->{try{return f.get(current);}catch(Exception e){throw new IllegalStateException(e);}},x->{try{f.set(current,x);}catch(Exception e){throw new IllegalStateException(e);}},f);
                }else if(v.getClass().getName().endsWith(".util.Key")){
                    add(id,name,label(name),"keybind",((InputConstants.Key)call(d,"get")).getName(),0,0,null,()->((InputConstants.Key)call(v,"get")).getName(),s->call(v,"set",InputConstants.getKey(s)),"Keys");
                }else if(v.getClass().getName().startsWith(current.getClass().getPackageName())&&!name.equals("colors.colors"))fields(id,name+".",v,d,depth+1);
            }catch(IllegalAccessException e){throw new IllegalStateException(e);}
        }
    }
    private static void bind(String id,String name,Object sample,Object def,Supplier<Object> read,Consumer<Object> write,Field f){
        String title=label(name),section=name.toLowerCase().matches(".*(color|opacity|scale|offset|hud|position|width|line|rotation|mainhand|offhand|icon).*" )?"Appearance":"General";
        if(f!=null&&Arrays.stream(f.getAnnotations()).anyMatch(a->a.annotationType().getSimpleName().equals("RequiresRestart")))title+=" (restart)";
        if(sample instanceof Boolean){add(id,name,title,"toggle",def.toString(),0,0,null,()->read.get().toString(),s->write.accept(Boolean.parseBoolean(s)),section);return;}
        if(sample instanceof Enum<?> e){String[] values=Arrays.stream(e.getDeclaringClass().getEnumConstants()).map(Enum::name).toArray(String[]::new);add(id,name,title,"choice",((Enum<?>)def).name(),0,0,values,()->((Enum<?>)read.get()).name(),s->write.accept(Arrays.stream(e.getDeclaringClass().getEnumConstants()).filter(a->a.name().equals(s)).findFirst().orElseThrow()),section);return;}
        if(sample instanceof Color){add(id,name,title,"color",hex(((Color)def).getRGB()),0,0,null,()->hex(((Color)read.get()).getRGB()),s->{Color old=(Color)read.get();write.accept(new Color((old.getAlpha()<<24)|rgb(s),true));},"Appearance");add(id,name+"Alpha",title+" opacity","slider",""+((Color)def).getAlpha(),0,255,null,()->""+((Color)read.get()).getAlpha(),s->{Color old=(Color)read.get();write.accept(new Color((Integer.parseInt(s)<<24)|(old.getRGB()&0xffffff),true));},"Appearance");return;}
        if(sample instanceof Number&&name.toLowerCase().contains("color")){
            add(id,name,title,"color",hex(((Number)def).intValue()),0,0,null,()->hex(((Number)read.get()).intValue()),s->write.accept((((Number)read.get()).intValue()&0xff000000)|rgb(s)),"Appearance");return;
        }
        if(sample instanceof Number n){
            boolean decimal=sample instanceof Float||sample instanceof Double;int factor=decimal?100:1;double[] range=range(id,name,n.doubleValue());
            if(f!=null)for(var a:f.getAnnotations())try{if(a.annotationType().getSimpleName().equals("IntegerOption"))range=new double[]{((Number)call(a,"intMin")).doubleValue(),((Number)call(a,"intMax")).doubleValue()};if(a.annotationType().getSimpleName().equals("BoundedDiscrete"))range=new double[]{((Number)call(a,"min")).doubleValue(),((Number)call(a,"max")).doubleValue()};}catch(RuntimeException ignored){}
            boolean percent=decimal&&(name.toLowerCase().matches(".*(scale|alpha|threshold|strength|size).*"));
            if(decimal&&!percent){double lo=range[0],hi=range[1];add(id,name,title,"decimal",def.toString(),0,0,null,()->read.get().toString(),text->{double value=Double.parseDouble(text);if(!Double.isFinite(value)||value<lo||value>hi)throw new IllegalArgumentException("Use a value between "+lo+" and "+hi);if(sample instanceof Float)write.accept((float)value);else write.accept(value);},section);return;}
            add(id,name,title+(decimal?" %":""),"slider",""+Math.round(((Number)def).doubleValue()*factor),(int)Math.round(range[0]*factor),(int)Math.round(range[1]*factor),null,()->""+Math.round(((Number)read.get()).doubleValue()*factor),s->{double v=Double.parseDouble(s)/factor;Object result;if(sample instanceof Float)result=(float)v;else if(sample instanceof Double)result=v;else if(sample instanceof Long)result=(long)v;else result=(int)v;write.accept(result);},section);return;
        }
        add(id,name,title,"text",def.toString(),0,0,null,()->read.get().toString(),write::accept,section);
    }
    private static double[] range(String id,String key,double value){String k=key.toLowerCase();
        if(k.contains("angle")||k.contains("rotation"))return new double[]{-180,180};
        if(k.equals("hudx")||k.equals("hudy"))return new double[]{0,4096};
        if(k.contains("offset"))return new double[]{-500,500};
        if(k.endsWith(".x")||k.endsWith(".y")||k.endsWith(".z"))return new double[]{-2,2};
        if(id.equals("handtweaks")&&k.contains("scale"))return new double[]{.1,3};
        if(k.endsWith(".size"))return new double[]{.1,10};
        if(k.endsWith("animationspeed"))return new double[]{1,100};
        if(k.contains("scale")||k.equals("outlinemultiplier"))return new double[]{.1,5};
        if(k.contains("alpha")||k.contains("threshold"))return new double[]{0,1};
        if(k.equals("motionblurstrength"))return new double[]{0,2};
        if(k.equals("recordingoverlaytargetfps"))return new double[]{24,240};
        if(k.equals("defaultmaxrowsize"))return new double[]{1,54};
        if(k.equals("line1")||k.equals("line2"))return new double[]{.1,10};
        return new double[]{Math.min(0,value),Math.max(100,value)};
    }
    private static void keys(String id){for(KeyMapping key:Minecraft.getInstance().options.keyMappings){String n=key.getName();if(!(n.contains(id)||id.equals("handtweaks")&&n.contains("tweakhands")||id.equals("naturalmotionblur")&&n.equals("Toggle Motion Blur")))continue;
        String title=net.minecraft.network.chat.Component.translatable(n).getString();add(id,"key."+n,title,"keybind",key.getDefaultKey().getName(),0,0,null,key::saveString,s->{key.setKey(InputConstants.getKey(s));KeyMapping.resetMapping();Minecraft.getInstance().options.save();},"Keys");}}
    private static void walksy(String id){
        Object config=net.fabricmc.loader.api.FabricLoader.getInstance().getEntrypointContainers("walksylib",type("main.walksy.lib.api.WalksyLibApi")).stream().filter(c->c.getProvider().getMetadata().getId().equals(id)).map(c->call(c.getEntrypoint(),"getConfig")).findFirst().orElseThrow();SAVERS.put(id,()->call(config,"saveAndNotify"));
        for(Object category:(List<?>)call(config,"categories"))for(Object group:(List<?>)call(category,"groups"))for(Object option:(List<?>)call(group,"getOptions")){
            Object v=call(option,"getValue"),d=call(option,"getDefaultValue");String name=call(option,"getSaveKey").toString(),title=call(group,"getName")+" · "+call(option,"getName");
            if(v instanceof Boolean){add(id,name,title,"toggle",d.toString(),0,0,null,()->call(option,"getValue").toString(),s->call(option,"setValue",Boolean.parseBoolean(s)),"General");}
            else if(v instanceof Number){add(id,name,title,"slider",d.toString(),((Number)call(option,"getMin")).intValue(),((Number)call(option,"getMax")).intValue(),null,()->call(option,"getValue").toString(),s->call(option,"setValue",Integer.parseInt(s)),"General");}
            else if(v instanceof List<?>){add(id,name,title+" (comma separated)","text",String.join(", ",((List<?>)d).stream().map(Object::toString).toList()),0,0,null,()->String.join(", ",((List<?>)call(option,"getValue")).stream().map(Object::toString).toList()),s->{var values=Arrays.stream(s.split(",")).map(String::trim).filter(t->!t.isEmpty()).toList();call(option,"setValue",values);},"General");}
            else if(v!=null&&v.getClass().getSimpleName().equals("PixelGridAnimation")){
                String key=PREFIX+id+"."+name;ICONS.put(key,v);ICON_RESETS.put(key,()->{Object copy=call(d,"copy");call(option,"setValue",copy);ICONS.put(key,copy);DIRTY.add(id);});Supplier<Object> animation=()->call(option,"getValue");add(id,name,title,"icon","",0,0,null,()->"Edit frames",s->{},"Appearance");
                for(String prop:List.of("OffsetX","OffsetY","Size","AnimationSpeed")){Object value=call(animation.get(),"get"+prop);Object defaults=call(d,"get"+prop);bind(id,name+"."+prop,value,defaults,()->call(animation.get(),"get"+prop),s->{switch(prop){case "OffsetX"->call(animation.get(),"setOffset",((Number)s).doubleValue(),((Number)call(animation.get(),"getOffsetY")).doubleValue());case "OffsetY"->call(animation.get(),"setOffset",((Number)call(animation.get(),"getOffsetX")).doubleValue(),((Number)s).doubleValue());case "Size"->call(animation.get(),"setSize",((Number)s).floatValue());default->call(animation.get(),"setAnimationSpeed",((Number)s).intValue());}},null);}
            }
        }
    }
    private static void shulkerColors(String id,Object registry){Map<?,?> categories=(Map<?,?>)call(registry,"categories");for(var entry:categories.entrySet()){Object category=entry.getValue();for(var item:((Map<?,?>)call(category,"keys")).entrySet()){Object c=item.getValue();String name="containerColor."+entry.getKey()+"."+item.getKey();add(id,name,label(item.getKey().toString())+" color","color",hex(((Number)call(c,"defaultRgb")).intValue()),0,0,null,()->hex(((Number)call(c,"rgb")).intValue()),s->call(c,"setRgb",rgb(s)),"Appearance");}}}
    private static void hitreg(String id){
        Class<?> settings=type("you.jass.betterhitreg.settings.Settings");Properties defaults=(Properties)get(settings,"defaults");SAVERS.put(id,()->{call(settings,"save");call(type("you.jass.betterhitreg.settings.Style"),"updateAll");});
        for(Object t:type("you.jass.betterhitreg.settings.Toggle").getEnumConstants()){String key=call(t,"key").toString();add(id,key,label(call(t,"label").toString()),"toggle",call(t,"defaultValue").toString(),0,0,null,()->call(settings,"get",key).toString(),s->call(settings,"set",key,s),"General");}
        for(Object t:type("you.jass.betterhitreg.settings.Setting").getEnumConstants()){
            String category=call(t,"category").toString();if(category.equals("tracked")||category.equals("toggle"))continue;String key=call(t,"key").toString(),d=call(t,"defaultValue").toString();boolean percent=key.endsWith("_amount");int factor=percent?100:1;
            add(id,key,label(key)+(percent?" %":""),"slider",""+Math.round(Double.parseDouble(d)*factor),0,key.equals("hitreg")?10:key.equals("metronome")?200:100,null,()->""+Math.round(Double.parseDouble(call(settings,"get",key).toString())*factor),s->call(settings,"set",key,percent?""+(Double.parseDouble(s)/factor):s),"General");
        }
        for(Object t:type("you.jass.betterhitreg.settings.Style").getEnumConstants()){
            String key=call(t,"colorKey").toString(),alpha=call(t,"opacityKey").toString();add(id,key,label(key),"color","#"+defaults.getProperty(key),0,0,null,()->"#"+call(settings,"get",key),s->{call(settings,"set",key,s.substring(1));call(type("you.jass.betterhitreg.settings.Style"),"updateAll");},"Appearance");
            add(id,alpha,label(alpha),"slider",defaults.getProperty(alpha),0,255,null,()->call(settings,"get",alpha).toString(),s->{call(settings,"set",alpha,s);call(type("you.jass.betterhitreg.settings.Style"),"updateAll");},"Appearance");
        }
    }
    private static void offline(String id){
        boolean hands=id.equals("handtweaks");Path file=Platform.gameDir().resolve("config/"+(hands?"handtweaks":"naturalmotionblur")+".json");JsonObject data;
        try{data=Files.exists(file)?JsonParser.parseString(Files.readString(file)).getAsJsonObject():new JsonObject();}catch(Exception e){throw new IllegalStateException("Existing configuration could not be read; preserved",e);}
        JsonObject config=data;SAVERS.put(id,()->{try{Files.createDirectories(file.getParent());Path temp=file.resolveSibling(file.getFileName()+".unbox.tmp");Files.writeString(temp,new GsonBuilder().setPrettyPrinting().create().toJson(config));Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new IllegalStateException(e);}});
        if(hands){json(id,config,"globalEnabled",true);for(String hand:List.of("mainHand","offHand")){JsonObject h=config.has(hand)?config.getAsJsonObject(hand):new JsonObject();config.add(hand,h);for(String s:List.of("enabled","hideHands"))json(id,h,hand+"."+s,s.equals("enabled"));for(String s:List.of("x","y","z","scaleX","scaleY","scaleZ","rotationX","rotationY","rotationZ"))json(id,h,hand+"."+s,s.startsWith("scale")?1f:0f);}}
        else{json(id,config,"enabled",true);json(id,config,"refreshRateScaling",true);json(id,config,"motionBlurStrength",1f);json(id,config,"recordingOverlayTargetFPS",60);jsonChoice(id,config,"blurAlgorithm","VELOCITY_BASED",new String[]{"VELOCITY_BASED","FRAME_BLENDING","HYBRID_BLENDING","ACCUMULATION_MAX","ACCUMULATION_MIX"});jsonChoice(id,config,"blurProfile","UNIFORM",new String[]{"UNIFORM","WEIGHTED"});}
    }
    private static void json(String id,JsonObject c,String name,Object def){String key=name.substring(name.lastIndexOf('.')+1);Supplier<Object> read=()->{if(!c.has(key))return def;return def instanceof Boolean?c.get(key).getAsBoolean():def instanceof Float?Float.valueOf(c.get(key).getAsFloat()):Integer.valueOf(c.get(key).getAsInt());};bind(id,name,def,def,read,v->{if(v instanceof Boolean b)c.addProperty(key,b);else c.addProperty(key,(Number)v);},null);}
    private static void jsonChoice(String id,JsonObject c,String key,String def,String[] choices){add(id,key,label(key),"choice",def,0,0,choices,()->c.has(key)?c.get(key).getAsString():def,s->c.addProperty(key,s),"General");}
}
