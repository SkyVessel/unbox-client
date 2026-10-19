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

package org.anti_ad.mc.alias.screen

import net.minecraft.world.inventory.AbstractFurnaceMenu
import net.minecraft.world.inventory.RecipeBookMenu
import net.minecraft.world.inventory.AnvilMenu
import net.minecraft.world.inventory.BeaconMenu
import net.minecraft.world.inventory.BlastFurnaceMenu
import net.minecraft.world.inventory.BrewingStandMenu
import net.minecraft.world.inventory.CartographyTableMenu
import net.minecraft.world.inventory.CrafterMenu
import net.minecraft.world.inventory.CraftingMenu
import net.minecraft.world.inventory.EnchantmentMenu
import net.minecraft.world.inventory.ItemCombinerMenu
import net.minecraft.world.inventory.FurnaceMenu
import net.minecraft.world.inventory.DispenserMenu
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.GrindstoneMenu
import net.minecraft.world.inventory.HopperMenu
import net.minecraft.world.inventory.HorseInventoryMenu
import net.minecraft.world.inventory.LecternMenu
import net.minecraft.world.inventory.LoomMenu
import net.minecraft.world.inventory.MerchantMenu
import net.minecraft.world.inventory.InventoryMenu
import net.minecraft.world.inventory.ShulkerBoxMenu
import net.minecraft.world.inventory.SmithingMenu
import net.minecraft.world.inventory.SmokerMenu
import net.minecraft.world.inventory.StonecutterMenu

val vanillaContainers = listOf<Class<*>>(AbstractFurnaceMenu::class.java,
                                         RecipeBookMenu::class.java,
                                         AnvilMenu::class.java,
                                         BeaconMenu::class.java,
                                         BlastFurnaceMenu::class.java,
                                         BrewingStandMenu::class.java,
                                         CartographyTableMenu::class.java,
                                         CrafterMenu::class.java,
                                         CraftingMenu::class.java,
                                         EnchantmentMenu::class.java,
                                         FurnaceMenu::class.java,
                                         DispenserMenu::class.java,
                                         ChestMenu::class.java,
                                         GrindstoneMenu::class.java,
                                         HopperMenu::class.java,
                                         HorseInventoryMenu::class.java,
                                         LecternMenu::class.java,
                                         LoomMenu::class.java,
                                         MerchantMenu::class.java,
                                         InventoryMenu::class.java,
                                         ShulkerBoxMenu::class.java,
                                         SmithingMenu::class.java,
                                         SmokerMenu::class.java,
                                         StonecutterMenu::class.java,
                                         ItemCombinerMenu::class.java)

val ItemCombinerMenu.`(inputSlotIndices)`
    get() = 0..<this.resultSlotIndex
