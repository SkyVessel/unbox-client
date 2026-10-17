package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.UUID;

/** UUID-scoped local display alias. It never mutates a GameProfile or outgoing text. */
public final class LocalNickname {
    private LocalNickname() {}

    public static String get() {
        Minecraft mc = Minecraft.getInstance();
        return get(mc.getUser().getProfileId());
    }

    public static String get(UUID uuid) {
        String value = UnboxClient.CONFIG.getProperty(key(uuid), "").strip();
        return valid(value) ? value : "";
    }

    public static void set(String value) {
        UUID uuid = Minecraft.getInstance().getUser().getProfileId();
        String clean = value == null ? "" : value.strip();
        if (!clean.isEmpty() && !valid(clean)) throw new IllegalArgumentException("Nickname must be 1–16 letters, numbers, spaces, _ or -");
        if (clean.isEmpty()) UnboxClient.CONFIG.remove(key(uuid));
        else UnboxClient.CONFIG.setProperty(key(uuid), clean);
        UnboxClient.save();
    }

    public static Component render(UUID player, Component original) {
        Minecraft mc = Minecraft.getInstance();
        UUID own = mc.player == null ? mc.getUser().getProfileId() : mc.player.getUUID();
        if (!UnboxClient.enabled("nickname") || !own.equals(player)) return original;
        String alias = get();
        return alias.isEmpty() ? original : Component.literal(alias).withStyle(original.getStyle());
    }

    private static String key(UUID uuid) { return "nickname." + uuid.toString().replace("-", ""); }
    private static boolean valid(String value) { return value.length() <= 16 && value.codePoints().allMatch(c -> Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-'); }
}
