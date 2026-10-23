package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import java.nio.file.Files;
import java.util.*;

/** Explicit, hidden disposable-world integration checks against the downloaded public JAR. */
final class PublicReplacementSmokeTest {
    private static int ticks;
    private static boolean done;
    private static final List<String> checks = new ArrayList<>();
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); checks.add(label); }
    static void tick(Minecraft mc) {
        if (done || mc.player == null || mc.getSingleplayerServer() == null || ++ticks < 80) return;
        done = true;
        Properties saved = new Properties();saved.putAll(UnboxClient.CONFIG);
        Throwable failure = null;
        try {
            mc.setScreen(null);
            check(FreecamController.available(), "Pinned public Freecam loaded on " + Platform.loader() + " " + Platform.mod("minecraft").version());
            check(FreecamController.key()!=null, "Right Shift keybind edits the native Freecam key mapping");
            check(Arrays.stream(mc.options.keyMappings).noneMatch(k->k.getName().equals("key.unbox.freecam")), "No duplicate legacy Freecam key registered");
            UnboxClient.set("freecam.key","key.keyboard.f7");ModuleBindings.apply();
            check(FreecamController.key().saveString().equals("key.keyboard.f7"),"Existing Unbox keybind migrates to the native key");
            var nativeType=Class.forName("net.xolt.freecam.Freecam");
            var toggle=nativeType.getMethod("toggle");
            var post=nativeType.getMethod("postTick",Minecraft.class);
            var tripodType=Class.forName("net.xolt.freecam.tripod.TripodSlot");
            var tripod=nativeType.getDeclaredMethod("toggleTripod",tripodType);tripod.setAccessible(true);
            var slot=Arrays.stream(tripodType.getEnumConstants()).filter(e->!((Enum<?>)e).name().equals("NONE")).findFirst().orElseThrow();
            var originalCamera=mc.getCameraEntity();var input=mc.player.input;var perspective=mc.options.getCameraType();
            UnboxClient.set("freecam",false);
            for(int i=0;i<5;i++){toggle.invoke(null);if(mc.getCameraEntity()!=originalCamera)throw new AssertionError("Disabled toggle changed camera");tripod.invoke(null,slot);if(mc.getCameraEntity()!=originalCamera)throw new AssertionError("Disabled tripod changed camera");}
            check(!FreecamController.active()&&mc.getCameraEntity()==originalCamera&&mc.player.input==input,"Disabled regular/tripod entry never creates a camera or replaces body input");
            KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(FreecamController.key().saveString()));post.invoke(null,mc);
            check(!FreecamController.active(),"Disabled native key handler does not activate Freecam");
            FreecamController.key().setDown(true);
            var hotbar=mc.options.keyHotbarSlots[0];KeyMapping.click(com.mojang.blaze3d.platform.InputConstants.getKey(hotbar.saveString()));post.invoke(null,mc);
            check(hotbar.consumeClick(),"Disabled tripod shortcut leaves hotbar input untouched");FreecamController.key().setDown(false);
            UnboxClient.set("freecam",true);post.invoke(null,mc);
            check(!FreecamController.active(),"Rejected clicks are not replayed on re-enable");
            mc.setScreen(new InventoryScreen(mc.player));toggle.invoke(null);
            check(!FreecamController.active(),"Inventory-screen input cannot activate a camera");mc.setScreen(null);
            toggle.invoke(null);
            check(FreecamController.active()&&mc.getCameraEntity()!=originalCamera,"Enabled input enters the upstream camera");
            FreecamIntegrationChecks.verify(mc,checks);
            var config=Class.forName("net.xolt.freecam.config.ModConfig");var settings=config.getMethod("get").invoke(null);
            check(config.getMethod("getFlightMode").invoke(settings).toString().equals("CREATIVE"),"Fresh installs use native inertial flight");
            check((boolean)config.getMethod("shouldShowPlayer").invoke(settings),"Upstream defaults retain the visible player body");
            UnboxClient.set("freecam",false);FreecamController.tick(mc);
            check(!FreecamController.active()&&mc.getCameraEntity()==originalCamera&&mc.player.input==input&&mc.options.getCameraType()==perspective,"Disabling restores camera, input and perspective");
            check(FreecamController.openSettings(new UnboxScreen()).isEmpty()&&mc.screen!=null,"Right Shift opens the native Freecam settings page");
            var parent=mc.screen;mc.screen.onClose();check(mc.screen!=parent,"Closing native settings returns to the parent");mc.setScreen(null);
            check(!ClientFeaturePolicy.allowed(ClientFeaturePolicy.Scope.TRUSTED_GAMEPLAY,false,false,false),"Public servers remain disallowed by session policy");
            check(ClientFeaturePolicy.allowed(ClientFeaturePolicy.Scope.TRUSTED_GAMEPLAY,false,false,true),"Verified private rooms permit Freecam");
            check(EnvironmentManifest.scan(mc.gameDirectory.toPath()).manifest()!=null,"Managed public Freecam coexists with the shared environment scan");
        } catch (Throwable e) { failure=e;e.printStackTrace(); }
        finally { FreecamController.stop();UnboxClient.CONFIG.clear();UnboxClient.CONFIG.putAll(saved); }
        try { Files.writeString(mc.gameDirectory.toPath().resolve("logs/replacement-smoke.json"),new com.google.gson.Gson().toJson(Map.of("ok",failure==null,"checks",checks,"error",failure==null?"":failure.toString()))); }
        catch(Exception e){e.printStackTrace();}
        mc.stop();
    }
}
