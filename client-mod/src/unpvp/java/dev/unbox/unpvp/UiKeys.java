package dev.unbox.unpvp;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
/** The panel edits the real Minecraft bindings. A newly assigned key wins every conflict. */
final class UiKeys {
 static final Map<String,KeyBinding> toggles=new LinkedHashMap<>();
 static void register(){for(UiState.Module m:UiState.MODULES)if(!m.id.equals("performance")&&!m.id.equals("resources")){KeyBinding key=new KeyBinding("Toggle "+m.title,0,"Unbox modules");toggles.put(m.id,key);ClientRegistry.registerKeyBinding(key);}}
 static KeyBinding binding(String id){Minecraft mc=Minecraft.getMinecraft();if(id.endsWith(".toggle"))return toggles.get(id.substring(0,id.length()-7));if(id.equals("sprint"))return mc.gameSettings.keyBindSprint;if(id.equals("sneak"))return mc.gameSettings.keyBindSneak;String description=id.equals("zoom")?"Unbox zoom":id.equals("lookback")?"Unbox look back":"Unbox menu";for(KeyBinding k:mc.gameSettings.keyBindings)if(k.getKeyDescription().equals(description))return k;return null;}
 static String label(String id){KeyBinding key=binding(id);return key==null?"Unbound":net.minecraft.client.settings.GameSettings.getKeyDisplayString(key.getKeyCode());}
 static int assign(String id,int code){KeyBinding target=binding(id);if(target==null)return 0;int replaced=0;for(KeyBinding k:Minecraft.getMinecraft().gameSettings.keyBindings)if(k!=target&&code!=0&&k.getKeyCode()==code){k.setKeyCode(0);replaced++;}target.setKeyCode(code);KeyBinding.unPressAllKeys();KeyBinding.resetKeyBindingArrayAndHash();Minecraft.getMinecraft().gameSettings.saveOptions();return replaced;}
 static void tick(){Minecraft mc=Minecraft.getMinecraft();for(Map.Entry<String,KeyBinding> e:toggles.entrySet())while(e.getValue().isPressed())if(mc.theWorld!=null&&mc.currentScreen==null&&mc.inGameHasFocus){UiState.set(e.getKey(),!UiState.on(e.getKey()));UiState.save();}}
}
