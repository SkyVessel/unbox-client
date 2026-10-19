/*
 * Inventory Profiles Next
 *
 *   Copyright (c) 2021-2024 Plamen K. Kosseff <p.kosseff@gmail.com>
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

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.util.Mth
import org.anti_ad.mc.common.gui.NativeContext
import org.anti_ad.mc.common.gui.NativeInputContext
import org.anti_ad.mc.common.gui.NativeInputContextBase
import org.anti_ad.mc.common.gui.widgets.glue.IButtonWidget
import org.anti_ad.mc.common.gui.widgets.glue.ISliderWidget
import org.anti_ad.mc.common.gui.widgets.glue.ITextFieldWidget
import org.anti_ad.mc.common.input.KeyCodes
import org.anti_ad.mc.common.math2d.Size
import org.anti_ad.mc.common.vanilla.Vanilla
import org.anti_ad.mc.common.vanilla.VanillaSound
import net.minecraft.client.gui.components.AbstractSliderButton as VanillaSliderWidget
import net.minecraft.client.gui.components.EditBox as VanillaTextFieldWidget

// ============
// vanillamapping code depends on mappings
// ============

@Suppress("UNCHECKED_CAST")
open class VanillaWidget<T: AbstractWidget>(val vanilla: T): Widget() {

    init {
        sizeChanged += {
            vanilla.width = width // TODO set height
        }
        screenLocationChanged += {
            vanilla.x = screenX
            vanilla.y = screenY
        }
    }

    var vanillaMessage: String
        get() = vanilla.message.string
        set(value) {
            vanilla.message = Component.literal(value)
        }

    override fun render(context: NativeContext,
                        mouseX: Int,
                        mouseY: Int,
                        partialTicks: Float) {

        vanilla.extractRenderState(context.native, mouseX, mouseY, partialTicks)
        super.render(context, mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(context: NativeInputContextBase<*>,
                              x: Int,
                              y: Int,
                              button: Int): Boolean {
        if (!absoluteBounds.contains(x, y)) {
            return false
        }
        if (button <= KeyCodes.MOUSE_SCROLL_UP && button >= KeyCodes.MOUSE_SCROLL_RIGHT) {
            val vertical = when (button) {
                KeyCodes.MOUSE_SCROLL_UP -> 1.0
                KeyCodes.MOUSE_SCROLL_DOWN -> -1.0
                else -> 0.0
            }
            val horizontal = when (button) {
                KeyCodes.MOUSE_SCROLL_LEFT -> 1.0
                KeyCodes.MOUSE_SCROLL_RIGHT -> -1.0
                else -> 0.0
            }
            return mouseScrolled(context, x, y, horizontal, vertical)
        }
        val sc = super.mouseClicked(context, x, y, button)
        if (!sc) {
            return vanilla.mouseClicked(context.value as MouseButtonEvent, context.doubled)
        }
        return true

    }

    override fun mouseReleased(context: NativeInputContextBase<*>,
                               x: Int,
                               y: Int,
                               button: Int): Boolean {
        if (!absoluteBounds.contains(x, y)) {
            return false
        }
        if (button <= KeyCodes.MOUSE_SCROLL_UP && button >= KeyCodes.MOUSE_SCROLL_RIGHT) {
            return false
        }
        val sc = super.mouseReleased(context, x, y, button)
        return sc || vanilla.mouseReleased(context.value as MouseButtonEvent)
    }

    override fun mouseScrolled(context: NativeInputContextBase<*>,
                               x: Int,
                               y: Int,
                               horizontal: Double,
                               vertical: Double): Boolean {
        if (!absoluteBounds.contains(x, y)) {
            return false
        }
        return super.mouseScrolled(context, x, y, horizontal, vertical) || vanilla.mouseScrolled(x.toDouble(), y.toDouble(), horizontal, vertical)
    }

    override fun mouseDragged(context: NativeInputContextBase<*>,
                              x: Double,
                              y: Double,
                              button: Int,
                              dx: Double,
                              dy: Double): Boolean {
        if (!absoluteBounds.contains(x, y)) {
            return false
        }

        return super.mouseDragged(context, x, y, button, dx, dy) || vanilla.mouseDragged((context as NativeInputContextBase<MouseButtonEvent>).value, dx, dy)
    }

    override fun keyPressed(context: NativeInputContextBase<*>,
                            keyCode: Int,
                            scanCode: Int,
                            modifiers: Int): Boolean {

        return super.keyPressed(context, keyCode, scanCode, modifiers)
                || vanilla.keyPressed((context as NativeInputContextBase<KeyEvent>).value)
    }

    override fun keyReleased(context: NativeInputContextBase<*>,
                             keyCode: Int,
                             scanCode: Int,
                             modifiers: Int): Boolean {

        return super.keyReleased(context, keyCode, scanCode, modifiers)
               || vanilla.keyReleased((context as NativeInputContextBase<KeyEvent>).value)
    }

    override fun charTyped(context: NativeInputContextBase<*>,
                           charIn: Char,
                           modifiers: Int): Boolean {

        return super.charTyped(context, charIn, modifiers) || vanilla.charTyped((context as NativeInputContextBase<CharacterEvent>).value)
    }

}

private class CustomVanillaSliderWidget(val minValue: Double,
                                        val maxValue: Double): VanillaSliderWidget(0, 0, 0, 20, Component.literal(""), 0.5) {

    var valueChangedEvent: () -> Unit = { }

    override fun updateMessage() {}
    override fun applyValue() {
        valueChangedEvent()
    }

    var translatedValue: Double
        get() = (maxValue - minValue) * super.value + minValue
        set(value) {
            super.value = (value - minValue) / (maxValue - minValue)
        }

/*
    override fun extractWidgetRenderState(drawContext: GuiGraphicsExtractor,
                                          i: Int,
                                          j: Int,
                                          f: Float) { // fix slider width > 400
        val hovered = isHovered

        super.extractWidgetRenderState(drawContext, i, j, f)
        val l = if (active) if (hovered) 16777120 else 14737632 else 10526880
        drawContext.text(Vanilla.textRenderer(), message, (x + width) / 2, y + (height - 8) / 2, l or (Mth.ceil(alpha * 255.0f) shl 24))
    }
*/
}

