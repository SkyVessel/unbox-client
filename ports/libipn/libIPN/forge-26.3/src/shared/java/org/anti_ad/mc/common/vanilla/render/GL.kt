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

package org.anti_ad.mc.common.vanilla.render

import org.anti_ad.mc.common.gui.NativeContext
import org.anti_ad.mc.common.math2d.Rectangle

import org.anti_ad.mc.common.vanilla.render.glue.rFillRect

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
fun rStandardGlState() { // reset to standard state (for screen rendering)
}

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
fun rClearDepth(context: NativeContext) {
}

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
inline fun rDepthMask(context: NativeContext,
                      bounds: Rectangle,
                      block: () -> Unit) {
}

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
private val depthBounds = mutableListOf<Rectangle>()

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
fun rCreateDepthMask(context: NativeContext,
                     bounds: Rectangle) {
}

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
private fun rCreateDepthMaskNoCheck(context: NativeContext,
                                    bounds: Rectangle) {
}

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
fun rRemoveDepthMask(context: NativeContext) {
}

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
private fun rOverwriteDepth(context: NativeContext,
                            bounds: Rectangle) {
    rFillRect(context,
              bounds,
              0)
}

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
fun rDisableDepth() { // todo see if same with disableDepthTest (?)
}

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
fun rEnableDepth() {
}

@Deprecated("DON'T USE", level = DeprecationLevel.ERROR)
private fun rEnableBlend() {
}
