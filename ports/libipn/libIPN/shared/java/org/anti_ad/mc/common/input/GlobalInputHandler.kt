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

package org.anti_ad.mc.common.input


import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.input.KeyEvent;
import org.anti_ad.mc.common.IInputHandler
import org.anti_ad.mc.common.gui.NativeInputContextBase
import org.anti_ad.mc.common.gui.debug.DebugInfos
import org.anti_ad.mc.common.gui.screen.ConfigOptionHotkeyDialog
import org.anti_ad.mc.common.gui.screen.ConfigScreenBase
import org.anti_ad.mc.common.vanilla.Vanilla
import org.anti_ad.mc.common.vanilla.VanillaUtil
import org.anti_ad.mc.libipn.Log

object GlobalInputHandler {

    val pressedKeys = mutableSetOf<Int>()
    var previousPressedKeys = mutableSetOf<Int>()
        private set
    var lastKey = -1
        private set
    var lastAction = Int.MIN_VALUE
        private set

    fun isWaitingForRelease(key: Int): Boolean {
        return pressedKeys.contains(key)
    }

    fun isActivated(keyCodes: List<Int>,
                    settings: KeybindSettings): Boolean {
        //Log.trace("will check if activated for settings: $settings and pressed keys: $pressedKeys and last pressed keys: $previousPressedKeys")
        if (keyCodes.isEmpty()) return false

        if (!settings.activateOn.isValid(lastAction)) return false
        if (!VanillaUtil.isValidScreen(settings.context)) return false
        //if (!settings.context.isValid(Vanilla.screen())) return false
        // checked: context, activateOn
        // ref: malilib KeybindMulti.updateIsPressed()
        val validateKeys = if (lastAction == InputConstants.PRESS) pressedKeys else previousPressedKeys
        return settings.validates(validateKeys,
                                  keyCodes)
    }

    fun isPressing(keyCodes: List<Int>,
                   settings: KeybindSettings): Boolean {
        if (keyCodes.isEmpty()) return false
        if (!VanillaUtil.isValidScreen(settings.context)) return false
        //if (!settings.context.isValid(Vanilla.screen())) return false
        return settings.validates(pressedKeys,
                                  keyCodes,
                                  justPressed = false)
    }

    private fun onKey(context: NativeInputContextBase<*>,
                      key: Int,
                      action: Int): Boolean { // action: only InputConstants.PRESS or InputConstants.RELEASE

        val isPress = action == InputConstants.PRESS
        if (isPress && pressedKeys.contains(key)) {
            return false
        }

        if (isPress) {
            pressedKeys.add(key)
        } else {
            pressedKeys.remove(key)
        }
        lastKey = key
        lastAction = action
        return onInput(context)/*.also {
            Log.trace("onKey($key, $action) -> after processing pressed so far: $pressedKeys, last pressed: $previousPressedKeys")
        }*/
    }

    private fun onInput(context: NativeInputContextBase<*>): Boolean {
        if (currentAssigningKeybind != null) {
            if (lastAction == InputConstants.PRESS) {
                handleAssignKeybind()
            } else {
                handleAssignKeybind()
            }
            return true
        }

        val screen = Vanilla.screen()
        if (screen != null && (screen is ConfigScreenBase || screen is ConfigOptionHotkeyDialog)) {
            return false
        }

        if (registeredCancellable.any { it.onInput(context,
                                                   lastKey,
                                                   lastAction) }) {
            return true
        }
        registered.forEach {
            it.onInput(context,
                       lastKey,
                       lastAction)
        }
        return false
    }

    // ============
    // Assign keybind
    // ============
    var currentAssigningKeybind: IKeybind? = null
        set(value) {
            pressedFirstKey = false
            ignoreLeftClick = true // left down -> ignore, left up -> set false
            field = value
        }
    private var pressedFirstKey = false
    private var ignoreLeftClick = false // fix forge version while compatible with fabric version

    private fun handleAssignKeybind() {
        val pressedKeys: List<Int> = currentAssigningKeybind?.run {
            settings.modifierKey.handleKeys(pressedKeys.toList())
        } ?: pressedKeys.toList()

        if (lastAction == InputConstants.PRESS) {
            if (lastKey == KeyCodes.MOUSE_BUTTON_1 && ignoreLeftClick) { // SDL_BUTTON_LEFT - 101
                return
            }
            pressedFirstKey = true
            if (lastKey == KeyCodes.KEY_ESCAPE) { // clear keybind
                currentAssigningKeybind?.keyCodes = listOf()
                currentAssigningKeybind = null
            } else {
                currentAssigningKeybind?.keyCodes = pressedKeys
            }
        } else { // lastAction == InputConstants.RELEASE
            if (lastKey == KeyCodes.MOUSE_BUTTON_1) {
                ignoreLeftClick = false
            }
            if (pressedKeys.isEmpty() && pressedFirstKey) {
                currentAssigningKeybind = null // all key released, assignment end
            }
        }
    }