fun newSliderWidget(minValue: Double = 0.0,
                    maxValue: Double = 1.0): ISliderWidget = SliderWidget(minValue, maxValue)

private class SliderWidget(override val minValue: Double = 0.0,
                           override val maxValue: Double = 1.0): ISliderWidget, VanillaWidget<VanillaSliderWidget>(CustomVanillaSliderWidget(minValue, maxValue)) {

    private val silder
        get() = vanilla as CustomVanillaSliderWidget

    override var valueChangedEvent: () -> Unit
        get() = silder.valueChangedEvent
        set(value) {
            silder.valueChangedEvent = value
        }

    override var value: Double
        get() = silder.translatedValue
        set(value) {
            silder.translatedValue = value
        }
}

open class NativeButtonWidget(): Button.Plain(0,
                                              0,
                                              0,
                                              20,
                                              Component.literal(""),
                                              {},
                                              Button.DEFAULT_NARRATION) {

    var sHeight
        get() = super.height
        set(value) {
            super.height = value
        }

    override fun mouseClicked(click: MouseButtonEvent,
                              doubled: Boolean): Boolean {
        return mouseClicked(NativeInputContext(click, doubled),
                            click.x.toInt(),
                            click.y.toInt(),
                            click.button())
    }

    fun mouseClicked(context: NativeInputContextBase<*>,
                     x: Int,
                     y: Int,
                     button: Int): Boolean {
        return true
    }

}

open class CustomButtonWidget(): IButtonWidget, VanillaWidget<NativeButtonWidget>(NativeButtonWidget()) {

