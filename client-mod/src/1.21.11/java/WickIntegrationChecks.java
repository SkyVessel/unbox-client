package dev.unbox.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Exercise the native light field as well as the settings bridge in the disposable world. */
final class WickIntegrationChecks {
    static List<String> run(Minecraft mc)throws Exception {
        List<String> checks=new ArrayList<>();
        String saved=UnboxClient.CONFIG.getProperty("dynamiclight");
        ItemStack held=mc.player.getMainHandItem().copy();
        Class<?> client=Class.forName("dev.wick.client.WickClient"),tracker=Class.forName("dev.wick.client.LightTracker"),lights=Class.forName("dev.wick.core.Lights");
        Object config=client.getMethod("config").invoke(null);
        var enabled=config.getClass().getMethod("enabled");
        var refresh=tracker.getMethod("refresh",Minecraft.class);
        var sample=lights.getMethod("packedAt",int.class,int.class,int.class);
        try {
            mc.player.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.TORCH));
            UnboxClient.set("dynamiclight",true);DynamicLightsBridge.sync();refresh.invoke(null,mc);
            if(!(boolean)enabled.invoke(config))throw new IllegalStateException("Wick did not enable");
            checks.add("Unbox Dynamic Lights enables the native Wick configuration");
            int light=(int)sample.invoke(null,mc.player.getBlockX(),mc.player.getBlockY()+1,mc.player.getBlockZ());
            if(light<=0)throw new IllegalStateException("Held torch did not populate Wick's light field");
            checks.add("A held torch populates the native dynamic light field next to the player");
            UnboxClient.set("dynamiclight",false);DynamicLightsBridge.sync();refresh.invoke(null,mc);
            if((boolean)enabled.invoke(config))throw new IllegalStateException("Wick did not disable");
            checks.add("Unbox Dynamic Lights disables the native Wick configuration");
            if((int)sample.invoke(null,mc.player.getBlockX(),mc.player.getBlockY()+1,mc.player.getBlockZ())!=0)throw new IllegalStateException("Disabled Wick retained dynamic light");
            checks.add("Disabling Dynamic Lights clears the native light field");
            return checks;
        } finally {
            mc.player.setItemInHand(InteractionHand.MAIN_HAND,held);
            if(saved==null)UnboxClient.CONFIG.remove("dynamiclight");else UnboxClient.CONFIG.setProperty("dynamiclight",saved);
            DynamicLightsBridge.sync();refresh.invoke(null,mc);
        }
    }
}
