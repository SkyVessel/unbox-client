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
import java.net.URI

val carpet_version: String? = "26.1-beta-9+v260311"
val controlify_version: String? = "3.0.0+26.1-fabric" //"2.4.1+1.21.10-fabric"
val yacl_version: String? = "3.9.0+26.1-fabric" //"3.8.1+1.21.11-fabric"

plugins {
    alias(libs.plugins.easymod)
    antlr
    id("org.barfuin.gradle.taskinfo") version "2+"
}

repositories {
    exclusiveContent {
        forRepository {
            maven {
                name = "Modrinth"
                url = URI("https://api.modrinth.com/maven")
            }
        }
        filter {
            includeGroup("maven.modrinth")
        }
    }
}


dependencies {
    shadedApi("com.yevdo:jwildcard:1.4")

    val antlrVersion = "4.13.2"
    antlr("org.antlr:antlr4:$antlrVersion")
    shadedApi("org.antlr:antlr4-runtime:$antlrVersion") {
        version {
            strictly(antlrVersion)
        }
    }

    implementation(kotlin("reflect"))

    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:[1.7.0,2)") {
        version {
            strictly(this.strictVersion)
        }
    }
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:[1.7.0,2)")

    carpet_version?.let {
        "compileOnly"("carpet:fabric-carpet:$carpet_version")
    }

    yacl_version?.let {
        "compileOnly"("dev.isxander:yet-another-config-lib:$it") {
            exclude("maven.modrinth")
            exclude(module = "reeses-sodium-options")
            exclude(group = "org.quiltmc.parsers")
            exclude(group = "org.jetbrains.kotlinx")
            exclude(group = "org.jetbrains.kotlin")

            exclude(module = "*")
        }
    }
    controlify_version?.let {
        "compileOnly"("dev.isxander:controlify:$it") {
            exclude("maven.modrinth")
            exclude(module = "reeses-sodium-options")
            exclude(group = "org.jetbrains.kotlinx")
            exclude(group = "org.jetbrains.kotlin")
            exclude(module = "reeses-sodium-options")
            exclude(group = "net.fabricmc.fabric-api")
            exclude(group = "net.caffeinemc")
            exclude(group = "org.quiltmc.parsers")
            exclude(module = "*")
        }
    }

/*
    implementation("com.electronwill.night-config:toml:3.6.0")

    api("maven.modrinth:metal-bundles:a88KETrE")
    api("maven.modrinth:easy-shulker-boxes:bYZtnl61")
    api("maven.modrinth:ohNO6lps:uXrWPsCu")
    api("maven.modrinth:QAGBst4M:papKcBDm")
    runtimeOnly("maven.modrinth:villager-pickup:bf8Q2X39")
*/

/*
    modImplementation("curse.maven:metal-bundles-891325:7084307")
    modImplementation("curse.maven:puzzles-lib-495476:7132147")
    modImplementation("curse.maven:forge-config-api-port-fabric-547434:7081288")
*/
}

easyMod {
    this.enableShadow = true
    this.enableProGuard = true

    this.shadowJarConfig = {
        relocate("org.antlr", "org.anti_ad.embedded.org.antlr")
        relocate("com.yevdo", "org.anti_ad.embedded.com.yevdo")
        minimize()
    }
    this.jarPostProcessConfig = {
        advzipArguments = if (modVersion.release()) {
            listOf("-4", "-z", "-i", "20")
        } else {
            listOf("-3", "-z", "-i", "10")
        }
    }
}


afterEvaluate {
    sourceSets {
        getByName("main") {
            antlr {
                setSrcDirs(listOf("src/shared/antlr"))
            }
        }
    }
}

tasks.named<AntlrTask>("generateGrammarSource").configure {
    val pkg = "org.anti_ad.mc.ipnext.gen"
    outputDirectory = file("build/generated-src/antlr/main/${pkg.replace('.', '/')}")
    arguments = listOf("-visitor", "-package", pkg,
                       "-Xexact-output-dir")
}

tasks.named("compileKotlin") {
    dependsOn("generateGrammarSource")
}

tasks.named("compileJava") {
    dependsOn("generateGrammarSource")
}
tasks.named("sourcesJar") {
    dependsOn("generateGrammarSource")
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
        if (modVersion.release()) {
            this.apiToken = System.getenv("CURSEFORGE_DEPOY_TOKEN")
        }
        this.uploadArtifacts.forEach {
            it.releaseType = Constants.RELEASE_TYPE_RELEASE
            it.addEnvironment("Client")
        }
    }
}