    fun isKeyDown(keyCode: Int): Boolean = KeyCodes.isKeyDown(keyCode)
    // ============
    // Vanilla key hook handler
    // ============
    fun onKey(context: NativeInputContextBase<*>,
              key: Int,
              scanCode: Int,
              action: @KeyEvent.Action Int,
              modifiers: Int,
              checkPressing: Boolean,
              handle: Long): Boolean {

        DebugInfos.onKey(context,
                         key,
                         scanCode,
                         action,
                         modifiers)
        previousPressedKeys.clear()
        previousPressedKeys.addAll(pressedKeys)
        if (handle != 0L && checkPressing && pressedKeys.isNotEmpty()) {
            val pressed = pressedKeys.toSet()
            pressedKeys.clear()
            pressed.forEach {
                if (isKeyDown(it)) {
                    pressedKeys.add(it)
                } else if (action == InputConstants.PRESS) {
                    onKey(context, it, InputConstants.RELEASE)
                }
            }
        }
        return when (action) {
            InputConstants.PRESS,
            InputConstants.RELEASE -> {
                onKey(context, key, action)
            }
            else -> false
        }
    }

    fun onMouseButton(context: NativeInputContextBase<*>,
                      button: Int,
                      action: Int,
                      mods: Int): Boolean {
        DebugInfos.onMouseButton(context,
                                 button,
                                 action,
                                 mods)
        previousPressedKeys.clear()
        previousPressedKeys.addAll(pressedKeys)
/*
        Log.trace {
            "Action: ${if (action == InputConstants.PRESS) "PRESS" else if (action == InputConstants.RELEASE) "RELEASE" else "UNKNOWN"}" +
            " Button: $button" +
            " Modifiers: $mods" +
            " Pressed: ${pressedKeys.joinToString(separator = ", ", prefix = "\n", postfix = "\n") { "$it: ${KeyCodes.getName(it)}" }}" +
            " Previous: ${previousPressedKeys.joinToString(separator = ", ", prefix = "\n", postfix = "\n") { "$it: ${KeyCodes.getName(it)}" }}"
        }
*/
        return when (action) {
            InputConstants.PRESS,
            InputConstants.RELEASE -> {
                val normalized = KeyCodes.mouseButtonToKeyCode(button)
                if (normalized < 0) {
                    Log.trace("Mouse button $normalized, ${if (action == InputConstants.PRESS) "pressed" else "released" }")
                }
                onKey(context, normalized, action)
            }
            else -> false
        }
    }

    fun shiftAnd(vararg keyCodes: Int) = (pressedKeys.contains(KeyCodes.KEY_LEFT_SHIFT) || pressedKeys.contains(KeyCodes.KEY_RIGHT_SHIFT))
            && pressedKeys.containsAll(keyCodes.distinct())

/*
    fun shiftAnd(vararg keyCodes: Int): Boolean {

        if (pressedKeys.contains(KeyCodes.KEY_LEFT_SHIFT)
            || pressedKeys.contains(KeyCodes.KEY_RIGHT_SHIFT)) {
            Log.trace {
                "keys found: ${pressedKeys.containsAll(keyCodes.distinct())}" +
                        pressedKeys.joinToString(separator = ", ", prefix = "\n", postfix = "\n") { "$it: ${KeyCodes.getName(it)}" }
            }
        }
        return (pressedKeys.contains(KeyCodes.KEY_LEFT_SHIFT)
                || pressedKeys.contains(KeyCodes.KEY_RIGHT_SHIFT))
                && pressedKeys.containsAll(keyCodes.distinct())
    }
*/

    fun altAnd(vararg keyCodes: Int)  = (pressedKeys.contains(KeyCodes.KEY_LEFT_ALT) || pressedKeys.contains(KeyCodes.KEY_RIGHT_ALT))
            && pressedKeys.containsAll(keyCodes.distinct())

    fun ctrlAnd(vararg keyCodes: Int)  = (pressedKeys.contains(KeyCodes.KEY_LEFT_CONTROL) || pressedKeys.contains(KeyCodes.KEY_RIGHT_CONTROL))
            && pressedKeys.containsAll(keyCodes.distinct())

    fun superAnd(vararg keyCodes: Int)  = (pressedKeys.contains(KeyCodes.KEY_LEFT_SUPER) || pressedKeys.contains(KeyCodes.KEY_RIGHT_SUPER))
            && pressedKeys.containsAll(keyCodes.distinct())

    fun arePressed( vararg keyCodes: Int) = pressedKeys.containsAll(keyCodes.distinct())


    // ============
    // api
    // ============
    private val registered: MutableSet<IInputHandler> = mutableSetOf()
    private val registeredCancellable: MutableSet<IInputHandler> = mutableSetOf() // screen only

    fun register(inputHandler: IInputHandler): Boolean =
        registered.add(inputHandler)

    fun unregister(inputHandler: IInputHandler): Boolean =
        registered.remove(inputHandler)

    fun registerCancellable(inputHandler: IInputHandler): Boolean =
        registeredCancellable.add(inputHandler)

    fun unregisterCancellable(inputHandler: IInputHandler): Boolean =
        registeredCancellable.remove(inputHandler)
}
