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

@file:Suppress("unused", "HasPlatformType", "ObjectPropertyName", "FunctionName")

package org.anti_ad.mc.alias.client.gui.screen.ingame

import net.minecraft.client.gui.screens.inventory.*

//Merchant
val MerchantScreen.`(recipes)`
    get() = this.menu.offers

val MerchantScreen.`(indexStartOffset)`: Int
    get() = this.scrollOff

var MerchantScreen.`(selectedIndex)`: Int
    get() = this.shopItem
    set(value) {
        this.shopItem = value
    }

fun MerchantScreen.`(syncRecipeIndex)`() = this.postButtonClick()

val MerchantScreen.`(offers)`
    get() = tradeOfferButtons

//Anvil
val AnvilScreen.`(nameField)`
    get() = this.name

var AnvilScreen.`(nameFieldText)`
    get() = this.name.value
    set(value) {
        this.name.value = value
    }

val vanillaScreens: Set<Class<*>> = setOf(AbstractCommandBlockEditScreen::class.java,
                                          AbstractFurnaceScreen::class.java,
                                          AnvilScreen::class.java,
                                          BeaconScreen::class.java,
                                          BlastFurnaceScreen::class.java,
                                          BookEditScreen::class.java,
                                          BookViewScreen::class.java,
                                          BrewingStandScreen::class.java,
                                          CartographyTableScreen::class.java,
                                          CommandBlockEditScreen::class.java,
                                          CraftingScreen::class.java,
                                          CreativeModeInventoryScreen::class.java,
                                          EnchantmentScreen::class.java,
                                          ItemCombinerScreen::class.java,
                                          FurnaceScreen::class.java,
                                          DispenserScreen::class.java,
                                          ContainerScreen::class.java,
                                          GrindstoneScreen::class.java,
                                          HopperScreen::class.java,
                                          HorseInventoryScreen::class.java,
                                          InventoryScreen::class.java,
                                          JigsawBlockEditScreen::class.java,
                                          LecternScreen::class.java,
                                          LoomScreen::class.java,
                                          MerchantScreen::class.java,
                                          MinecartCommandBlockEditScreen::class.java,
                                          ShulkerBoxScreen::class.java,
                                          SignEditScreen::class.java,
                                          SmithingScreen::class.java,
                                          SmokerScreen::class.java,
                                          StonecutterScreen::class.java,
                                          StructureBlockEditScreen::class.java,
                                          CrafterScreen::class.java)
