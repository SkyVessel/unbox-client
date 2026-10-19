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

import org.lwjgl.sdl.SDLKeyboard
import org.lwjgl.sdl.SDLMouse
import org.lwjgl.sdl.SDLScancode

@Suppress("MemberVisibilityCanBePrivate")
object KeyCodes {

    //region const val KEY_s
    //@formatter:off
    const val KEY_UNKNOWN = SDLScancode.SDL_SCANCODE_UNKNOWN
    const val KEY_SPACE = SDLScancode.SDL_SCANCODE_SPACE
    const val KEY_APOSTROPHE = SDLScancode.SDL_SCANCODE_APOSTROPHE
    const val KEY_COMMA = SDLScancode.SDL_SCANCODE_COMMA
    const val KEY_MINUS = SDLScancode.SDL_SCANCODE_MINUS
    const val KEY_PERIOD = SDLScancode.SDL_SCANCODE_PERIOD
    const val KEY_SLASH = SDLScancode.SDL_SCANCODE_SLASH
    const val KEY_0 = SDLScancode.SDL_SCANCODE_0
    const val KEY_1 = SDLScancode.SDL_SCANCODE_1
    const val KEY_2 = SDLScancode.SDL_SCANCODE_2
    const val KEY_3 = SDLScancode.SDL_SCANCODE_3
    const val KEY_4 = SDLScancode.SDL_SCANCODE_4
    const val KEY_5 = SDLScancode.SDL_SCANCODE_5
    const val KEY_6 = SDLScancode.SDL_SCANCODE_6
    const val KEY_7 = SDLScancode.SDL_SCANCODE_7
    const val KEY_8 = SDLScancode.SDL_SCANCODE_8
    const val KEY_9 = SDLScancode.SDL_SCANCODE_9
    const val KEY_SEMICOLON = SDLScancode.SDL_SCANCODE_SEMICOLON
    const val KEY_EQUAL = SDLScancode.SDL_SCANCODE_EQUALS
    const val KEY_A = SDLScancode.SDL_SCANCODE_A
    const val KEY_B = SDLScancode.SDL_SCANCODE_B
    const val KEY_C = SDLScancode.SDL_SCANCODE_C
    const val KEY_D = SDLScancode.SDL_SCANCODE_D
    const val KEY_E = SDLScancode.SDL_SCANCODE_E
    const val KEY_F = SDLScancode.SDL_SCANCODE_F
    const val KEY_G = SDLScancode.SDL_SCANCODE_G
    const val KEY_H = SDLScancode.SDL_SCANCODE_H
    const val KEY_I = SDLScancode.SDL_SCANCODE_I
    const val KEY_J = SDLScancode.SDL_SCANCODE_J
    const val KEY_K = SDLScancode.SDL_SCANCODE_K
    const val KEY_L = SDLScancode.SDL_SCANCODE_L
    const val KEY_M = SDLScancode.SDL_SCANCODE_M
    const val KEY_N = SDLScancode.SDL_SCANCODE_N
    const val KEY_O = SDLScancode.SDL_SCANCODE_O
    const val KEY_P = SDLScancode.SDL_SCANCODE_P
    const val KEY_Q = SDLScancode.SDL_SCANCODE_Q
    const val KEY_R = SDLScancode.SDL_SCANCODE_R
    const val KEY_S = SDLScancode.SDL_SCANCODE_S
    const val KEY_T = SDLScancode.SDL_SCANCODE_T
    const val KEY_U = SDLScancode.SDL_SCANCODE_U
    const val KEY_V = SDLScancode.SDL_SCANCODE_V
    const val KEY_W = SDLScancode.SDL_SCANCODE_W
    const val KEY_X = SDLScancode.SDL_SCANCODE_X
    const val KEY_Y = SDLScancode.SDL_SCANCODE_Y
    const val KEY_Z = SDLScancode.SDL_SCANCODE_Z
    const val KEY_LEFT_BRACKET = SDLScancode.SDL_SCANCODE_LEFTBRACKET
    const val KEY_BACKSLASH = SDLScancode.SDL_SCANCODE_BACKSLASH
    const val KEY_RIGHT_BRACKET = SDLScancode.SDL_SCANCODE_RIGHTBRACKET
    const val KEY_GRAVE_ACCENT = SDLScancode.SDL_SCANCODE_GRAVE
    const val KEY_WORLD_1 = SDLScancode.SDL_SCANCODE_NONUSBACKSLASH
    const val KEY_WORLD_2 = SDLScancode.SDL_SCANCODE_NONUSHASH
    const val KEY_ESCAPE = SDLScancode.SDL_SCANCODE_ESCAPE
    const val KEY_ENTER = SDLScancode.SDL_SCANCODE_RETURN
    const val KEY_TAB = SDLScancode.SDL_SCANCODE_TAB
    const val KEY_BACKSPACE = SDLScancode.SDL_SCANCODE_BACKSPACE
    const val KEY_INSERT = SDLScancode.SDL_SCANCODE_INSERT
    const val KEY_DELETE = SDLScancode.SDL_SCANCODE_DELETE
    const val KEY_RIGHT = SDLScancode.SDL_SCANCODE_RIGHT
    const val KEY_LEFT = SDLScancode.SDL_SCANCODE_LEFT
    const val KEY_DOWN = SDLScancode.SDL_SCANCODE_DOWN
    const val KEY_UP = SDLScancode.SDL_SCANCODE_UP
    const val KEY_PAGE_UP = SDLScancode.SDL_SCANCODE_PAGEUP
    const val KEY_PAGE_DOWN = SDLScancode.SDL_SCANCODE_PAGEDOWN
    const val KEY_HOME = SDLScancode.SDL_SCANCODE_HOME
    const val KEY_END = SDLScancode.SDL_SCANCODE_END
    const val KEY_CAPS_LOCK = SDLScancode.SDL_SCANCODE_CAPSLOCK
    const val KEY_SCROLL_LOCK = SDLScancode.SDL_SCANCODE_SCROLLLOCK
    const val KEY_NUM_LOCK = SDLScancode.SDL_SCANCODE_NUMLOCKCLEAR
    const val KEY_PRINT_SCREEN = SDLScancode.SDL_SCANCODE_PRINTSCREEN
    const val KEY_PAUSE = SDLScancode.SDL_SCANCODE_PAUSE
    const val KEY_F1 = SDLScancode.SDL_SCANCODE_F1
    const val KEY_F2 = SDLScancode.SDL_SCANCODE_F2
    const val KEY_F3 = SDLScancode.SDL_SCANCODE_F3
    const val KEY_F4 = SDLScancode.SDL_SCANCODE_F4
    const val KEY_F5 = SDLScancode.SDL_SCANCODE_F5
    const val KEY_F6 = SDLScancode.SDL_SCANCODE_F6
    const val KEY_F7 = SDLScancode.SDL_SCANCODE_F7
    const val KEY_F8 = SDLScancode.SDL_SCANCODE_F8
    const val KEY_F9 = SDLScancode.SDL_SCANCODE_F9
    const val KEY_F10 = SDLScancode.SDL_SCANCODE_F10
    const val KEY_F11 = SDLScancode.SDL_SCANCODE_F11
    const val KEY_F12 = SDLScancode.SDL_SCANCODE_F12
    const val KEY_F13 = SDLScancode.SDL_SCANCODE_F13
    const val KEY_F14 = SDLScancode.SDL_SCANCODE_F14
    const val KEY_F15 = SDLScancode.SDL_SCANCODE_F15
    const val KEY_F16 = SDLScancode.SDL_SCANCODE_F16
    const val KEY_F17 = SDLScancode.SDL_SCANCODE_F17
    const val KEY_F18 = SDLScancode.SDL_SCANCODE_F18
    const val KEY_F19 = SDLScancode.SDL_SCANCODE_F19
    const val KEY_F20 = SDLScancode.SDL_SCANCODE_F20
    const val KEY_F21 = SDLScancode.SDL_SCANCODE_F21
    const val KEY_F22 = SDLScancode.SDL_SCANCODE_F22
    const val KEY_F23 = SDLScancode.SDL_SCANCODE_F23
    const val KEY_F24 = SDLScancode.SDL_SCANCODE_F24
    //const val KEY_F25                                         // no SDL scancode, gone in 26.3
    const val KEY_KP_0 = SDLScancode.SDL_SCANCODE_KP_0
    const val KEY_KP_1 = SDLScancode.SDL_SCANCODE_KP_1
    const val KEY_KP_2 = SDLScancode.SDL_SCANCODE_KP_2
    const val KEY_KP_3 = SDLScancode.SDL_SCANCODE_KP_3
    const val KEY_KP_4 = SDLScancode.SDL_SCANCODE_KP_4
    const val KEY_KP_5 = SDLScancode.SDL_SCANCODE_KP_5
    const val KEY_KP_6 = SDLScancode.SDL_SCANCODE_KP_6
    const val KEY_KP_7 = SDLScancode.SDL_SCANCODE_KP_7
    const val KEY_KP_8 = SDLScancode.SDL_SCANCODE_KP_8
    const val KEY_KP_9 = SDLScancode.SDL_SCANCODE_KP_9
    const val KEY_KP_DECIMAL = SDLScancode.SDL_SCANCODE_KP_PERIOD
    const val KEY_KP_DIVIDE = SDLScancode.SDL_SCANCODE_KP_DIVIDE
    const val KEY_KP_MULTIPLY = SDLScancode.SDL_SCANCODE_KP_MULTIPLY
    const val KEY_KP_SUBTRACT = SDLScancode.SDL_SCANCODE_KP_MINUS
    const val KEY_KP_ADD = SDLScancode.SDL_SCANCODE_KP_PLUS
    const val KEY_KP_ENTER = SDLScancode.SDL_SCANCODE_KP_ENTER
    const val KEY_KP_EQUAL = SDLScancode.SDL_SCANCODE_KP_EQUALS
    const val KEY_LEFT_SHIFT = SDLScancode.SDL_SCANCODE_LSHIFT
    const val KEY_LEFT_CONTROL = SDLScancode.SDL_SCANCODE_LCTRL
    const val KEY_LEFT_ALT = SDLScancode.SDL_SCANCODE_LALT
    const val KEY_LEFT_SUPER = SDLScancode.SDL_SCANCODE_LGUI
    const val KEY_RIGHT_SHIFT = SDLScancode.SDL_SCANCODE_RSHIFT
    const val KEY_RIGHT_CONTROL = SDLScancode.SDL_SCANCODE_RCTRL
    const val KEY_RIGHT_ALT = SDLScancode.SDL_SCANCODE_RALT
    const val KEY_RIGHT_SUPER = SDLScancode.SDL_SCANCODE_RGUI
    const val KEY_MENU = SDLScancode.SDL_SCANCODE_APPLICATION

