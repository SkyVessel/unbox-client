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

package org.anti_ad.mc.common.vanilla

import com.mojang.blaze3d.Blaze3D
import net.minecraft.client.gui.screens.PrivacyConfirmLinkScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import org.anti_ad.mc.common.extensions.createDirectories
import org.anti_ad.mc.common.extensions.div
import org.anti_ad.mc.common.extensions.pathFrom
import org.anti_ad.mc.common.extensions.tryCatch
import org.anti_ad.mc.common.input.KeybindSettings
import org.anti_ad.mc.common.input.MainKeybind
import java.io.File
import java.net.URL
import java.nio.file.Path
import kotlin.concurrent.thread

object VanillaUtil {

    val shiftKey: MainKeybind = MainKeybind("LEFT_SHIFT", KeybindSettings.ANY_DEFAULT)
    val ctrlKey: MainKeybind = MainKeybind("LEFT_CONTROL", KeybindSettings.ANY_DEFAULT)
    val altKey: MainKeybind = MainKeybind("LEFT_ALT", KeybindSettings.ANY_DEFAULT)

    fun isOnClientThread(): Boolean = Vanilla.mc().isSameThread

    // ============
    // info
    // ============
    fun inGame() = Vanilla.worldNullable() != null && Vanilla.playerNullable() != null

    fun languageCode(): String = Vanilla.languageManager().selected

    fun shiftDown() = shiftKey.isPressing()
    fun ctrlDown() = ctrlKey.isPressing()
    fun altDown() = altKey.isPressing()

    // Mouse.onCursorPos() / GameRenderer.render()
    fun mouseX(): Int = mouseXDouble().toInt()
    fun mouseY(): Int = mouseYDouble().toInt()
    fun mouseXRaw(): Double = Vanilla.mouse().xpos()
    fun mouseYRaw(): Double = Vanilla.mouse().ypos()
    fun mouseXDouble(): Double = mouseScaleX(mouseXRaw())
    fun mouseYDouble(): Double = mouseScaleY(mouseYRaw())
    fun mouseScaleX(amount: Double): Double = Vanilla.mouse().getScaledXPos(Vanilla.window())
    fun mouseScaleY(amount: Double): Double = Vanilla.mouse().getScaledYPos(Vanilla.window())

    // ============
    // do actions
    // ============

    private fun runDirectory(): Path = Vanilla.runDirectoryFile().toPath().normalize()
    fun configDirectory(): Path = runDirectory() / "config"
    fun configDirectory(modName: String): Path = (configDirectory() / modName).apply { createDirectories() }

    fun getResourceAsString(identifier: String): String? = tryCatch {
        Vanilla.resourceManager().getResource(Identifier.parse(identifier)).get().open().reader().readText()
    }

    fun loggingString(path: Path): String = // return ".minecraft/config/file.txt" etc
            (if (path.isAbsolute) path pathFrom (runDirectory() / "..") else path).toString()

    fun open(file: File) {
        // ResourcePackOptionsScreen.init()
        //todo check with every version if this is safe to do!!!!!
        thread { ->
            Blaze3D.openPath(file.toPath())
            //Util.getPlatform().openFile(file)
        }
    }

    fun open(url: URL) {
        // ResourcePackOptionsScreen.init()
        thread { ->
            PrivacyConfirmLinkScreen.confirmLinkNow(Vanilla.screen(), url.toURI())
            //Util.getPlatform().openUri(url.toURI())
        }
    }


    fun isValidScreen(ctx: KeybindSettings.Context) = ctx.isValid(Vanilla.screen())

    fun chat(message: Any) = Vanilla.chatHud().addClientSystemMessage(message as? Component ?: Component.literal(message.toString()))
}

private fun KeybindSettings.Context.isValid(s: Screen?) = when (this) {
    KeybindSettings.Context.INGAME -> s == null
    KeybindSettings.Context.GUI    -> s != null
    KeybindSettings.Context.ANY    -> true
}

fun showSubTitle(text: Component?) {
    if (text == null) return
    Vanilla.inGameHud().resetTitleTimes()
    Vanilla.inGameHud().setSubtitle(text)
    Vanilla.inGameHud().setTitle(Component.nullToEmpty(" "))
}

object PositionedSoundInstance {
    fun master(sound: SoundEvent, pitch: Float, volume: Float) = SimpleSoundInstance.forUI(sound, pitch, volume)
}
