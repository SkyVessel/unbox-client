package dev.unbox.client;

import net.minecraft.network.chat.Component;

/** Rendering-only scoreboard settings consumed by ScoreboardAppearanceMixin. */
public final class ScoreboardAppearance {
    private ScoreboardAppearance() {}
    public static int background(boolean title) {
        int rgb = ClientHud.color(title ? "scoreboard.titleBackground" : "scoreboard.backgroundColor", "#000000") & 0xffffff;
        int alpha = UnboxClient.number(title ? "scoreboard.titleOpacity" : "scoreboard.opacity", title ? 50 : 35, 0, 100) * 255 / 100;
        return (alpha << 24) | rgb;
    }
    public static int text(boolean title, boolean score) {
        String key = title ? "scoreboard.titleColor" : score ? "scoreboard.scoreColor" : "scoreboard.textColor";
        return 0xff000000 | (ClientHud.color(key, "#FFFFFF") & 0xffffff);
    }
    public static boolean shadow() { return ClientHud.flag("scoreboard.shadow", true); }
    public static boolean showTitle() { return ClientHud.flag("scoreboard.showTitle", true); }
    public static boolean showScores() { return ClientHud.flag("scoreboard.showScores", true); }
    public static Component tint(Component value, boolean title, boolean score) {
        return Component.literal(value.getString()).withColor(text(title, score));
    }
}
