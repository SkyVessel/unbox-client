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

package org.anti_ad.mc.alias.nbt

import net.minecraft.nbt.Tag
import net.minecraft.nbt.NbtUtils

fun NbtHelper_toFormattedString(element: Tag, boolean: Boolean) = NbtUtils.toPrettyComponent(element).string
fun NbtHelper_toFormattedString(element: Tag) = NbtUtils.toPrettyComponent(element).string
//fun NbtHelper_toFormattedString(sb: StringBuilder, element: Tag, level: Int, boolean: Boolean) = NbtUtils.prettyPrint(sb, element, level, boolean)
