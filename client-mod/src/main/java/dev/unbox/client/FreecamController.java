package dev.unbox.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ScrollWheelHandler;
import java.nio.file.Files;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.lang.reflect.Method;
import java.util.Arrays;

/** Unbox policy and settings entry point; the public Freecam mod owns all camera movement. */
public final class FreecamController {
    private static Method isEnabled, toggle, camera;
    private static boolean resolved, prepared;
    private static double speed = 1;
    private static ScrollWheelHandler wheel = new ScrollWheelHandler();
    private static String error = "";
    private FreecamController() {}

    private static void resolve() {
        if (resolved || !Platform.loaded("freecam")) return;
        resolved = true;
        try {
            Class<?> type = Class.forName("net.xolt.freecam.Freecam");
            isEnabled = type.getMethod("isEnabled");
            toggle = type.getMethod("toggle");
            camera = type.getMethod("getFreeCamera");
        } catch (ReflectiveOperationException e) {
            error = "Freecam integration unavailable";
            System.err.println("[Unbox] " + error + ": " + e);
        }
    }
    public static boolean available() { resolve(); return Platform.loaded("freecam") && error.isEmpty(); }
    public static String reason() { return error.isEmpty() ? "Launch with Unbox to install Freecam" : error; }
    public static KeyMapping key() {
        return Arrays.stream(Minecraft.getInstance().options.keyMappings)
            .filter(k -> k.getName().equals("key.freecam.toggle")).findFirst().orElse(null);
    }
    public static void tick(Minecraft mc) {
        resolve();
        prepareDefaults();
        if (!active()) return;
        // Only clean up an already-running camera after permission/session changes.
        // FreecamEntryGateMixin rejects disabled input BEFORE any camera is created.
        if (!UnboxClient.enabled("freecam") || mc.player == null || !mc.player.isAlive()) stop();
        else if (Freelook.active()) Freelook.suspend();
    }
    /** Set familiar inertial flight only for a fresh upstream config; keep existing native choices. */
    private static void prepareDefaults() {
        if (prepared || !available()) return;
        prepared=true;
        if (Files.exists(Platform.configDir().resolve("freecam.json")) || Files.exists(Platform.configDir().resolve("freecam.json5"))) return;
        try {
            Object config=Class.forName("net.xolt.freecam.config.ModConfig").getMethod("get").invoke(null);
            Object data=config.getClass().getMethod("getData").invoke(config);
            Object movement=data.getClass().getField("movement").get(data);
            Class<?> mode=Class.forName("net.xolt.freecam.config.model.FlightMode");
            Object creative=Arrays.stream(mode.getEnumConstants()).filter(e->((Enum<?>)e).name().equals("CREATIVE")).findFirst().orElseThrow();
            movement.getClass().getField("flightMode").set(movement,creative);
            movement.getClass().getField("horizontalSpeed").setDouble(movement,.5);
            movement.getClass().getField("verticalSpeed").setDouble(movement,.5);
            Object controller=Class.forName("net.xolt.freecam.config.controller.ConfigControllerRegistry").getMethod("get",Class.class).invoke(null,data.getClass());
            Class.forName("net.xolt.freecam.config.controller.ConfigController").getMethod("save").invoke(controller);
        } catch (ReflectiveOperationException e) { System.err.println("[Unbox] Could not prepare Freecam defaults: "+e); }
    }
    public static void prepareActivation() { speed=1;wheel=new ScrollWheelHandler(); }
    public static double speedMultiplier() { return active()?speed:1; }
    /** Keep spectator-style wheel adjustment; movement still belongs entirely to Freecam. */
    public static boolean scroll(long window,double horizontal,double vertical) {
        Minecraft mc=Minecraft.getInstance();
        if (!active() || window!=mc.getWindow().handle() || mc.screen!=null || mc.getOverlay()!=null || !mc.mouseHandler.isMouseGrabbed()) return false;
        double sensitivity=mc.options.mouseWheelSensitivity().get();boolean discrete=mc.options.discreteMouseScroll().get();
        var steps=wheel.onMouseScroll((discrete?Math.signum(horizontal):horizontal)*sensitivity,(discrete?Math.signum(vertical):vertical)*sensitivity);
        speed=Math.clamp(speed+steps.y*.1,0,4);
        return true;
    }
    public static boolean active() {
        resolve();
        if (isEnabled == null) return false;
        try { return (boolean)isEnabled.invoke(null); }
        catch (ReflectiveOperationException e) { return false; }
    }
    public static void stop() {
        if (!active()) return;
        try { toggle.invoke(null); }
        catch (ReflectiveOperationException e) { System.err.println("[Unbox] Could not stop Freecam: " + e); }
    }
    public static String openSettings(Screen parent) {
        if (!available()) return reason();
        try {
            Class<?> type = Class.forName("net.xolt.freecam.config.gui.ConfigScreenProvider");
            Object provider = type.getMethod("provider").invoke(null);
            Screen screen = (Screen)type.getMethod("getConfigScreen", Screen.class).invoke(provider, parent);
            Minecraft.getInstance().setScreen(screen);
            return "";
        } catch (ReflectiveOperationException e) { return "Could not open Freecam settings: " + e.getClass().getSimpleName(); }
    }
    // Kept for disposable-world integration checks and cooperating Unbox features.
    static Entity cameraEntity() {
        resolve(); if (camera == null) return null;
        try { return (Entity)camera.invoke(null); }
        catch (ReflectiveOperationException e) { return null; }
    }
    public static Vec3 position() { Entity e = cameraEntity(); return e == null ? Vec3.ZERO : e.getEyePosition(); }
    public static float yaw() { Entity e = cameraEntity(); return e == null ? 0 : e.getYRot(); }
    public static float pitch() { Entity e = cameraEntity(); return e == null ? 0 : e.getXRot(); }
    public static void turn(double x, double y) { Entity e = cameraEntity(); if (e != null) e.turn(x, y); }
}