    //@formatter:on
    //endregion
    //region const val MOUSE_s
    // kept in the historic -100..-93 range so existing keybind configs keep their meaning.
    // SDL reordered middle/right, so BUTTON_2 and BUTTON_3 trade values compared to GLFW.
    const val MOUSE_BUTTON_1 = SDLMouse.SDL_BUTTON_LEFT - 101   // SDL 1 -> -100
    const val MOUSE_BUTTON_2 = SDLMouse.SDL_BUTTON_RIGHT - 101  // SDL 3 ->  -98
    const val MOUSE_BUTTON_3 = SDLMouse.SDL_BUTTON_MIDDLE - 101 // SDL 2 ->  -99
    const val MOUSE_BUTTON_4 = SDLMouse.SDL_BUTTON_X1 - 101     // SDL 4 ->  -97
    const val MOUSE_BUTTON_5 = SDLMouse.SDL_BUTTON_X2 - 101     // SDL 5 ->  -96
    const val MOUSE_BUTTON_6 = 6 - 101                          //       ->  -95
    const val MOUSE_BUTTON_7 = 7 - 101                          //       ->  -94
    const val MOUSE_BUTTON_8 = 8 - 101                          //       ->  -93
    //endregion

    //@formatter:on
    //endregion
    //region const val MOUSE_SCROLL_
    // synthetic, libIPN only - SDL has no button code for the wheel
    const val MOUSE_SCROLL_UP = -10000
    const val MOUSE_SCROLL_DOWN = -10001
    const val MOUSE_SCROLL_LEFT = -10002
    const val MOUSE_SCROLL_RIGHT = -10003
    //endregion

