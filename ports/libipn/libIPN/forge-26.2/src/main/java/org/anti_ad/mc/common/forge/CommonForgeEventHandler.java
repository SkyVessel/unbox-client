/*
 * Inventory Profiles Next
 *
 *   Copyright (c) 2019-2020 jsnimda <7615255+jsnimda@users.noreply.github.com>
 *   Copyright (c) 2021-2022 Plamen K. Kosseff <p.kosseff@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.anti_ad.mc.common.forge;

//import net.minecraft.client.gui.screens.Screen //net.minecraft.client.gui.screen.Screen;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import org.anti_ad.mc.common.gui.NativeInputContext;
import org.anti_ad.mc.common.gui.NativeInputContextBase;
import org.anti_ad.mc.common.input.GlobalInputHandler;
import org.anti_ad.mc.common.input.GlobalScreenEventListener;
import org.anti_ad.mc.common.input.KeyCodes;
import org.anti_ad.mc.common.vanilla.Vanilla;
import org.lwjgl.glfw.GLFW;

public class CommonForgeEventHandler {

    // Keyboard <-> KeyboardListener , onKey <-> onKeyEvent // ref: malilib forge 1.14.4 ForgeInputEventHandler
    @SubscribeEvent
    public void onKeyLast(InputEvent.Key event) {
        if (Vanilla.INSTANCE.screen() == null) { // non null is handled below
            var context = new NativeInputContext<>(event.getInfo(), false);
            GlobalInputHandler.INSTANCE.onKey(context, event.getKey(), event.getScanCode(), event.getAction(), event.getModifiers(), true, 0l);
        }
    }

    @SubscribeEvent
    public boolean onKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        final var context = new NativeInputContext<>(event.getInfo(), false);
        var b1 = onScreenKey(context, event.getKeyCode(), event.getScanCode(), GLFW.GLFW_PRESS, event.getModifiers(), event);
        var b2 = GlobalScreenEventListener.INSTANCE.onKeyPressed(context, event.getKeyCode(), event.getScanCode(), event.getModifiers(), true);
        return b1 || b2;
    }

    @SubscribeEvent
    public boolean onKeyRelease(ScreenEvent.KeyReleased.Pre event) {
        final var context = new NativeInputContext<>(event.getInfo(), false);
        var b1 = onScreenKey(context, event.getKeyCode(), event.getScanCode(), GLFW.GLFW_RELEASE, event.getModifiers(), event);
        var b2 = GlobalScreenEventListener.INSTANCE.onKeyReleased(context, event.getKeyCode(), event.getScanCode(), event.getModifiers(), true);
        return b1 || b2;
    }

    @SubscribeEvent
    public boolean onKeyPressedPost(ScreenEvent.KeyPressed.Post event) {
        final var context = new NativeInputContext<>(event.getInfo(), false);
        return GlobalScreenEventListener.INSTANCE.onKeyPressed(context, event.getKeyCode(), event.getScanCode(), event.getModifiers(), false);
    }

    @SubscribeEvent
    public boolean onKeyReleasePost(ScreenEvent.KeyReleased.Post event) {
        final var context = new NativeInputContext<>(event.getInfo(), false);
        return GlobalScreenEventListener.INSTANCE.onKeyReleased(context, event.getKeyCode(), event.getScanCode(), event.getModifiers(), false);
    }

    // Keyboard.onKey()
    // fix vanilla keybind swallow my listener
    // by line 308 aboolean[0] early returned
    // (e.g. pressing z + 1 while hovering slots)
    private boolean onScreenKey(NativeInputContextBase<?> context, int key, int scanCode, int action, int modifiers, ScreenEvent event) {
        // tmp solution fixing crafting recipe crash when opening other screen
        // (as post will also be swallowed if vanilla screen handle it)
        // fixme better approach
        Screen lastScreen = Vanilla.INSTANCE.screen();
        long handle = Vanilla.INSTANCE.window().handle();
        boolean result = GlobalInputHandler.INSTANCE.onKey(context, key, scanCode, action, modifiers, true, handle);
        return result || lastScreen != Vanilla.INSTANCE.screen(); // detect gui change, cancel vanilla
    }

    // ============
    // mouse
    // ============

    @SubscribeEvent
    public void onMouseButtonLast(InputEvent.MouseButton.Post event) {
        if (Vanilla.INSTANCE.screen() == null) { // non null is handled below
            final var context = new NativeInputContext(event.getInfo(), false);
            GlobalInputHandler.INSTANCE.onMouseButton(context, event.getButton(), event.getAction(), event.getModifiers());
        }
    } // fixme occasionally throw npe on Vanilla.mc() (idk why, build/class loading related?)

    @SubscribeEvent
    public boolean onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        final var context = new NativeInputContext(event.getInfo(), false);
        var b1 = onScreenMouseButton(context, event.getButton(), GLFW.GLFW_PRESS, lastMods, event);
        var b2 = GlobalScreenEventListener.INSTANCE.onMouseClicked(context, event.getMouseX(), event.getMouseY(), event.getButton(), true);
        return b1 || b2;
    }

    @SubscribeEvent
    public boolean onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        final var context = NativeInputContext.getEMPTY();
        var b1 = onScreenMouseButton(context, event.getButton(), GLFW.GLFW_RELEASE, lastMods, event);
        var b2 = GlobalScreenEventListener.INSTANCE.onMouseReleased(context, event.getMouseX(), event.getMouseY(), event.getButton(), true);
        return b1 || b2;
    }

    @SubscribeEvent
    public void onMouseClickedPost(ScreenEvent.MouseButtonPressed.Post event) {
        final var context = NativeInputContext.getEMPTY();
        GlobalScreenEventListener.INSTANCE.onMouseClicked(context, event.getMouseX(), event.getMouseY(), event.getButton(), false);
    }

    @SubscribeEvent
    public void onMouseReleasedPost(ScreenEvent.MouseButtonReleased.Post event) {
        final var context = NativeInputContext.getEMPTY();
        GlobalScreenEventListener.INSTANCE.onMouseReleased(context, event.getMouseX(), event.getMouseY(), event.getButton(), false);
    }

    private boolean onScreenMouseButton(NativeInputContextBase<?> context, int button, int action, int mods, ScreenEvent event) {
        Screen lastScreen = Vanilla.INSTANCE.screen();
        boolean result = GlobalInputHandler.INSTANCE.onMouseButton(context, button, action, mods);
        return result || lastScreen != Vanilla.INSTANCE.screen(); // detect gui change, cancel vanilla
    }

    private int lastMods = 0;

    @SubscribeEvent
    public void onRawMouse(InputEvent.MouseButton.Pre event) {
        lastMods = event.getModifiers();
    }

    @SubscribeEvent
    public boolean onMouseScroll(InputEvent.MouseScrollingEvent event) {
        double deltaX = event.getDeltaX();
        double deltaY = event.getDeltaY();
        boolean b1 = false, b2 = false;

        if (deltaX != 0) {
            b1 = processHorizontalScrollEvent(deltaX);
        }
        if (deltaY != 0) {
            b2 = processVerticalScrollEvent(deltaY);
        }
        return b1 || b2;
    }

    @SubscribeEvent
    public boolean onGuiMouseScroll(ScreenEvent.MouseScrolled.Pre event) {
        double deltaX = event.getDeltaX();
        double deltaY = event.getDeltaY();
        boolean b1 = false, b2 = false;

        if (deltaX != 0) {
            Screen lastScreen = Vanilla.INSTANCE.screen();
            boolean result = processHorizontalScrollEvent(deltaX);
            result = processHorizontalScrollGUIEvent(deltaX, event.getMouseX(), event.getMouseY()) || result;
            b1 = (result || lastScreen != Vanilla.INSTANCE.screen());
        }
        if (deltaY != 0) {
            Screen lastScreen = Vanilla.INSTANCE.screen();
            boolean result = processVerticalScrollEvent(deltaY);
            result = processVerticalScrollGUIEvent(deltaY, event.getMouseX(), event.getMouseY()) || result;
            b2 = (result || lastScreen != Vanilla.INSTANCE.screen());
        }
        return b1 || b2;
    }

    private static boolean processVerticalScrollGUIEvent(double delta, double mouseX, double mouseY) {
        int button;
        if (delta > 0) {
            button = KeyCodes.MOUSE_SCROLL_UP;
        } else {
            button = KeyCodes.MOUSE_SCROLL_DOWN;
        }
        boolean result = GlobalScreenEventListener.INSTANCE.onMouseClicked(NativeInputContext.getEMPTY(), mouseX, mouseY, button, true);
        result = GlobalScreenEventListener.INSTANCE.onMouseReleased(NativeInputContext.getEMPTY(), mouseX, mouseY, button, true) || result;
        return result;
    }

    private static boolean processHorizontalScrollGUIEvent(double delta, double mouseX, double mouseY) {
        int button;
        if (delta > 0) {
            button = KeyCodes.MOUSE_SCROLL_LEFT;
        } else {
            button = KeyCodes.MOUSE_SCROLL_RIGHT;
        }
        boolean result = GlobalScreenEventListener.INSTANCE.onMouseClicked(NativeInputContext.getEMPTY(), mouseX, mouseY, button, true);
        result = GlobalScreenEventListener.INSTANCE.onMouseReleased(NativeInputContext.getEMPTY(), mouseX, mouseY, button, true) || result;
        return result;
    }

    private static boolean processVerticalScrollEvent(double delta) {
        int button;
        if (delta > 0) {
            button = KeyCodes.MOUSE_SCROLL_UP;
        } else {
            button = KeyCodes.MOUSE_SCROLL_DOWN;
        }
        boolean result = GlobalInputHandler.INSTANCE.onMouseButton(NativeInputContext.getEMPTY(), button, GLFW.GLFW_PRESS, 0);
        result = GlobalInputHandler.INSTANCE.onMouseButton(NativeInputContext.getEMPTY(), button, GLFW.GLFW_RELEASE, 0) | result;
        return result;
    }

    private static boolean processHorizontalScrollEvent(double delta) {
        int button;
        if (delta > 0) {
            button = KeyCodes.MOUSE_SCROLL_LEFT;
        } else {
            button = KeyCodes.MOUSE_SCROLL_RIGHT;
        }
        boolean result = GlobalInputHandler.INSTANCE.onMouseButton(NativeInputContext.getEMPTY(), button, GLFW.GLFW_PRESS, 0);
        result = GlobalInputHandler.INSTANCE.onMouseButton(NativeInputContext.getEMPTY(), button, GLFW.GLFW_RELEASE, 0) | result;
        return result;
    }


//  @SubscribeEvent
//  public void onWorldLoad(WorldEvent.Load event) {
//    // clear keybind (pressing keys)
//    GlobalInputHandler.INSTANCE.getPressedKeys().clear();
//  }

    // todo mouse move event
  /*
  @Inject(method = "onCursorPos", at = @At("RETURN"))
  private void onCursorPos(long handle, double xpos, double ypos, CallbackInfo ci) {
    VanillaUtil.INSTANCE.updateMouse();
  }
   */

}
