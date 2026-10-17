package dev.unbox.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ScrollWheelHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import java.util.UUID;

/** Client-only spectator camera. The server-owned player and game mode never change. */
public final class FreecamController {
    private static KeyMapping key;
    private static SpectatorCamera camera;
    private static Entity previousCamera;
    private static CameraType previousPerspective;
    private static ClientLevel level;
    private static ScrollWheelHandler scroll;

    private FreecamController() {}

    public static void init() {
        key = Platform.key(new KeyMapping("key.unbox.freecam", GLFW.GLFW_KEY_F6, KeyMapping.Category.MISC));
    }

    public static KeyMapping key() { return key; }

    public static void tick(Minecraft mc) {
        if (key == null) return;
        while (key.consumeClick()) {
            if (active()) stop();
            else if (mc.screen == null && mc.player != null && UnboxClient.enabled("freecam")) start(mc);
        }
        if (!active()) return;
        if (!UnboxClient.enabled("freecam") || mc.screen != null || mc.player == null || mc.level != level || !mc.player.isAlive() || mc.getCameraEntity() != camera) { stop(); return; }
        if (Freelook.active()) Freelook.suspend();

        camera.setOldPosAndRot();
        camera.yHeadRotO = camera.yHeadRot;
        camera.yBodyRotO = camera.yBodyRot;
        // Reuse the actual LocalPlayer input/sprint/flight/travel pipeline. Calling tick()
        // would send player movement packets; this detached entity only runs aiStep().
        camera.aiStep();
    }

    private static void start(Minecraft mc) {
        Freelook.suspend();
        previousCamera = mc.getCameraEntity();
        previousPerspective = mc.options.getCameraType();
        level = mc.level;
        camera = new SpectatorCamera(mc, level);
        scroll = new ScrollWheelHandler();
        camera.noPhysics = true;
        camera.setNoGravity(true);
        camera.setInvisible(true);
        Vec3 feet = mc.player.getEyePosition().subtract(0, camera.getEyeHeight(), 0);
        camera.setPos(feet);
        camera.setYRot(mc.player.getYRot());
        camera.setXRot(mc.player.getXRot());
        syncPlayerRotation();
        camera.setOldPosAndRot(feet, camera.getYRot(), camera.getXRot());
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.setCameraEntity(camera);
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        if (camera != null && mc.getCameraEntity() == camera) {
            Entity restore = previousCamera != null && previousCamera.level() == mc.level ? previousCamera : mc.player;
            mc.setCameraEntity(restore);
        }
        if (previousPerspective != null) mc.options.setCameraType(previousPerspective);
        camera = null;
        previousCamera = null;
        previousPerspective = null;
        level = null;
        scroll = null;
    }

    /** Same wheel accumulation, sensitivity, increment and limits as vanilla spectator flight. */
    public static boolean scroll(long window, double horizontal, double vertical) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || window != mc.getWindow().handle() || mc.screen != null
                || mc.getOverlay() != null || !mc.mouseHandler.isMouseGrabbed()) return false;
        mc.getFramerateLimitTracker().onInputReceived();
        boolean discrete = mc.options.discreteMouseScroll().get();
        double sensitivity = mc.options.mouseWheelSensitivity().get();
        var steps = scroll.onMouseScroll((discrete ? Math.signum(horizontal) : horizontal)*sensitivity,
            (discrete ? Math.signum(vertical) : vertical)*sensitivity);
        var abilities = camera.getAbilities();
        abilities.setFlyingSpeed(Math.clamp(abilities.getFlyingSpeed()+steps.y*.005f, 0f, .2f));
        return true;
    }

    public static void turn(double horizontal, double vertical) { if (camera != null) { camera.turn(horizontal, vertical); syncPlayerRotation(); } }
    public static boolean active() { return camera != null; }
    public static Vec3 position() { return camera == null ? Vec3.ZERO : camera.getEyePosition(); }
    public static float yaw() { return camera == null ? 0 : camera.getViewYRot(1); }
    public static float pitch() { return camera == null ? 0 : camera.getXRot(); }
    static Entity cameraEntity() { return camera; }

    private static void syncPlayerRotation() {
        camera.setYHeadRot(camera.getYRot());
        camera.yHeadRotO = camera.yRotO;
        camera.setYBodyRot(camera.getYRot());
        camera.yBodyRotO = camera.yRotO;
    }

    private static final class SpectatorCamera extends LocalPlayer {
        private SpectatorCamera(Minecraft mc, ClientLevel level) {
            // LocalPlayer requires the connection for its profile. Only aiStep is used;
            // the network-sending tick and ability update paths are overridden below.
            super(mc, level, mc.player.connection, mc.player.getStats(), mc.player.getRecipeBook(), Input.EMPTY, false, mc.player.chatAbilities());
            setUUID(UUID.randomUUID());
            input = new KeyboardInput(mc.options);
            getAbilities().flying = true;
            // Always flying; skip LocalPlayer's creative double-jump flight toggle.
            getAbilities().mayfly = false;
            getAbilities().invulnerable = true;
        }
        @Override public boolean isSpectator() { return true; }
        @Override public boolean isCreative() { return false; }
        @Override public void tick() { aiStep(); }
        @Override public void onUpdateAbilities() {}
    }
}
