package dev.unbox.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Host-only adapters to the pinned, native FallingTree and VeinMiner engines. */
public final class SurvivalHostMods {
    private static final Map<String,String> errors=new ConcurrentHashMap<>();
    private static volatile MinecraftServer configuredServer;
    private static volatile boolean veinReady,treeReady,remoteKnown,remoteVein,remoteTree;
    private static String lastSettings="";
    private static HostState lastState;
    private static int ticks;
    private static KeyMapping veinKey;
    private SurvivalHostMods(){}

    public record HostState(boolean veinminer,boolean fallingtree) implements CustomPacketPayload {
        static final Type<HostState> TYPE=new Type<>(Identifier.fromNamespaceAndPath("unbox","survival_host_features"));
        static final StreamCodec<RegistryFriendlyByteBuf,HostState> CODEC=CustomPacketPayload.codec(
            (packet,buffer)->{buffer.writeBoolean(packet.veinminer);buffer.writeBoolean(packet.fallingtree);},
            buffer->new HostState(buffer.readBoolean(),buffer.readBoolean()));
        public Type<HostState> type(){return TYPE;}
    }
    private static boolean managed(String module){return module.equals("veinminer")||module.equals("fallingtree");}
    public static boolean available(String module){return !managed(module)||(Platform.loaded(module)&&!errors.containsKey(module));}
    public static String reason(String module){
        if(!managed(module))return "";
        if(errors.containsKey(module))return errors.get(module);
        if(module.equals("veinminer")&&!Platform.loaded(module)&&Platform.loader().equals("neoforge")&&Platform.mod("minecraft").version().equals("26.1"))return "VeinMiner is unavailable for this NeoForge 26.1 runtime";
        return Platform.loaded(module)?"":"Launch with Unbox to install "+(module.equals("veinminer")?"VeinMiner":"FallingTree");
    }
    public static boolean hostKnown(){return Minecraft.getInstance().getSingleplayerServer()!=null||remoteKnown&&trustedRemote();}
    public static boolean hostEnabled(String module){
        if(Minecraft.getInstance().getSingleplayerServer()!=null)return serverEnabled(module);
        return remoteKnown&&trustedRemote()&&(module.equals("veinminer")?remoteVein:module.equals("fallingtree")&&remoteTree);
    }
    private static boolean trustedRemote(){
        Minecraft mc=Minecraft.getInstance();
        return SocialLogin.trustedClientSession()||mc.getCurrentServer()!=null&&mc.getCurrentServer().isLan();
    }
    static void receive(HostState state){
        if(!trustedRemote()||Minecraft.getInstance().getSingleplayerServer()!=null)return;
        remoteVein=state.veinminer;remoteTree=state.fallingtree;remoteKnown=true;
    }
    static void clearClientHost(){remoteKnown=false;remoteVein=false;remoteTree=false;}
    static void joined(ServerPlayer player){
        if(player.level().getServer().isSingleplayer()&&MapTransport.canSendSurvivalHostState(player))
            MapTransport.sendSurvivalHostState(player,new HostState(serverEnabled("veinminer"),serverEnabled("fallingtree")));
    }
    public static boolean serverEnabled(String module){
        MinecraftServer current=Minecraft.getInstance().getSingleplayerServer();
        return current!=null&&current==configuredServer&&current.isSingleplayer()&&available(module)
            &&ClientFeaturePolicy.configured(module)&&(module.equals("veinminer")?veinReady:module.equals("fallingtree")&&treeReady);
    }
    public static boolean veinClientActive(){return hostEnabled("veinminer")&&Platform.loaded("veinminer_client")&&!errors.containsKey("veinminer");}
    public static KeyMapping key(){
        if(veinKey!=null)return veinKey;
        if(!Platform.loaded("veinminer_client"))return null;
        try{
            Object bindings=singleton("de.miraculixx.veinminerClient.constants.KeyBindings");
            KeyMapping toggle=(KeyMapping)call(bindings,"getToggle"),hold=(KeyMapping)call(bindings,"getHold");
            if(toggle==null||hold==null)return null;
            // Reuse the native toggle and preserve the old shortcut on first migration.
            // Leaving hold bound would keep mining active during the second key press.
            if(!hold.isUnbound()){
                if(toggle.isUnbound())toggle.setKey(InputConstants.getKey(hold.saveString()));
                hold.setKey(InputConstants.UNKNOWN);hold.setDown(false);
                while(hold.consumeClick()){}
                KeyMapping.resetMapping();Minecraft.getInstance().options.save();
            }
            veinKey=toggle;return veinKey;
        }
        catch(ReflectiveOperationException|RuntimeException|LinkageError e){return null;}
    }
    public static void tick(Minecraft minecraft){
        MinecraftServer server=minecraft.getSingleplayerServer();
        if(server==null){configuredServer=null;veinReady=false;treeReady=false;lastState=null;lastSettings="";return;}
        if(configuredServer!=server){configuredServer=server;veinReady=false;treeReady=false;lastSettings="";lastState=null;ticks=0;}
        if(ticks++%20!=0)return;
        String settings=ClientFeaturePolicy.configured("veinminer")+":"+ClientFeaturePolicy.configured("fallingtree")+":"+
            UnboxClient.number("veinminer.maxChain",64,1,256)+":"+UnboxClient.number("veinminer.cooldown",20,0,100)+":"+
            ClientHud.flag("veinminer.mustSneak",false)+":"+ClientHud.flag("veinminer.correctTool",true)+":"+
            UnboxClient.number("fallingtree.maxSize",128,16,512)+":"+ClientHud.flag("fallingtree.leaves",true)+":"+ClientHud.flag("fallingtree.requireSneak",false);
        boolean changed=!settings.equals(lastSettings);lastSettings=settings;
        server.execute(()->{
            if(configuredServer!=server)return;
            // Reapply owned options so an upstream command cannot bypass the host controls.
            syncVein(changed);syncTree();
            HostState state=new HostState(serverEnabled("veinminer"),serverEnabled("fallingtree"));
            if(!state.equals(lastState)){lastState=state;for(ServerPlayer player:server.getPlayerList().getPlayers())joined(player);}
        });
    }
    private static void syncVein(boolean changed){
        if(!available("veinminer")){veinReady=false;return;}
        try{
            Object manager=singleton("de.miraculixx.veinminer.config.ConfigManager");Object settings=call(manager,"getSettings");
            set(settings,"setMaxChain",int.class,UnboxClient.number("veinminer.maxChain",64,1,256));
            set(settings,"setCooldown",int.class,UnboxClient.number("veinminer.cooldown",20,0,100));
            set(settings,"setMustSneak",boolean.class,ClientHud.flag("veinminer.mustSneak",false));
            set(settings,"setNeedCorrectTool",boolean.class,ClientHud.flag("veinminer.correctTool",true));
            set(settings,"setAutoUpdate",boolean.class,false);
            Object client=call(settings,"getClient");
            set(client,"setAllow",boolean.class,true);
            Object overrides=call(client,"getOverrides");
            set(overrides,"setMaxChain",Integer.class,null);
            set(overrides,"setCooldown",Integer.class,null);
            set(overrides,"setMustSneak",Boolean.class,null);
            set(overrides,"setNeedCorrectTool",Boolean.class,null);
            Object host=call(singleton("de.miraculixx.veinminer.command.ActiveHost"),"getHost");
            Class.forName("de.miraculixx.veinminer.command.VeinminerHost").getMethod("setActive",boolean.class)
                .invoke(host,ClientFeaturePolicy.configured("veinminer"));
            veinReady=true;
            if(changed){Method notify=manager.getClass().getDeclaredMethod("onAfterReload");notify.setAccessible(true);notify.invoke(manager);}
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){veinReady=false;failed("veinminer",e);}
    }
    private static void syncTree(){
        if(!available("fallingtree")){treeReady=false;return;}
        try{
            Class<?> entry=Class.forName("fr.rakambda.fallingtree."+(Platform.loader().equals("neoforge")?"neoforge":"fabric")+".FallingTree");
            Object mod=entry.getMethod("getMod").invoke(null);Object config=call(mod,"getOwnConfiguration");Object trees=call(config,"getTrees");
            boolean enabled=ClientFeaturePolicy.configured("fallingtree");
            set(trees,"setTreeBreaking",boolean.class,enabled);
            set(trees,"setLeavesBreaking",boolean.class,enabled&&ClientHud.flag("fallingtree.leaves",true));
            set(trees,"setMaxSize",int.class,UnboxClient.number("fallingtree.maxSize",128,16,512));
            Class<?> mode=Class.forName("fr.rakambda.fallingtree.common.config.enums.SneakMode");
            Object sneak=mode.getField(ClientHud.flag("fallingtree.requireSneak",false)?"SNEAK_ENABLE":"SNEAK_DISABLE").get(null);
            set(config,"setSneakMode",mode,sneak);
            treeReady=true;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError e){treeReady=false;failed("fallingtree",e);}
    }
    public static Object abortedTree(){
        try{return Class.forName("fr.rakambda.fallingtree.common.tree.AbortedResult").getField("NOT_ENABLED").get(null);}
        catch(ReflectiveOperationException e){throw new IllegalStateException("Unsupported FallingTree engine",e);}
    }
    private static Object singleton(String type)throws ReflectiveOperationException{return Class.forName(type).getField("INSTANCE").get(null);}
    private static Object call(Object object,String method)throws ReflectiveOperationException{return object.getClass().getMethod(method).invoke(object);}
    private static void set(Object object,String method,Class<?> type,Object value)throws ReflectiveOperationException{object.getClass().getMethod(method,type).invoke(object,value);}
    private static void failed(String module,Throwable e){errors.put(module,"Integration unavailable; check the installed mod version");System.err.println("[Unbox] "+module+" integration disabled: "+e);}
}
