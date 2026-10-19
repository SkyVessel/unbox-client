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

plugins {
    alias(libs.plugins.easymod)
}


dependencies {
    shadedApi("ca.solo-studios:kt-fuzzy-jvm:0.1.0") {
        exclude("org.jetbrains.kotlin")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:[1.7.0,2)")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:[1.7.0,2)")
}

easyMod {
    this.modMenuCompileOnly = true
    this.enableShadow = true
    this.enableProGuard = false

    this.shadowJarConfig = {
        relocate("ca.solostudios", "org.anti_ad.embedded.ca.solostudios")
        relocate("com.yevdo", "org.anti_ad.embedded.com.yevdo")
        minimize()
    }
    this.jarPostProcessConfig = {
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
        if (modVersion.release()) {
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
