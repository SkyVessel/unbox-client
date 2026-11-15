package dev.unbox.client;
import java.util.List;
import net.minecraft.client.gui.screens.Screen;
/** PvP settings are supplied only by the native 1.21.11 Fabric implementation. */
public final class PvpSettings {
 public static boolean supports(String id){return false;}
 public static boolean owns(String key){return false;}
 public static String value(String key,String fallback){return fallback;}
 public static void write(String key,String value){}
 public static void flush(){}
 public static List<String> tabs(String id){return List.of();}
 public static List<ModOptions.Option> options(String id,String section){return List.of();}
 public static String notice(String id){return "";}
 public static void reset(String id){}
 public static void resetOption(ModOptions.Option o){}
 public static void openIcon(String key,Screen parent){}
}
