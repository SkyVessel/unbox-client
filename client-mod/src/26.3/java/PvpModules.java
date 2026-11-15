package dev.unbox.client;
import java.util.*;
import net.minecraft.client.gui.screens.Screen;
/** 26.3 intentionally has no PvP catalogue. The Fabric backport supplies the implementation. */
public final class PvpModules {
 public static boolean available(){return false;}
 public static boolean contains(String id){return false;}
 public static List<ModOptions.Mod> entries(){return List.of();}
 public static boolean shared(String id){return Set.of("fps","cps","ping","keystrokes","armor","sprint","zoom","crosshair","chat","scoreboard","coordinates","viewmodel","daytime").contains(id);}
 public static boolean enabled(String id){return false;}
 public static boolean toggle(String id){return false;}
 public static String error(){return "";}
 public static String status(String id){return "";}
 static boolean configure(String id,Screen parent){return false;}
 public static void open(String id,Screen parent){}
}
