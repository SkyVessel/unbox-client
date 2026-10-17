package dev.unbox.client;

import com.mojang.blaze3d.platform.InputConstants;
import de.miraculixx.veinminerClient.KeyBindManager;
import de.miraculixx.veinminerClient.constants.KeyBindings;
import de.miraculixx.veinminerClient.network.NetworkManager;
import de.miraculixx.veinminerClient.render.HUDProvider;
import de.miraculixx.veinminerClient.render.HUDRenderer;
import dev.unbox.client.mixin.VeinMinerClientGateMixin;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Real upstream key/state code + real Unbox adapters, without starting a game or renderer. */
public final class VeinMinerToggleTest {
    private static Minecraft mc;
    private static final KeyBindManager manager=KeyBindManager.INSTANCE;
    private static final VeinMinerClientGateMixin gate=new VeinMinerClientGateMixin(){public void onDisconnect(){manager.onDisconnect();}};
    private static KeyMapping key;
    private static final class TestOptions extends Options {
        TestOptions(){super(null,new File("unused"));}
        @Override public void save(){} // Disk persistence is outside this input-state test.
    }
    private static Object allocate(Class<?> type)throws Exception{
        Class<?> unsafeType=Class.forName("sun.misc.Unsafe");Field field=unsafeType.getDeclaredField("theUnsafe");field.setAccessible(true);
        return unsafeType.getMethod("allocateInstance",Class.class).invoke(field.get(null),type);
    }
    private static void set(Class<?> type,Object owner,String name,Object value)throws Exception{Field field=type.getDeclaredField(name);field.setAccessible(true);field.set(owner,value);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void permission(boolean allowed){SurvivalHostMods.receive(new SurvivalHostMods.HostState(allowed,false));}
    private static boolean head(String target)throws Exception{
        CallbackInfo ci=new CallbackInfo(target,true);
        // Execute the actual registered tick HEAD handlers; the normal Mixin bootstrap requires a game loader.
        for(Method method:VeinMinerClientGateMixin.class.getDeclaredMethods()){
            Inject inject=method.getAnnotation(Inject.class);
            if(inject!=null&&Arrays.asList(inject.method()).contains(target)){method.setAccessible(true);method.invoke(gate,ci);if(ci.isCancelled())return false;}
        }
        return true;
    }
    private static void tick()throws Exception{if(head("tick"))manager.tick();}
    private static void disconnect()throws Exception{head("onDisconnect");manager.onDisconnect();}
    private static void down() {var input=InputConstants.getKey(key.saveString());KeyMapping.set(input,true);KeyMapping.click(input);}
    private static void up() {KeyMapping.set(InputConstants.getKey(key.saveString()),false);}
    private static void press()throws Exception{down();tick();up();tick();}
    public static void main(String[] args)throws Exception{
        mc=(Minecraft)allocate(Minecraft.class);set(Minecraft.class,null,"instance",mc);set(Minecraft.class,mc,"options",allocate(TestOptions.class));
        KeyMapping.Category category=new KeyMapping.Category(Identifier.fromNamespaceAndPath("unbox","test"));
        var hold=new KeyMapping("key.veinminer_client.hold",89,category);
        boolean custom=args.length>0;
        var toggle=new KeyMapping("key.veinminer_client.toggle",custom?88:-1,category);
        KeyBindings.INSTANCE.setHold(hold);KeyBindings.INSTANCE.setToggle(toggle);
        HUDProvider.INSTANCE.setInstance(new HUDRenderer(){});
        set(NetworkManager.class,null,"isVeinminerActive",true);
        permission(true);key=SurvivalHostMods.key();require(key!=null,"Unbox exposes a working mining shortcut");
        require(key.saveString().equals(custom?"key.keyboard.x":"key.keyboard.y"),"preserve an existing toggle binding or migrate the old hold shortcut");
        down();tick();require(manager.isPressed(),"first press enables mining");
        up();tick();require(manager.isPressed(),"release must leave vein mining enabled");
        down();tick();require(!manager.isPressed(),"second press disables mining immediately, even before release");
        up();tick();require(!manager.isPressed(),"release after disabling leaves it disabled");
        press();require(manager.isPressed(),"toggle can be enabled again");
        permission(false);tick();require(!manager.isPressed(),"host disabling mining resets active mode");
        down();tick();up();permission(true);tick();require(!manager.isPressed(),"disabled-period clicks cannot reactivate when the host enables mining");
        press();require(manager.isPressed(),"toggle enabled before disconnect");
        down();up();SurvivalHostMods.clearClientHost();disconnect();permission(true);tick();require(!manager.isPressed(),"disconnect/rejoin starts with mining disabled");
        mc.screen=new Screen(Component.empty()){};down();tick();up();mc.screen=null;tick();
        require(!manager.isPressed(),"clicks while a screen is open cannot toggle mining after closing it");
        press();require(manager.isPressed(),"screen-open check must not suppress subsequent gameplay presses");
        mc.screen=new Screen(Component.empty()){};down();tick();up();mc.screen=null;tick();
        require(manager.isPressed(),"screen key presses preserve an already enabled mode");
        SocialLogin.trusted=false;tick();require(!manager.isPressed(),"ordinary public-server context disables mining");
        down();tick();up();SocialLogin.trusted=true;permission(true);tick();require(!manager.isPressed(),"untrusted-context clicks are discarded");
        System.out.println("PASS: release persistence, second press, host disable, disconnect, screen input, public-server gate");
    }
}
