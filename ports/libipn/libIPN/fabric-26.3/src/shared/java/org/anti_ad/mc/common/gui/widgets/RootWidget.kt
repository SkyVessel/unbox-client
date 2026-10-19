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

package org.anti_ad.mc.common.gui.widgets

import org.anti_ad.mc.common.gui.NativeInputContextBase
import org.anti_ad.mc.common.gui.layout.AnchorStyles
import org.anti_ad.mc.common.math2d.Size




private fun <T> RoutedEvent<T>.orInvoke(event: T, handled: Boolean) = handled or this.invoke(event, handled)

// ============
// events
// ============
data class MouseEvent(val context: NativeInputContextBase<*>,
                      val x: Int,
                      val y: Int,
                      val button: Int)

data class MouseScrolledEvent(val context: NativeInputContextBase<*>,
                              val x: Int,
                              val y: Int,
                              val horisontal: Double,
                              val vertical: Double)

data class MouseDraggedEvent(val context: NativeInputContextBase<*>,
                             val x: Double,
                             val y: Double,
                             val button: Int,
                             val dx: Double,
                             val dy: Double)

data class KeyEvent(val context: NativeInputContextBase<*>,
                    val keyCode: Int,
                    val scanCode: Int,
                    val modifiers: Int)

data class CharTypedEvent(val context: NativeInputContextBase<*>,
                          val charIn: Char,
                          val modifiers: Int)

class RootWidget : Widget() {
    override val allowParent: Boolean
        get() = false

    init {
        anchor = AnchorStyles.all
        size = Size(containerWidth,
                    containerHeight)
    }

    val mouseClicked = RoutedEvent<MouseEvent>()
    override fun mouseClicked(context: NativeInputContextBase<*>,
                              x: Int,
                              y: Int,
                              button: Int) = mouseClicked.orInvoke(MouseEvent(context, x, y, button), super.mouseClicked(context, x, y, button))

    val mouseRelease = RoutedEvent<MouseEvent>()
    override fun mouseReleased(context: NativeInputContextBase<*>,
                               x: Int,
                               y: Int,
                               button: Int) = mouseRelease.orInvoke(MouseEvent(context, x, y, button), super.mouseReleased(context, x, y, button))

    val mouseScrolled = RoutedEvent<MouseScrolledEvent>()
    override fun mouseScrolled(context: NativeInputContextBase<*>,
                               x: Int,
                               y: Int,
                               horizontal: Double,
                               vertical: Double) = mouseScrolled.orInvoke(MouseScrolledEvent(context, x, y, horizontal, vertical),
                                                                          super.mouseScrolled(context, x, y, horizontal, vertical))

    val mouseDragged = RoutedEvent<MouseDraggedEvent>()
    override fun mouseDragged(context: NativeInputContextBase<*>,
                              x: Double,
                              y: Double,
                              button: Int,
                              dx: Double,
                              dy: Double) = mouseDragged.orInvoke(MouseDraggedEvent(context, x, y, button, dx, dy),
                                                                  super.mouseDragged(context,
                                                                                     x,
                                                                                     y,
                                                                                     button,
                                                                                     dx,
                                                                                     dy))

    val keyPressed = RoutedEvent<KeyEvent>()
    override fun keyPressed(context: NativeInputContextBase<*>,
                            keyCode: Int,
                            scanCode: Int,
                            modifiers: Int) = keyPressed.orInvoke(KeyEvent(context, keyCode, scanCode, modifiers), super.keyPressed(context,
                                                                                                                                    keyCode,
                                                                                                                                    scanCode,
                                                                                                                                    modifiers))

    val keyReleased = RoutedEvent<KeyEvent>()
    override fun keyReleased(context: NativeInputContextBase<*>,
                             keyCode: Int,
                             scanCode: Int,
                             modifiers: Int) =
            keyReleased.orInvoke(KeyEvent(context, keyCode, scanCode, modifiers), super.keyReleased(context,
                                                                                                    keyCode,
                                                                                                    scanCode,
                                                                                                    modifiers))

    val charTyped = RoutedEvent<CharTypedEvent>()
    override fun charTyped(context: NativeInputContextBase<*>,
                           charIn: Char,
                           modifiers: Int) = charTyped.orInvoke(CharTypedEvent(context, charIn, modifiers), super.charTyped(context, charIn, modifiers))
}
