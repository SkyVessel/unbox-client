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

val antlrVersion: String = project.property("antlrVersion") as String

plugins {
    idea
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlinSer) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.shadow) apply false
    alias(libs.plugins.easymod)

    id("org.barfuin.gradle.taskinfo") version "2+"
    antlr
}

dependencies {
    antlr("org.antlr:antlr4:$antlrVersion") {
        version {
            strictly(antlrVersion)
        }
    }
}


configurations.all {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")

}

modVersion {
    semanticVersion = "2.3.8"
    release = {
        System.getenv("IPNEXT_RELEASE") != null
    }
}


easyMod {
    collectArtifacts = true
}

project.allprojects {
    if (this.rootProject != this) {
        group = "org.anti_ad.mc"
    }

}

/**
 * After publishing a module, drop that module from this machine's Gradle cache so the next
 * consumer build re-resolves it instead of reusing the stale copy. Both stores matter: files-2.1
 * holds the jar, and metadata-* /descriptors records which timestamped SNAPSHOT a version
 * resolved to, which changes on every republish.
 *
 * Unlike easy-mod, this evicts only the module being published rather than the whole
 * org.anti_ad.mc group, so the sibling mods keep their cached artifacts.
 *
 * Note this only helps when republishing the same version. A newly published version still hides
 * behind the 24h dynamic-version cache in dynamic-revisions.bin, which is left alone here.
 */
project.subprojects {
    val modulesCache = File(System.getProperty("user.home"), ".gradle/caches/modules-2")
    val artifact = "${rootProject.name}-$name"

    val evictFromLocalCache = tasks.register("evictFromLocalCache") {
        group = "publishing"
        description = "Removes $artifact from the local Gradle artifact cache."
        doLast {
            val descriptorDirs = modulesCache.listFiles { f -> f.isDirectory && f.name.startsWith("metadata-") }?.map { File(it, "descriptors/org.anti_ad.mc/$artifact") } ?: emptyList()
            (listOf(File(modulesCache, "files-2.1/org.anti_ad.mc/$artifact")) + descriptorDirs).forEach { dir ->
                if (dir.exists()) {
                    logger.lifecycle("Evicting $dir")
                    dir.deleteRecursively()
                }
            }
        }
    }

    tasks.matching { it.name == "publishAllPublicationsToIpnOfficialRepoRepository" }.configureEach {
        finalizedBy(evictFromLocalCache)
    }
}