    override var clickEvent: (Int) -> Unit = {}

    override var clickThrough: Boolean = false

    var sizeModifier: Size = Size(0, 0)

    override var active: Boolean
        get() = vanilla.active
        set(value) {
            vanilla.active = value
        }

    override var size: Size
        get() = Size(vanilla.width, vanilla.sHeight)
        set(value) {
            vanilla.sHeight = value.height
            vanilla.width = value.width
            super.size = value
        }

    init {
        sizeChanged += {
            vanilla.sHeight = it.newValue.height
            vanilla.width = it.newValue.width
        }
    }

    override var text: String = ""
        set(value) {
            field = value
            vanilla.message = Component.literal(field)
        }

    constructor(clickEvent: (button: Int) -> Unit): this() {
        this.clickEvent = { button ->
            VanillaSound.playClick()
            clickEvent(button)
        }
    }

    constructor(clickEvent: () -> Unit): this() {
        this.clickEvent = { button ->
            if (button == InputConstants.MOUSE_BUTTON_LEFT) {
                VanillaSound.playClick()
                clickEvent()
            }
        }
    }

    override fun renderButton(context: NativeContext,
                              hovered: Boolean) {

    }

    override fun render(context: NativeContext,
                        mouseX: Int,
                        mouseY: Int,
                        partialTicks: Float) {
        vanilla.x = screenX
        vanilla.y = screenY
        vanilla.width = bounds.width + sizeModifier.width
        vanilla.sHeight = bounds.height + sizeModifier.height
        super.render(context, mouseX, mouseY, partialTicks)
    }

    override fun mouseClicked(context: NativeInputContextBase<*>,
                              x: Int,
                              y: Int,
                              button: Int): Boolean {
        if (active) onClick(button)
        return !clickThrough
    }

    override fun onClick(button: Int) {
        clickEvent(button)
    }

}

fun newButtonWidget(): IButtonWidget = CustomButtonWidget()
fun newButtonWidget(clickEvent: () -> Unit): IButtonWidget = CustomButtonWidget(clickEvent)
fun newButtonWidget(clickEvent: (button: Int) -> Unit): IButtonWidget = CustomButtonWidget(clickEvent)

private class CustomTextFieldWidget(textRenderer: Font,
                                    i: Int,
                                    j: Int,
                                    k: Int,
                                    l: Int,
                                    string: String?): VanillaTextFieldWidget(textRenderer, i, j, k, l, Component.literal(string ?:"")) {

    override fun setFocused(bl: Boolean) {
        super.setFocused(bl)
    }

    init {
        setMaxLength(32767)
    }
}

fun newTextFieldWidget(height: Int): ITextFieldWidget = TextFieldWidget(height)

private class TextFieldWidget(height: Int): ITextFieldWidget, VanillaWidget<VanillaTextFieldWidget>(CustomTextFieldWidget(Vanilla.textRenderer(), 0, 0, 0, height, "")) {

    override var textPredicate: (string: String) -> Boolean = { true }
    override var changedEvent: (string: String) -> Unit = { }
        set(value) {
            field = value
            vanilla.setResponder {
                value(it)
            }
        }

    override var vanillaText: String
        get() = vanilla.value
        set(value) {
            if (textPredicate(value)) {
                if (vanilla.value != value) {
                    vanilla.value = value
                }
            }
        }

    override var vanillaFocused: Boolean
        get() = vanilla.isFocused
        set(value) {
            (vanilla as CustomTextFieldWidget).isFocused = value
        }

    override fun gotFocus() {
        super.gotFocus()
        vanillaFocused = true
    }

    override fun lostFocus() {
        super.lostFocus()
        vanillaFocused = false
    }

    override fun editing(): Boolean = vanilla.isFocused

    init {
        this.textPredicate = textPredicate
        this.changedEvent = changedEvent
    }
}
