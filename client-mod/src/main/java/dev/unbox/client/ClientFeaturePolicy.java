package dev.unbox.client;

import net.minecraft.client.Minecraft;
import java.util.Set;

/** Central world/session policy for modules which can affect server-observed play. */
public final class ClientFeaturePolicy {
    public enum Scope { LOCAL_VISUAL, TRUSTED_GAMEPLAY, HOST_MANAGED }

    private static final Set<String> TRUSTED = Set.of(
        "freecam", "inventory", "radar", "cave", "entityRadar"
    );
    private static final Set<String> HOST = Set.of("gravestones","veinminer","fallingtree");

    private ClientFeaturePolicy() {}

    public static Scope scope(String key) {
        String module = module(key);
        if (HOST.contains(module)) return Scope.HOST_MANAGED;
        if (TRUSTED.contains(module)) return Scope.TRUSTED_GAMEPLAY;
        return Scope.LOCAL_VISUAL;
    }

    /** This method never reads UnboxClient.enabled(), so callers can safely gate enabled(). */
    public static boolean allowed(String key) {
        if(!SurvivalClientMods.available(module(key))) return false;
        if(!SurvivalHostMods.available(module(key))) return false;
        if(module(key).equals("dynamiclight")) return Platform.loaded("wick");
        Minecraft mc = Minecraft.getInstance();
        return allowed(scope(key), mc.getSingleplayerServer() != null, recognizedLan(mc), SocialLogin.trustedClientSession());
    }

    static boolean allowed(Scope scope, boolean singleplayer, boolean recognizedLan, boolean trustedRoom) {
        return switch (scope) {
            case LOCAL_VISUAL -> true;
            case TRUSTED_GAMEPLAY -> singleplayer || recognizedLan || trustedRoom;
            case HOST_MANAGED -> singleplayer;
        };
    }

    public static String reason(String key) {
        if (allowed(key)) return "";
        if(!SurvivalClientMods.available(module(key))) return SurvivalClientMods.reason(module(key));
        if(!SurvivalHostMods.available(module(key))) return SurvivalHostMods.reason(module(key));
        if(module(key).equals("dynamiclight")) return "Launch with Unbox to install Wick";
        return switch (scope(key)) {
            case LOCAL_VISUAL -> "";
            case TRUSTED_GAMEPLAY -> "Available in singleplayer, LAN and verified Unbox rooms";
            case HOST_MANAGED -> "Managed by the Unbox room host";
        };
    }

    public static boolean configured(String key) {
        return Boolean.parseBoolean(UnboxClient.CONFIG.getProperty(key, "false"));
    }

    private static String module(String key) {
        int dot = key.indexOf('.');
        return dot < 0 ? key : key.substring(0, dot);
    }

    private static boolean recognizedLan(Minecraft mc) {
        // Uses Minecraft's discovery/type metadata. Addresses are deliberately never inspected.
        return mc.getCurrentServer() != null && mc.getCurrentServer().isLan();
    }
}
