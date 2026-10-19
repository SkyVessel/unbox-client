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

package org.anti_ad.mc.common.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.anti_ad.mc.common.gui.NativeInputContext;
import org.anti_ad.mc.common.gui.debug.DebugInfos;
import org.anti_ad.mc.common.input.GlobalInputHandler;
import org.anti_ad.mc.common.input.GlobalScreenEventListener;
import org.anti_ad.mc.common.vanilla.Vanilla;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static org.anti_ad.mc.common.input.KeyCodes.*;

@Mixin(MouseHandler.class)
public class MixinMouse {

    @Inject(method = "onButton", at = @At(value = "TAIL"))
    private void onMouseButtonLast(long handle, MouseButtonInfo input, int action, CallbackInfo ci) {
        final var window = Vanilla.INSTANCE.window();
        if (handle == window.handle()) {
            final var click = new MouseButtonEvent(Vanilla.INSTANCE.mouse().getScaledXPos(window), Vanilla.INSTANCE.mouse().getScaledYPos(window), input);
            final var context = new NativeInputContext<>(click, false);
            if (Vanilla.INSTANCE.screen() != null) {
                GlobalScreenEventListener.INSTANCE.onMouse(context, input.button(), action, input.modifiers(), false);
            } else {
                GlobalInputHandler.INSTANCE.onMouseButton(context, input.button(), action, input.modifiers());
            }
        }
    }

    @Inject(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z"), cancellable = true)
    private void onScreenMouseButtonPress(long handle, MouseButtonInfo input, int action, CallbackInfo ci) {
        final Screen lastScreen = Vanilla.INSTANCE.screen();
        final var window = Vanilla.INSTANCE.window();
        final var click = new MouseButtonEvent(Vanilla.INSTANCE.mouse().getScaledXPos(window), Vanilla.INSTANCE.mouse().getScaledYPos(window), input);
        final var context = new NativeInputContext<>(click, false);
        boolean result = GlobalInputHandler.INSTANCE.onMouseButton(context, input.button(), action, input.modifiers()) ||
                         (lastScreen != null && GlobalScreenEventListener.INSTANCE.onMouse(context, input.button(), action, input.modifiers(), true));
        if (result || lastScreen != Vanilla.INSTANCE.screen()) { // detect gui change, cancel vanilla
            ci.cancel();
        }
    }


    @Inject(method = "onButton", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z"), cancellable = true)
    private void onScreenMouseButtonRelease(long handle, MouseButtonInfo input, int action, CallbackInfo ci) {
        final var window = Vanilla.INSTANCE.window();
        final var click = new MouseButtonEvent(Vanilla.INSTANCE.mouse().getScaledXPos(window), Vanilla.INSTANCE.mouse().getScaledYPos(window), input);
        final var context = new NativeInputContext<>(click, false);
        Screen lastScreen = Vanilla.INSTANCE.screen();
        boolean result = GlobalInputHandler.INSTANCE.onMouseButton(context, input.button(), action, input.modifiers()) ||
                         (lastScreen != null && GlobalScreenEventListener.INSTANCE.onMouse(context, input.button(), action, input.modifiers(), true));
        if (result || lastScreen != Vanilla.INSTANCE.screen()) { // detect gui change, cancel vanilla
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "onScroll(JDD)V", cancellable = true)
    void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().handle()) {
            var context = new NativeInputContext<MouseButtonEvent>(null, false);
            DebugInfos.INSTANCE.onScrollButton(context, horizontal, vertical);
            boolean result = false;

            if (horizontal != 0) {
                int button = horizontal > 0 ? MOUSE_SCROLL_LEFT : MOUSE_SCROLL_RIGHT;

                result = GlobalInputHandler.INSTANCE.onMouseButton(context, button, InputConstants.PRESS, 0);
                result = GlobalScreenEventListener.INSTANCE.onMouse(context, button, InputConstants.PRESS, 0, true)
                        || result;
            }
            if (vertical != 0) {
                int button = vertical > 0 ? MOUSE_SCROLL_UP : MOUSE_SCROLL_DOWN;
                result = GlobalInputHandler.INSTANCE.onMouseButton(context, button, InputConstants.PRESS, 0);
                result = GlobalScreenEventListener.INSTANCE.onMouse(context, button, InputConstants.PRESS, 0, true)
                        || result;
            }

            if (horizontal != 0) {
                int button = horizontal > 0 ? MOUSE_SCROLL_LEFT: MOUSE_SCROLL_RIGHT;
                result = GlobalInputHandler.INSTANCE.onMouseButton(context, button, InputConstants.RELEASE, 0);
                result = GlobalScreenEventListener.INSTANCE.onMouse(context, button, InputConstants.RELEASE, 0, true)
                        || result;
            }
            if (vertical != 0) {
                int button = vertical > 0 ? MOUSE_SCROLL_UP : MOUSE_SCROLL_DOWN;
                result = GlobalInputHandler.INSTANCE.onMouseButton(context, button, InputConstants.RELEASE, 0);
                result = GlobalScreenEventListener.INSTANCE.onMouse(context, button, InputConstants.RELEASE, 0, true)
                        || result;
            }

            if (result) {
                ci.cancel();
            }
        } else {
            ci.cancel();
        }
    }
}
