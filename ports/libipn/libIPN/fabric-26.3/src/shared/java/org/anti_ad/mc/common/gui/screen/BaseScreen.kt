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

package org.anti_ad.mc.common.gui.screen

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import org.anti_ad.mc.common.gui.NativeContext
import org.anti_ad.mc.common.gui.widgets.RootWidget
import org.anti_ad.mc.common.gui.widgets.Widget
import org.anti_ad.mc.common.math2d.Size
import org.anti_ad.mc.common.vanilla.VanillaScreenUtil
import org.anti_ad.mc.common.gui.NativeInputContext
import org.anti_ad.mc.common.gui.NativeInputContextBase
import org.anti_ad.mc.common.vanilla.glue.IScreenMarker
/*
import org.anti_ad.mc.common.vanilla.render.rClearDepth
import org.anti_ad.mc.common.vanilla.render.rStandardGlState
*/

// ============
// vanillamapping code depends on mappings (package org.anti_ad.mc.common.gui.screen)
// ============

abstract class BaseScreen(text: Component) : Screen(text), IScreenMarker {

    constructor() : this(Component.literal(""))

    var isClosing: Boolean = false

    var parent: Screen? = null

    val titleString: String
        get() = this.title.string

    protected var blurEnabled = true

    open val screenInfo: ScreenInfo
        get() {
            parent?.let { p ->
                if (p is BaseScreen) {
                    return p.screenInfo
                } else {
                    return if (p.isPauseScreen) {
                        ScreenInfo.pausing
                    } else {
                        ScreenInfo.default
                    }
                }
            }
            return ScreenInfo.default
        }

    open fun closeScreen() {
        this.isClosing = true
        VanillaScreenUtil.openScreenNullable(parent)
        this.isClosing = false
    }

    fun hasParent(screen: Screen): Boolean {
        val parents = mutableSetOf<BaseScreen>()
        var currentParent = this
        while (currentParent != screen) {
            parents.add(currentParent)
            currentParent = (currentParent.parent as? BaseScreen) ?: return (currentParent.parent == screen)
            if (currentParent in parents) { // loop
                return false
            }
        }
        return true
    }

    // ============
    // widget
    // ============
    val rootWidget = RootWidget()
    fun addWidget(widget: Widget) {
        rootWidget.addChild(widget)
    }

    fun dumpWidgetTree() {
        rootWidget.dumpWidgetTree()
    }

    fun internalClearWidgets() {
        rootWidget.clearChildren()
    }

    // ============
    // render
    // ============
    open fun renderWidgetPre(context: NativeContext,
                             mouseX: Int,
                             mouseY: Int,
                             partialTicks: Float) {
    }

    open fun render(context: NativeContext,
                    mouseX: Int,
                    mouseY: Int,
                    partialTicks: Float) {

        super.extractRenderState(context.native,
                                 mouseX,
                                 mouseY,
                                 partialTicks)
        renderWidgetPre(context,
                        mouseX,
                        mouseY,
                        partialTicks)
        rootWidget.render(context,
                          mouseX,
                          mouseY,
                          partialTicks)
    }

    override fun extractRenderState(drawContext: GuiGraphicsExtractor,
                                    mouseX: Int,
                                    mouseY: Int,
                                    delta: Float) {

        render(NativeContext(drawContext, /*RenderPipelines.GUI*/),
               mouseX,
               mouseY,
               delta)
    }

    // ============
    // vanilla overrides
    // ============
    final override fun isPauseScreen(): Boolean = screenInfo.isPauseScreen
    final override fun onClose() {
        if (!isClosing) {
            closeScreen()
        }
        isClosing = false
    }

    // ============
    // event delegates
    // ============
    override fun resize(width: Int,
                        height: Int) {
        super.resize(width,
                     height)
        rootWidget.size = Size(width,
                               height)
    }

