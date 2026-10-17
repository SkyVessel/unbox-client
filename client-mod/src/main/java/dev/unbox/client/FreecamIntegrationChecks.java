package dev.unbox.client;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Runs only in the opt-in visual test's disposable world, with the real mixed-in client. */
final class FreecamIntegrationChecks {
    private static void check(boolean ok, String message, List<String> checks) {
        if (!ok) throw new IllegalStateException(message);
        checks.add(message);
    }
    private static void release(Minecraft mc) {
        for (KeyMapping key : List.of(mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft,
                mc.options.keyRight, mc.options.keyJump, mc.options.keyShift, mc.options.keySprint)) key.setDown(false);
    }
    private static void reset(Minecraft mc, Entity camera, Vec3 position, float yaw) {
        release(mc); camera.setDeltaMovement(Vec3.ZERO); camera.setPos(position);
        FreecamController.turn((yaw-FreecamController.yaw())/.15, -FreecamController.pitch()/.15);
        // Clear vanilla's double-tap sprint window between independent movement fixtures.
        for(int i=0;i<=mc.options.sprintWindow().get();i++) FreecamController.tick(mc);
        camera.setOldPosAndRot();
    }
    static void verify(Minecraft mc, List<String> checks) throws Exception {
        Entity camera = FreecamController.cameraEntity();
        Vec3 origin = camera.position(); float yaw = camera.getYRot(), pitch = camera.getXRot();
        var sprintOption = UnboxClient.CONFIG.getProperty("sprint");
        UnboxClient.CONFIG.setProperty("sprint", "false");
        try {
            reset(mc,camera,origin,0);
            mc.options.keyUp.setDown(true); FreecamController.tick(mc);
            double first = camera.position().z-origin.z;
            check(Math.abs(first-.05f*.98f)<1e-6,"Native spectator first-tick acceleration (0.049 blocks), not a fixed position jump",checks);
            FreecamController.tick(mc);
            double second = camera.position().z-origin.z-first;
            check(Math.abs(second-(first*.91f+first))<1e-6,"Held forward accelerates with native horizontal drag",checks);
            mc.options.keyUp.setDown(false); Vec3 before = camera.position(); FreecamController.tick(mc);
            check(Math.abs(camera.position().z-before.z-second*.91f)<1e-6,"Release preserves and decays native flight inertia",checks);
            for(int i=0;i<100;i++) FreecamController.tick(mc);
            check(camera.getDeltaMovement().lengthSqr()==0,"Native dead zone brings released camera to rest",checks);

            float[] headings={0,90,180,-90};
            Vec3[] right={new Vec3(-1,0,0),new Vec3(0,0,-1),new Vec3(1,0,0),new Vec3(0,0,1)};
            for(int i=0;i<headings.length;i++) {
                reset(mc,camera,origin,headings[i]); mc.options.keyRight.setDown(true); FreecamController.tick(mc);
                Vec3 delta=camera.position().subtract(origin);
                check(delta.dot(right[i])>.048&&delta.cross(right[i]).length()<1e-5,"D strafes right at yaw "+headings[i],checks);
                reset(mc,camera,origin,headings[i]); mc.options.keyLeft.setDown(true); FreecamController.tick(mc);
                check(camera.position().subtract(origin).dot(right[i])<-.048,"A strafes left at yaw "+headings[i],checks);
            }
            reset(mc,camera,origin,0); mc.options.keyJump.setDown(true); FreecamController.tick(mc);
            check(Math.abs(camera.getY()-origin.y-.15f)<1e-6,"Space uses native independent vertical acceleration",checks);
            before=camera.position(); mc.options.keyJump.setDown(false); FreecamController.tick(mc);
            check(Math.abs(camera.getY()-before.y-.15f*.6)<1e-6,"Vertical release uses native 0.6 drag",checks);
            reset(mc,camera,origin,0); mc.options.keyUp.setDown(true); mc.options.keyJump.setDown(true); FreecamController.tick(mc);
            check(Math.abs(camera.getZ()-origin.z-first)<1e-6&&Math.abs(camera.getY()-origin.y-.15f)<1e-6,"Forward plus ascent does not normalize away either axis",checks);
            reset(mc,camera,origin,0); mc.options.keyUp.setDown(true); mc.options.keyRight.setDown(true); FreecamController.tick(mc);
            check(Math.abs(camera.position().subtract(origin).horizontalDistance()-.05f)<1e-6,"Diagonal input follows vanilla square-movement correction",checks);
            reset(mc,camera,origin,0); mc.options.keyUp.setDown(true); mc.options.keySprint.setDown(true); FreecamController.tick(mc);
            check(Math.abs(camera.getZ()-origin.z-first*2)<1e-6,"Native sprint doubles forward flying acceleration",checks);
            mc.options.keySprint.setDown(false); before=camera.position(); FreecamController.tick(mc);
            check(Math.abs(camera.getZ()-before.z-(first*2*.91f+first*2))<1e-6,"Sprint remains active while forward stays held, like spectator",checks);
            reset(mc,camera,origin,0);
            // Invoke the actual mouse callback: speed changes must not reach hotbar selection.
            var scroll=net.minecraft.client.MouseHandler.class.getDeclaredMethod("onScroll",long.class,double.class,double.class);
            scroll.setAccessible(true);
            // Supply the captured-mouse input state inside this synchronous fixture. Multiple
            // test JVMs (or the user switching apps) must not turn it into an OS-focus test.
            var captured=net.minecraft.client.MouseHandler.class.getDeclaredField("mouseGrabbed");
            captured.setAccessible(true); boolean wasCaptured=captured.getBoolean(mc.mouseHandler);
            var sensitivity=mc.options.mouseWheelSensitivity().get();
            var discrete=mc.options.discreteMouseScroll().get();
            var abilities=((net.minecraft.world.entity.player.Player)camera).getAbilities();
            int slot=mc.player.getInventory().getSelectedSlot();
            try {
                captured.setBoolean(mc.mouseHandler,false);
                check(!FreecamController.scroll(mc.getWindow().handle(),0,1),"Uncaptured mouse cannot change Freecam speed",checks);
                captured.setBoolean(mc.mouseHandler,true);
                mc.options.mouseWheelSensitivity().set(1d); mc.options.discreteMouseScroll().set(false);
                scroll.invoke(mc.mouseHandler,mc.getWindow().handle(),0d,.5d);
                check(Math.abs(abilities.getFlyingSpeed()-.05f)<1e-7,"Fractional wheel input accumulates without jumping speed",checks);
                scroll.invoke(mc.mouseHandler,mc.getWindow().handle(),0d,.5d);
                check(Math.abs(abilities.getFlyingSpeed()-.055f)<1e-7,"Wheel increases flight speed by vanilla 0.005 step",checks);
                scroll.invoke(mc.mouseHandler,mc.getWindow().handle(),0d,100d);
                check(abilities.getFlyingSpeed()==.2f,"Wheel speed clamps at vanilla upper limit",checks);
                scroll.invoke(mc.mouseHandler,mc.getWindow().handle(),0d,-100d);
                check(abilities.getFlyingSpeed()==0f,"Wheel speed clamps at vanilla lower limit",checks);
                check(mc.player.getInventory().getSelectedSlot()==slot,"Freecam wheel leaves the body's selected item unchanged",checks);
            } finally {
                captured.setBoolean(mc.mouseHandler,wasCaptured);
                abilities.setFlyingSpeed(.05f); mc.options.mouseWheelSensitivity().set(sensitivity); mc.options.discreteMouseScroll().set(discrete);
            }
        } finally {
            reset(mc,camera,origin,yaw); FreecamController.turn(0,pitch/.15);
            if(sprintOption==null)UnboxClient.CONFIG.remove("sprint");else UnboxClient.CONFIG.setProperty("sprint",sprintOption);
        }
    }
}
