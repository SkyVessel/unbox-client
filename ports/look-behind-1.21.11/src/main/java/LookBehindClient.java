package net.razrcraft.lookbehind.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class LookBehindClient implements ClientModInitializer {
    private static KeyMapping lookBehindKey;
    private static boolean lookbehind;

    public static boolean isLookingBehind() {
        return lookbehind;
    }

    @Override
    public void onInitializeClient() {
        // Register the keybind
        lookBehindKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.lookbehind.look_behind",   // Translation key
                InputConstants.Type.KEYSYM,                      // Key type
                GLFW.GLFW_KEY_R,                            // Default key (R)
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("lookbehind","keys"))
        ));

        // Handle key press logic
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            lookbehind = client.player != null && client.screen == null && client.isWindowActive() && lookBehindKey.isDown();
        });
    }
}
