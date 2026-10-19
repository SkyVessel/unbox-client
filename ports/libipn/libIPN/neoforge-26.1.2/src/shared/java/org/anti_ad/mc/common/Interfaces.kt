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

package org.anti_ad.mc.common

import org.anti_ad.mc.common.gui.NativeInputContextBase

interface Savable {
    fun save()
    fun load()
}

interface IInputHandler {
    fun onInput(context: NativeInputContextBase<*>, lastKey: Int, lastAction: Int): Boolean
}

interface ScreenEventListener { // eavesdrop event/input
    fun resize(width: Int,
               height: Int) {
    }

    //  fun mouseMoved(x: Double, y: Double) {}
    fun mouseClicked(context: NativeInputContextBase<*>,
                     x: Double,
                     y: Double,
                     button: Int) = false

    fun mouseRelease(context: NativeInputContextBase<*>,
                     x: Double,
                     y: Double,
                     button: Int) = false

    fun mouseDragged(context: NativeInputContextBase<*>,
                     x: Double,
                     y: Double,
                     button: Int,
                     dx: Double,
                     dy: Double) = false

    fun mouseScrolled(context: NativeInputContextBase<*>,
                      x: Double,
                      y: Double,
                      horizontal: Double,
                      vertical: Double) = false

    fun keyPressed(context: NativeInputContextBase<*>,
                   keyCode: Int,
                   scanCode: Int,
                   modifiers: Int) = false

    fun keyReleased(context: NativeInputContextBase<*>,
                    keyCode: Int,
                    scanCode: Int,
                    modifiers: Int) = false

    fun charTyped(context: NativeInputContextBase<*>,
                  charIn: Char,
                  modifiers: Int) = false
}
