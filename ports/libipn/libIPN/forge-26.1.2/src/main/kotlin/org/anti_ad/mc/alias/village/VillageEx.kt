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

package org.anti_ad.mc.alias.village

import net.minecraft.core.Holder
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.npc.villager.VillagerProfession
import net.minecraft.world.item.trading.MerchantOffer

val ResourceKey<VillagerProfession>.`(professionId)`
    inline get() = this.identifier().path

val Holder<VillagerProfession>.`(professionId)`
    get() = this.unwrapKey().get().`(professionId)`

val MerchantOffer.`(originalFirstBuyItem)`
    get() = itemCostA

val MerchantOffer.`(secondBuyItem)`
    get() =  if (itemCostB.isPresent) itemCostB.get() else null

val MerchantOffer.`(sellItem)`
    get() = result

val MerchantOffer.`(isDisabled)`
    get() = isOutOfStock
