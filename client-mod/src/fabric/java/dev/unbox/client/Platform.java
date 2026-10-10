package dev.unbox.client;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
public final class Platform {
 public record Mod(String name,String version){}
 public static Path gameDir(){return FabricLoader.getInstance().getGameDir();}
 public static Path configDir(){return FabricLoader.getInstance().getConfigDir();}
 public static String loader(){return "fabric";}public static String loaderVersion(){return "0.19.5";}
 public static boolean loaded(String id){return FabricLoader.getInstance().isModLoaded(id);}
 public static Mod mod(String id){return FabricLoader.getInstance().getModContainer(id).map(m->new Mod(m.getMetadata().getName(),m.getMetadata().getVersion().getFriendlyString())).orElse(null);}
 public static KeyMapping key(KeyMapping k){return KeyMappingHelper.registerKeyMapping(k);}
 public static void onTick(Consumer<Minecraft> c){ClientTickEvents.END_CLIENT_TICK.register(c::accept);}
 public static void hud(Identifier id,java.util.function.BiConsumer<GuiGraphicsExtractor,DeltaTracker> draw){HudElementRegistry.addLast(id,draw::accept);}
}