    fun getName(keyCode: Int): String {
        return MAP_KEY_CODE_TO_NAME.getOrDefault(keyCode,
                                                 "keycode $keyCode")
    }

    fun getKeyCode(name: String): Int {
        return MAP_NAME_TO_KEY_CODE.getOrDefault(name,
                                                 -1)
    }

    fun getFriendlyName(name: String): String {
        return MAP_NAME_TO_DISPLAY_TEXT.getOrDefault(name,
                                                     name)
    }

    fun getFriendlyName(keyCode: Int): String {
        val name = getName(keyCode)
        // glfwGetKeyName() equivalent: resolve the scancode through the current layout and
        // keep the system name only when it is a single printable character, otherwise fall
        // back to our own display table (SDL returns "Left Shift" where GLFW returned null)
        val localizedName = if (keyCode > 0 && keyCode < SDLScancode.SDL_SCANCODE_COUNT) {
            SDLKeyboard.SDL_GetKeyName(SDLKeyboard.SDL_GetKeyFromScancode(keyCode,
                                                                          0.toShort(),
                                                                          false))
                ?.takeIf { it.codePointCount(0, it.length) == 1 }
        } else {
            null
        }
        return localizedName ?: getFriendlyName(name)
    }

    val modifiers: Set<Int>
        get() = MODIFIER_KEY_CODES.keys