    override fun mouseClicked(click: MouseButtonEvent,
                              doubled: Boolean): Boolean {
        return mouseClicked(NativeInputContext(click, doubled),
                            click.x,
                            click.y,
                            click.button())
    }

    open fun mouseClicked(context: NativeInputContextBase<*>,
                          x: Double,
                          y: Double,
                          button: Int): Boolean = rootWidget.mouseClicked(context,
                                                                          x.toInt(),
                                                                          y.toInt(),
                                                                          button)

    override fun mouseReleased(click: MouseButtonEvent): Boolean = mouseReleased(context = NativeInputContext(click, false),
                                                                                 x = click.x,
                                                                                 y = click.y,
                                                                                 button = click.button())

    open fun mouseReleased(context: NativeInputContextBase<*>,
                           x: Double,
                           y: Double,
                           button: Int): Boolean = rootWidget.mouseReleased(context,
                                                                            x.toInt(),
                                                                            y.toInt(),
                                                                            button)

    override fun mouseDragged(click: MouseButtonEvent,
                              offsetX: Double,
                              offsetY: Double): Boolean {
        return mouseDragged(NativeInputContext(click, false),
                            click.x,
                            click.y,
                            click.button(),
                            offsetX,
                            offsetY)
    }

    open fun mouseDragged(context: NativeInputContextBase<*>,
                          x: Double,
                          y: Double,
                          button: Int,
                          offsetX: Double,
                          offsetY: Double): Boolean = rootWidget.mouseDragged(context,
                                                                              x,
                                                                              y,
                                                                              button,
                                                                              offsetX,
                                                                              offsetY) // fixme fix dx dy decimal rounding off

    override fun mouseScrolled(d: Double,
                               e: Double,
                               horisontal: Double,
                               vertical: Double): Boolean = rootWidget.mouseScrolled(NativeInputContext(null, false),
                                                                                     d.toInt(),
                                                                                     e.toInt(),
                                                                                     horisontal,
                                                                                     vertical)

    open fun mouseScrolled(context: NativeInputContextBase<*>,
                           d: Double,
                           e: Double,
                           horisontal: Double,
                           vertical: Double): Boolean {

        return rootWidget.mouseScrolled(context, d.toInt(), e.toInt(), horisontal, vertical)
    }

    override fun keyPressed(input: KeyEvent): Boolean {
        return super.keyPressed(input) || keyPressed(NativeInputContext(input, false),
                                                     input.key,
                                                     0,
                                                     input.modifiers)
    }

    open fun keyPressed(context: NativeInputContextBase<*>,
                        keyCode: Int,
                        scanCode: Int,
                        modifiers: Int): Boolean = rootWidget.keyPressed(context,
                                                                         keyCode,
                                                                         scanCode,
                                                                         modifiers)

    override fun keyReleased(input: KeyEvent): Boolean {
        return super.keyReleased(input) || keyReleased(NativeInputContext(input, false),
                                                       input.key,
                                                       0,
                                                       input.modifiers)
    }

    open fun keyReleased(context: NativeInputContextBase<*>,
                         keyCode: Int,
                         scanCode: Int,
                         modifiers: Int): Boolean = rootWidget.keyReleased(context,
                                                                           keyCode,
                                                                           scanCode,
                                                                           modifiers)

    override fun charTyped(input: CharacterEvent): Boolean {
        return input.isAllowedChatCharacter && charTyped(NativeInputContext(input, false), input.codepoint.toChar(), 0)
    }

    open fun charTyped(context: NativeInputContextBase<*>,
                       charIn: Char,
                       modifiers: Int): Boolean =
            rootWidget.charTyped(context,
                                 charIn,
                                 modifiers)

    open fun extractBlurredBackground(context: NativeContext) = extractBlurredBackground(context.native)

    override fun extractBlurredBackground(context: GuiGraphicsExtractor) {
        if (blurEnabled) {
            super.extractBlurredBackground(context)
        }
    }

}
