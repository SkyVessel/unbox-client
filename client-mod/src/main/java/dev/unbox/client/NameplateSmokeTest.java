package dev.unbox.client;

import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Opt-in visual regression helper; invoked only in the isolated visual smoke world. */
final class NameplateSmokeTest {
    private static CameraType previous;
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static EntityRenderState state(Minecraft mc, Player player) {
        EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(player);
        return renderer.createRenderState(player, 1);
    }
    private static void check(boolean ok, String label, List<String> checks) {
        if (!ok) throw new IllegalStateException(label);
        checks.add(label);
    }
    static void verify(Minecraft mc, List<String> checks) {
        previous = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        check(state(mc, mc.player).nameTag == null, "First person does not put the own nameplate in the camera", checks);
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        var own = state(mc, mc.player);
        check(own.nameTag != null && own.nameTag.getString().equals("\ue000 Builder"), "Third-person renderer contains own nickname and Unbox logo together", checks);
        check(own.nameTagAttachment != null, "Own nameplate has a native head attachment", checks);
        UnboxClient.set("nickname", false);
        check(state(mc, mc.player).nameTag.getString().equals("\ue000 " + mc.player.getDisplayName().getString()), "Disabling nickname restores real name without removing Unbox logo", checks);
        UnboxClient.set("nickname", true);
        mc.options.hideGui = true;
        check(state(mc, mc.player).nameTag == null, "F1 hides own nameplate", checks);
        mc.options.hideGui = false;
        var remote = new RemotePlayer(mc.level, new GameProfile(UUID.randomUUID(), "OtherPlayer"));
        remote.setPos(mc.player.position().add(2, 0, 0));
        remote.setOldPosAndRot();
        var unknown = state(mc, remote);
        check(unknown.nameTag != null && !unknown.nameTag.getString().contains("\ue000"), "Unknown client has no false Unbox badge", checks);
        NameBadges.receive(new NameBadges.Packet(List.of(remote.getUUID())));
        var known = state(mc, remote);
        check(known.nameTag != null && known.nameTag.getString().equals("\ue000 OtherPlayer"), "Capability-recognized remote player has logo and keeps their own name", checks);
        NameBadges.receive(new NameBadges.Packet(List.of()));
    }
    static void restore(Minecraft mc) { if (previous != null) mc.options.setCameraType(previous); }
}
