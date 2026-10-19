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

import masecla.modrinth4j.model.version.ProjectVersion.*
import net.darkhax.curseforgegradle.Constants
import net.darkhax.curseforgegradle.TaskPublishCurseForge

repositories {
    maven("https://repo.nyon.dev/releases")
}

plugins {
    alias(libs.plugins.easymod)
    id("org.barfuin.gradle.taskinfo")
}

@Suppress("UNCHECKED_CAST")
val inclusions: List<String> by lazy { extra["libipn.kotlin.deps"] as List<String> }

dependencies {
    shadedApi("ca.solo-studios:kt-fuzzy-jvm:0.1.0") {
        exclude("org.jetbrains.kotlin")
    }

    @Suppress("AvoidDuplicateDependencies")
    inclusions.forEach { name ->

        "jarJar"(name)
        implementation(name) {
            exclude("org.jetbrains.kotlin")
            exclude("org.jetbrains.kotlinx")
            exclude("org.jetbrains")
        }
    }
}

easyMod {
    enableShadow = true
    enableProGuard = false

    shadowJarConfig = {
        relocate("ca.solostudios", "org.anti_ad.embedded.ca.solostudios")
        relocate("com.yevdo", "org.anti_ad.embedded.com.yevdo")
        exclude("LICENSE", "/META-INF/LICENSE", "/**/*LICENSE*")
        minimize()

    }
    jarPostProcessConfig = {
        entryFilter = {
            !(it.startsWith("org/anti_ad/mc/alias") && it.endsWith("AliasKt.class"))
        }
        advzipArguments = if (modVersion.release()) {
            listOf("-4", "-z", "-i", "100")
        } else {
            listOf("-3", "-z", "-i", "10")
        }
    }
}


publishing {
    repositories {
        maven {
            val releasesRepoUrl = "https://maven.ipn-mod.org/releases"
            val snapshotsRepoUrl = "https://maven.ipn-mod.org/snapshots"
            name = "ipnOfficialRepo"
            credentials(PasswordCredentials::class)
            authentication {
                create<BasicAuthentication>("basic")
            }
            url = uri(if (modVersion.release()) releasesRepoUrl else snapshotsRepoUrl)
        }
    }
}


afterEvaluate {
    modrinth {
        failSilently.set(true)
        if (System.getenv("IPNEXT_RELEASE") != null) {
            token.set(System.getenv("MODRINTH_TOKEN"))
        }
        versionType.set(VersionType.RELEASE.name)
    }
    project.tasks.withType(TaskPublishCurseForge::class.java) {
        if (System.getenv("IPNEXT_RELEASE") != null) {
            this.apiToken = System.getenv("CURSEFORGE_DEPOY_TOKEN")
        }
        this.uploadArtifacts.forEach {
            it.releaseType = Constants.RELEASE_TYPE_RELEASE
            it.addEnvironment("Client")
        }
    }
}
