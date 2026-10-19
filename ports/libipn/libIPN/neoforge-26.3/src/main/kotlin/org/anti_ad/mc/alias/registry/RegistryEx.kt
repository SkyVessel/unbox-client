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

@file:Suppress("unused", "ObjectPropertyName", "HasPlatformType")
package org.anti_ad.mc.alias.registry

import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier

val `(REGISTRIES-BLOCK_ENTITY_TYPES-IDS)`
    get() = BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet()

val `(REGISTRIES-BLOCK-IDS)`
    get() = BuiltInRegistries.BLOCK.keySet()

val `(REGISTRIES-CONTAINER-IDS)`
    get() = BuiltInRegistries.MENU.keySet()

val `(REGISTRIES-ITEM-IDS)`
    get() = BuiltInRegistries.ITEM.keySet()

@Suppress("FunctionName")
fun <T: Any> Registry<T>.`(get)`(id: Identifier) = getValue(id)