    fun getModifierName(keyCode: Int): String {
        return MODIFIER_DISPLAY_TEXTS.getOrElse(keyCode,
                                                { getFriendlyName(keyCode) })
    }

    fun getModifierKeyCode(keyCode: Int): Int {
        return MODIFIER_KEY_CODES.getOrDefault(keyCode,
                                               keyCode)
    }

    fun isKeyDown(keyCode: Int): Boolean {
        // keyboard: SDL scancode, indexes the keyboard state array
        if (keyCode > 0) {
            if (keyCode >= SDLScancode.SDL_SCANCODE_COUNT) return false
            val keyboardState = SDLKeyboard.SDL_GetKeyboardState() ?: return false
            return keyboardState.get(keyCode) != 0.toByte()
        }
        // mouse: our -100..-93 ids map back to SDL buttons 1..8; scroll ids fall outside
        val button = keyCode + 101
        return button in 1..32 &&
                (SDLMouse.SDL_GetMouseState(null, null) and (1 shl (button - 1))) != 0
    }

    /**
     * Converts a mouse button as Minecraft reports it into our key code space.
     * Values that are already negative are our own synthetic codes (the scroll
     * sentinels) and pass through untouched.
     */
    fun mouseButtonToKeyCode(button: Int): Int =
        if (button >= 0) button - 101 else button

    // ============
    // private
    // ============

    private val MAP_KEY_CODE_TO_NAME = mutableMapOf<Int, String>()
    private val MAP_NAME_TO_KEY_CODE = mutableMapOf<String, Int>()
    private val MAP_NAME_TO_DISPLAY_TEXT = mutableMapOf<String, String>()

    private fun addEntry(name: String,
                         displayText: String?,
                         keyCode: Int) {
        MAP_KEY_CODE_TO_NAME.getOrPut(keyCode,
                                      { name })
        MAP_NAME_TO_KEY_CODE.getOrPut(name,
                                      { keyCode })
        MAP_NAME_TO_DISPLAY_TEXT.getOrPut(name,
                                          { if (displayText.isNullOrEmpty()) name else displayText })
    }

