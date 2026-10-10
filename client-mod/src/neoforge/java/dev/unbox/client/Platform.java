package dev.unbox.client;
import java.nio.file.Path;
import java.util.function.Consumer;
import net.minecraft.client.*;
import net.minecraft.resources.Identifier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.*;
public final class Platform {
 static IEventBus bus;
 public record Mod(String name,String version){}
 public static Path gameDir(){return FMLPaths.GAMEDIR.get();}public static Path configDir(){return FMLPaths.CONFIGDIR.get();}
 public static String loader(){return "neoforge";}public static String loaderVersion(){return "26.1.0.19-beta";}
 public static boolean loaded(String id){return ModList.get().isLoaded(id);}
 public static Mod mod(String id){return ModList.get().getModContainerById(id).map(c->new Mod(c.getModInfo().getDisplayName(),c.getModInfo().getVersion().toString())).orElse(null);}
 public static KeyMapping key(KeyMapping k){bus.addListener((RegisterKeyMappingsEvent e)->e.register(k));return k;}
 public static void onTick(Consumer<Minecraft> c){NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->c.accept(Minecraft.getInstance()));}
 public static void hud(Identifier id,java.util.function.BiConsumer<GuiGraphicsExtractor,DeltaTracker> draw){bus.addListener((RegisterGuiLayersEvent e)->e.registerAboveAll(id,draw::accept));}
}
