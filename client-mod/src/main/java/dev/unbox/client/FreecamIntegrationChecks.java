package dev.unbox.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Exercise the installed upstream camera, rather than the removed Unbox flight implementation. */
final class FreecamIntegrationChecks {
    private static void check(boolean ok, String message, List<String> checks) {
        if (!ok) throw new IllegalStateException(message);
        checks.add(message);
    }
    private static void release(Minecraft mc) {
        for (KeyMapping key : List.of(mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft,
                mc.options.keyRight, mc.options.keyJump, mc.options.keyShift, mc.options.keySprint)) key.setDown(false);
    }
    static void verify(Minecraft mc, List<String> checks) {
        Entity camera = FreecamController.cameraEntity();
        check(camera != null && camera.getClass().getName().equals("net.xolt.freecam.util.FreeCamera"),
            "The public Freecam mod owns the detached camera", checks);
        Vec3 origin = camera.position(), body = mc.player.position();
        float yaw = camera.getYRot(), pitch = camera.getXRot();
        float bodyYaw=mc.player.getYRot(),bodyPitch=mc.player.getXRot();
        try {
            float[] headings = {0, 90, 180, -90};
            Vec3[] right = {new Vec3(-1,0,0),new Vec3(0,0,-1),new Vec3(1,0,0),new Vec3(0,0,1)};
            for (int i=0;i<headings.length;i++) {
                release(mc);camera.setDeltaMovement(Vec3.ZERO);camera.setPos(origin);
                camera.setYRot(headings[i]);camera.setXRot(0);
                mc.options.keyRight.setDown(true);camera.tick();
                check(camera.position().subtract(origin).dot(right[i])>0,"Upstream D moves right at yaw "+headings[i],checks);
                release(mc);camera.setDeltaMovement(Vec3.ZERO);camera.setPos(origin);
                mc.options.keyLeft.setDown(true);camera.tick();
                check(camera.position().subtract(origin).dot(right[i])<0,"Upstream A moves left at yaw "+headings[i],checks);
            }
            check(mc.player.position().distanceTo(body)<1e-6,"Detached-camera input never moves the player body",checks);
            float before=camera.getYRot();mc.player.turn(40,20);
            check(camera.getYRot()!=before&&mc.player.getYRot()==bodyYaw&&mc.player.getXRot()==bodyPitch,"Mouse rotation moves only the upstream camera, not the body",checks);
            try {
                var captured=net.minecraft.client.MouseHandler.class.getDeclaredField("mouseGrabbed");captured.setAccessible(true);
                boolean wasCaptured=captured.getBoolean(mc.mouseHandler);
                var callback=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onScroll",long.class,double.class,double.class);callback.setAccessible(true);
                int slot=mc.player.getInventory().getSelectedSlot();
                try {
                    captured.setBoolean(mc.mouseHandler,false);check(!FreecamController.scroll(mc.getWindow().handle(),0,1),"Uncaptured wheel is ignored",checks);
                    captured.setBoolean(mc.mouseHandler,true);
                    callback.invoke(mc.mouseHandler,mc.getWindow().handle(),0d,1d);
                    check(FreecamController.speedMultiplier()>1,"Wheel adjusts native flight speed",checks);
                    check(mc.player.getInventory().getSelectedSlot()==slot,"Freecam wheel does not switch the player's item",checks);
                } finally { captured.setBoolean(mc.mouseHandler,wasCaptured); }
            } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
        } finally {
            release(mc);camera.setDeltaMovement(Vec3.ZERO);camera.setPos(origin);camera.setYRot(yaw);camera.setXRot(pitch);
        }
    }
}
