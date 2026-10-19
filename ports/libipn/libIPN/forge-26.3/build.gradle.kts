/*
 * Inventory Profiles Next
 *
 *   Copyright (c) 2025 Plamen K. Kosseff <p.kosseff@gmail.com>
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




import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

import masecla.modrinth4j.model.version.ProjectVersion.*
import net.darkhax.curseforgegradle.Constants
import net.darkhax.curseforgegradle.TaskPublishCurseForge
import net.minecraftforge.jarjar.gradle.JarJar

import net.minecraftforge.jarjar.gradle.JarJarDependencyMethods
import org.gradle.kotlin.dsl.invoke

buildscript {
    dependencies {
        classpath("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    }
}

repositories {
    minecraft.mavenizer(this)
}

plugins {
    alias(libs.plugins.easymod)
}



@Suppress("UNCHECKED_CAST")
val inclusions: List<String> by lazy { extra["libipn.kotlin.deps"] as List<String> }

jarJar.register()

dependencies {
    shadedApi("ca.solo-studios:kt-fuzzy-jvm:0.1.0") {
        exclude("org.jetbrains.kotlin")
        exclude("org.jetbrains")
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



data class JJId(val group: String, val artifact: String)
data class JJVer(val range: String, val artifactVersion: String)
data class JJDependency(val identifier: JJId?, val version: JJVer?, val path: String, val isObfuscated: Boolean)
data class JJRoot(val jars: List<JJDependency>)


val jjManifest by lazy {
    getJarJarManifest().map {
        it.path
    }
}

fun getJarJarManifest(): List<JJDependency> {

    val metadataFile = layout.buildDirectory.file("minecraftforge/jarJar/jarJarMetadata/metadata.json").get().asFile
    if (!metadataFile.exists()) return emptyList()

    val root = Json.parseToJsonElement(metadataFile.readText()).jsonObject.get("jars")?.jsonArray

    return root?.map { element ->
        val obj = element.jsonObject

        val identifierObj = obj["identifier"]?.jsonObject
        val versionObj = obj["version"]?.jsonObject

        JJDependency(identifier = identifierObj?.let {
            JJId(group = it["group"]?.jsonPrimitive?.contentOrNull ?: "",
                 artifact = it["artifact"]?.jsonPrimitive?.contentOrNull ?: "")
        },
                     version = versionObj?.let {
                         JJVer(range = it["range"]?.jsonPrimitive?.contentOrNull ?: "",
                               artifactVersion = it["artifactVersion"]?.jsonPrimitive?.contentOrNull ?: "")
                     },
                     path = obj["path"]?.jsonPrimitive?.contentOrNull ?: "",
                     isObfuscated = obj["isObfuscated"]?.jsonPrimitive?.booleanOrNull ?: false)
    } ?: listOf()
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
        entryFilter = { it ->
            jjManifest.forEach {
                logger.debug("jjManifest entry: $it")
            }
            logger.debug("Checking entry: $it")
            when {
                it.startsWith("META-INF/jarjar/") && it.endsWith("metadata.json") -> true
                it.startsWith("org/anti_ad/mc/alias") && it.endsWith("AliasKt.class") -> false
                it.startsWith("META-INF/jarjar/") && !jjManifest.contains(it) -> false
                else -> true
            }
                //!(it.startsWith("org/anti_ad/mc/alias") && it.endsWith("AliasKt.class"))
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


