/*
 * Inventory Profiles Next
 *
 *   Copyright (c) 2024 Plamen K. Kosseff <p.kosseff@gmail.com>
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

package org.anti_ad.mc.alias.text

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.chat.MutableComponent

fun fromSerializedJson(json: String): MutableComponent {
    val jsonElement: JsonElement = JsonParser.parseString(json)
    val text = ComponentSerialization.CODEC.parse(RegistryAccess.EMPTY.createSerializationContext(JsonOps.INSTANCE), jsonElement)
    return try {
        text.partialOrThrow
    } catch(e: Exception) {
        Component.literal("")
    } as MutableComponent
}
