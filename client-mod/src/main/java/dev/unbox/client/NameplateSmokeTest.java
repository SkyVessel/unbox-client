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
    private static CameraType previous;private static com.google.gson.JsonObject previousView;private static byte[] previousBridge;
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
        previousView=SocialBridge.view.deepCopy();var fixture=new com.google.gson.JsonObject();fixture.addProperty("connected",true);fixture.addProperty("updated",System.currentTimeMillis());var self=new com.google.gson.JsonObject();self.addProperty("uuid",mc.getUser().getProfileId().toString().replace("-",""));self.addProperty("kind","microsoft");fixture.add("self",self);var logos=new com.google.gson.JsonObject();var crown=new com.google.gson.JsonObject();crown.addProperty("id","unbox-creator-crown");var cash=new com.google.gson.JsonObject();cash.addProperty("id","unbox-fluttering-cash");logos.add(mc.player.getUUID().toString().replace("-",""),crown);logos.add(remote.getUUID().toString().replace("-",""),cash);fixture.add("playerLogos",logos);SocialBridge.view=fixture;
        check(state(mc,mc.player).nameTag.getString().equals("\ue001 Builder"),"Equipped crown appears in the real own-name renderer",checks);
        check(state(mc,remote).nameTag.getString().equals("\ue002 OtherPlayer"),"Another player's equipped cash appears in the real remote-name renderer",checks);
        try {
            var sourceMethod=net.minecraft.client.gui.Font.class.getDeclaredMethod("getGlyphSource",net.minecraft.network.chat.FontDescription.class);sourceMethod.setAccessible(true);
            var source=(net.minecraft.client.gui.GlyphSource)sourceMethod.invoke(mc.font,new net.minecraft.network.chat.FontDescription.Resource(net.minecraft.resources.Identifier.fromNamespaceAndPath("unbox","name_badge")));
            var missing=source.getGlyph(0x10ffff);
            for(int code:new int[]{0xe000,0xe001,0xe002})check(source.getGlyph(code)!=missing,"Badge U+"+Integer.toHexString(code)+" bakes without the missing-glyph fallback",checks);
        } catch(ReflectiveOperationException e){throw new RuntimeException(e);}
        logos.remove(remote.getUUID().toString().replace("-",""));check(state(mc,remote).nameTag.getString().equals("\ue000 OtherPlayer"),"Cleared or revoked remote equipment returns to the basic badge",checks);
        fixture.addProperty("updated",0);check(state(mc,mc.player).nameTag.getString().equals("\ue000 Builder"),"Stale cloud data cannot keep a custom badge",checks);fixture.addProperty("updated",System.currentTimeMillis());self.addProperty("uuid",UUID.randomUUID().toString());check(state(mc,mc.player).nameTag.getString().equals("\ue000 Builder"),"Another viewer account cannot supply the badge mapping",checks);self.addProperty("uuid",mc.getUser().getProfileId().toString().replace("-",""));
        try{var path=mc.gameDirectory.toPath().resolve("unbox-social-view.json");previousBridge=java.nio.file.Files.exists(path)?java.nio.file.Files.readAllBytes(path):null;java.nio.file.Files.writeString(path,fixture.toString());}catch(java.io.IOException e){throw new RuntimeException(e);}
        NameBadges.receive(new NameBadges.Packet(List.of()));
    }
    static void restore(Minecraft mc) { if (previous != null) mc.options.setCameraType(previous);if(previousView!=null){SocialBridge.view=previousView;try{var path=mc.gameDirectory.toPath().resolve("unbox-social-view.json");if(previousBridge==null)java.nio.file.Files.deleteIfExists(path);else java.nio.file.Files.write(path,previousBridge);}catch(java.io.IOException e){throw new RuntimeException(e);}} }
}