    private val MODIFIER_KEY_CODES = mutableMapOf<Int, Int>()
    private val MODIFIER_DISPLAY_TEXTS = mutableMapOf<Int, String>()
    private fun addModifier(displayText: String,
                            keyCode: Int,
                            vararg moreKeyCodes: Int) {
        val modifiers = moreKeyCodes + keyCode
        modifiers.forEach { MODIFIER_KEY_CODES[it] = keyCode }
        modifiers.forEach { MODIFIER_DISPLAY_TEXTS[it] = displayText }
    }

    init {
        //region addEntry()s
        //@formatter:off
        addEntry("UNKNOWN",
                 null,
                 KEY_UNKNOWN)
        addEntry("SPACE",
                 "Space",
                 KEY_SPACE)
        addEntry("APOSTROPHE",
                 "'",
                 KEY_APOSTROPHE)
        addEntry("COMMA",
                 ",",
                 KEY_COMMA)
        addEntry("MINUS",
                 "-",
                 KEY_MINUS)
        addEntry("PERIOD",
                 ".",
                 KEY_PERIOD)
        addEntry("SLASH",
                 "/",
                 KEY_SLASH)
        addEntry("0",
                 "0",
                 KEY_0)
        addEntry("1",
                 "1",
                 KEY_1)
        addEntry("2",
                 "2",
                 KEY_2)
        addEntry("3",
                 "3",
                 KEY_3)
        addEntry("4",
                 "4",
                 KEY_4)
        addEntry("5",
                 "5",
                 KEY_5)
        addEntry("6",
                 "6",
                 KEY_6)
        addEntry("7",
                 "7",
                 KEY_7)
        addEntry("8",
                 "8",
                 KEY_8)
        addEntry("9",
                 "9",
                 KEY_9)
        addEntry("SEMICOLON",
                 ";",
                 KEY_SEMICOLON)
        addEntry("EQUAL",
                 "=",
                 KEY_EQUAL)
        addEntry("A",
                 "a",
                 KEY_A)
        addEntry("B",
                 "b",
                 KEY_B)
        addEntry("C",
                 "c",
                 KEY_C)
        addEntry("D",
                 "d",
                 KEY_D)
        addEntry("E",
                 "e",
                 KEY_E)
        addEntry("F",
                 "f",
                 KEY_F)
        addEntry("G",
                 "g",
                 KEY_G)
        addEntry("H",
                 "h",
                 KEY_H)
        addEntry("I",
                 "i",
                 KEY_I)
        addEntry("J",
                 "j",
                 KEY_J)
        addEntry("K",
                 "k",
                 KEY_K)
        addEntry("L",
                 "l",
                 KEY_L)
        addEntry("M",
                 "m",
                 KEY_M)
        addEntry("N",
                 "n",
                 KEY_N)
        addEntry("O",
                 "o",
                 KEY_O)
        addEntry("P",
                 "p",
                 KEY_P)
        addEntry("Q",
                 "q",
                 KEY_Q)
        addEntry("R",
                 "r",
                 KEY_R)
        addEntry("S",
                 "s",
                 KEY_S)
        addEntry("T",
                 "t",
                 KEY_T)
        addEntry("U",
                 "u",
                 KEY_U)
        addEntry("V",
                 "v",
                 KEY_V)
        addEntry("W",
                 "w",
                 KEY_W)
        addEntry("X",
                 "x",
                 KEY_X)
        addEntry("Y",
                 "y",
                 KEY_Y)
        addEntry("Z",
                 "z",
                 KEY_Z)
        addEntry("LEFT_BRACKET",
                 "[",
                 KEY_LEFT_BRACKET)
        addEntry("BACKSLASH",
                 "\\",
                 KEY_BACKSLASH)
        addEntry("RIGHT_BRACKET",
                 "]",
                 KEY_RIGHT_BRACKET)
        addEntry("GRAVE_ACCENT",
                 "`",
                 KEY_GRAVE_ACCENT)
        addEntry("WORLD_1",
                 null,
                 KEY_WORLD_1)
        addEntry("WORLD_2",
                 null,
                 KEY_WORLD_2)
        addEntry("ESCAPE",
                 "Esc",
                 KEY_ESCAPE)
        addEntry("ENTER",
                 "Enter",
                 KEY_ENTER)
        addEntry("TAB",
                 "Tab",
                 KEY_TAB)
        addEntry("BACKSPACE",
                 "Backspace",
                 KEY_BACKSPACE)
        addEntry("INSERT",
                 "Insert",
                 KEY_INSERT)
        addEntry("DELETE",
                 "Delete",
                 KEY_DELETE)
        addEntry("RIGHT",
                 "Right",
                 KEY_RIGHT)
        addEntry("LEFT",
                 "Left",
                 KEY_LEFT)
        addEntry("DOWN",
                 "Down",
                 KEY_DOWN)
        addEntry("UP",
                 "Up",
                 KEY_UP)
        addEntry("PAGE_UP",
                 "Page Up",
                 KEY_PAGE_UP)
        addEntry("PAGE_DOWN",
                 "Page Down",
                 KEY_PAGE_DOWN)
        addEntry("HOME",
                 "Home",
                 KEY_HOME)
        addEntry("END",
                 "End",
                 KEY_END)
        addEntry("CAPS_LOCK",
                 "Caps Lock",
                 KEY_CAPS_LOCK)
        addEntry("SCROLL_LOCK",
                 "Scroll Lock",
                 KEY_SCROLL_LOCK)
        addEntry("NUM_LOCK",
                 "Num Lock",
                 KEY_NUM_LOCK)
        addEntry("PRINT_SCREEN",
                 "Print Screen",
                 KEY_PRINT_SCREEN)
        addEntry("PAUSE",
                 "Pause",
                 KEY_PAUSE)
        addEntry("F1",
                 "F1",
                 KEY_F1)
        addEntry("F2",
                 "F2",
                 KEY_F2)
        addEntry("F3",
                 "F3",
                 KEY_F3)
        addEntry("F4",
                 "F4",
                 KEY_F4)
        addEntry("F5",
                 "F5",
                 KEY_F5)
        addEntry("F6",
                 "F6",
                 KEY_F6)
        addEntry("F7",
                 "F7",
                 KEY_F7)
        addEntry("F8",
                 "F8",
                 KEY_F8)
        addEntry("F9",
                 "F9",
                 KEY_F9)
        addEntry("F10",
                 "F10",
                 KEY_F10)
        addEntry("F11",
                 "F11",
                 KEY_F11)
        addEntry("F12",
                 "F12",
                 KEY_F12)
        addEntry("F13",
                 null,
                 KEY_F13)
        addEntry("F14",
                 null,
                 KEY_F14)
        addEntry("F15",
                 null,
                 KEY_F15)
        addEntry("F16",
                 null,
                 KEY_F16)
        addEntry("F17",
                 null,
                 KEY_F17)
        addEntry("F18",
                 null,
                 KEY_F18)
        addEntry("F19",
                 null,
                 KEY_F19)
        addEntry("F20",
                 null,
                 KEY_F20)
        addEntry("F21",
                 null,
                 KEY_F21)
        addEntry("F22",
                 null,
                 KEY_F22)
        addEntry("F23",
                 null,
                 KEY_F23)
        addEntry("F24",
                 null,
                 KEY_F24)
        //addEntry("F25",
        //         null,
        //         KEY_F25)
        addEntry("KP_0",
                 "Numpad 0",
                 KEY_KP_0)
        addEntry("KP_1",
                 "Numpad 1",
                 KEY_KP_1)
        addEntry("KP_2",
                 "Numpad 2",
                 KEY_KP_2)
        addEntry("KP_3",
                 "Numpad 3",
                 KEY_KP_3)
        addEntry("KP_4",
                 "Numpad 4",
                 KEY_KP_4)
        addEntry("KP_5",
                 "Numpad 5",
                 KEY_KP_5)
        addEntry("KP_6",
                 "Numpad 6",
                 KEY_KP_6)
        addEntry("KP_7",
                 "Numpad 7",
                 KEY_KP_7)
        addEntry("KP_8",
                 "Numpad 8",
                 KEY_KP_8)
        addEntry("KP_9",
                 "Numpad 9",
                 KEY_KP_9)
        addEntry("KP_DECIMAL",
                 "Numpad .",
                 KEY_KP_DECIMAL)
        addEntry("KP_DIVIDE",
                 "Numpad /",
                 KEY_KP_DIVIDE)
        addEntry("KP_MULTIPLY",
                 "Numpad *",
                 KEY_KP_MULTIPLY)
        addEntry("KP_SUBTRACT",
                 "Numpad -",
                 KEY_KP_SUBTRACT)
        addEntry("KP_ADD",
                 "Numpad +",
                 KEY_KP_ADD)
        addEntry("KP_ENTER",
                 "Numpad Enter",
                 KEY_KP_ENTER)
        addEntry("KP_EQUAL",
                 null,
                 KEY_KP_EQUAL)
        addEntry("LEFT_SHIFT",
                 "Left Shift",
                 KEY_LEFT_SHIFT)
        addEntry("LEFT_CONTROL",
                 "Left Ctrl",
                 KEY_LEFT_CONTROL)
        addEntry("LEFT_ALT",
                 "Left Alt",
                 KEY_LEFT_ALT)
        addEntry("LEFT_SUPER",
                 "Left Win",
                 KEY_LEFT_SUPER)
        addEntry("RIGHT_SHIFT",
                 "Right Shift",
                 KEY_RIGHT_SHIFT)
        addEntry("RIGHT_CONTROL",
                 "Right Ctrl",
                 KEY_RIGHT_CONTROL)
        addEntry("RIGHT_ALT",
                 "Right Alt",
                 KEY_RIGHT_ALT)
        addEntry("RIGHT_SUPER",
                 "Right Win",
                 KEY_RIGHT_SUPER)
        addEntry("MENU",
                 "Menu",
                 KEY_MENU)
        addEntry("BUTTON_1",
                 "Left Button",
                 MOUSE_BUTTON_1)
        addEntry("BUTTON_2",
                 "Right Button",
                 MOUSE_BUTTON_2)
        addEntry("BUTTON_3",
                 "Middle Button",
                 MOUSE_BUTTON_3)
        addEntry("BUTTON_4",
                 "Back Button",
                 MOUSE_BUTTON_4)
        addEntry("BUTTON_5",
                 "Forward Button",
                 MOUSE_BUTTON_5)
        addEntry("BUTTON_6",
                 null,
                 MOUSE_BUTTON_6)
        addEntry("BUTTON_7",
                 null,
                 MOUSE_BUTTON_7)
        addEntry("BUTTON_8",
                 null,
                 MOUSE_BUTTON_8)

        addEntry("MOUSE_SCROLL_UP",
                 "Scroll Wheel Up",
                 MOUSE_SCROLL_UP)
        addEntry("MOUSE_SCROLL_DOWN",
                 "Scroll Wheel Down",
                 MOUSE_SCROLL_DOWN)
        addEntry("MOUSE_SCROLL_LEFT",
                 "Scroll Wheel Left",
                 MOUSE_SCROLL_LEFT)
        addEntry("MOUSE_SCROLL_RIGHT",
                 "Scroll Wheel Right",
                 MOUSE_SCROLL_RIGHT)

        //@formatter:on
        //endregion
        //@formatter:off
        addModifier("Shift",
                    KEY_LEFT_SHIFT,
                    KEY_RIGHT_SHIFT)
        addModifier("Ctrl",
                    KEY_LEFT_CONTROL,
                    KEY_RIGHT_CONTROL)
        addModifier("Alt",
                    KEY_LEFT_ALT,
                    KEY_RIGHT_ALT)
        addModifier("Win",
                    KEY_LEFT_SUPER,
                    KEY_RIGHT_SUPER)
        //@formatter:on
    }
}
