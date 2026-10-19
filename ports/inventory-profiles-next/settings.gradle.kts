/*
 * Inventory Profiles Next
 *
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






rootProject.name = "InventoryProfilesNext"


include("platforms:neoforge-26.1.2")






pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral().content {
            excludeGroup("net.jodah")
        }
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }
        maven { url = uri("https://maven.minecraftforge.net") }
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }

        maven {
            name = "Modrinth"
            url = uri("https://api.modrinth.com/maven")
        }

        maven {
            name = "libIPN-Snapshots"
            mavenContent {
                snapshotsOnly()
            }
            url = uri("https://maven.ipn-mod.org/snapshots")
        }
        maven {
            name = "libIPN-Releases"
            mavenContent {
                releasesOnly()
            }
            url = uri("https://maven.ipn-mod.org/releases")
        }

    }
}

dependencyResolutionManagement {
    repositories {

        gradlePluginPortal()
        google()
        maven { url = uri("https://maven.minecraftforge.net") }
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases/") }
        mavenCentral()
        maven { url = uri("https://plugins.gradle.org/m2/") }
        maven { url = uri("https://repo.spongepowered.org/repository/maven-public/") }

        maven {
            name = "Forge"
            url = uri("https://maven.minecraftforge.net")
        }

        maven {
            url = uri("https://maven.ipn-mod.org/snapshots")
        }
        maven {
            url = uri("https://maven.ipn-mod.org/releases")
        }

        maven {
            url = uri("https://maven.isxander.dev/releases")
            name = "Xander Maven"
        }

        maven {
            name = "Modrinth"
            url = uri("https://api.modrinth.com/maven")
        }


        exclusiveContent {
            forRepository {
                maven {
                    url = uri("https://cursemaven.com")
                }
            }
            filter {
                includeGroup("curse.maven")
            }
        }


        exclusiveContent {
            forRepository {
                maven { url = mavenCentral().url }
            }
            filter {
                includeVersion("org.lwjgl", "lwjgl-freetype", "3.3.3")
            }
        }

    }
}



plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1+"
}
