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

val controlify_version: String? = "2.4.1+1.21.8-neoforge"
val yacl_version: String? = "3.7.1+1.21.6-neoforge"

val antlrVersion: String by project


plugins {
    antlr
    alias(libs.plugins.easymod)
    id("org.barfuin.gradle.taskinfo") version "2+"
}


configurations.all {
    resolutionStrategy {
        force("org.ow2.asm:asm-analysis:9.7")
        force("org.ow2.asm:asm-util:9.7")
        force("org.antlr:antlr4-runtime:$antlrVersion")
        //            force("org.anti_ad.mc:libIPN-$libIPN_version")
    }
}

configurations.runtimeClasspath {
    exclude("org.jetbrains.kotlin")
    exclude("org.jetbrains")
}

dependencies {
    shadedApi("com.yevdo:jwildcard:1.4")
    shadedApi("org.antlr:antlr4-runtime:$antlrVersion") {
        version {
            strictly(antlrVersion)
        }
    }
    implementation("org.antlr:antlr4-runtime:$antlrVersion") {
        version {
            strictly(antlrVersion)
        }
    }

    antlr("org.antlr:antlr4:$antlrVersion") {
        version {
            strictly(antlrVersion)
        }
    }

    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-json:[1.7.0,2)")
    compileOnly("org.jetbrains.kotlinx:kotlinx-serialization-core:[1.7.0,2)")

    compileOnly("curse.maven:easy-villagers-400514:7312615")
    compileOnly("curse.maven:workshop-for-handsome-adventurer-875843:5752681")

    yacl_version?.let {
        compileOnly("dev.isxander:yet-another-config-lib:$it") {
            isTransitive = false
            exclude("maven.modrinth")
            exclude(module = "reeses-sodium-options")
        }
    }
    controlify_version?.let {
        compileOnly("dev.isxander:controlify:$it") {
            isTransitive = false
            exclude("maven.modrinth")
            exclude(module = "reeses-sodium-options")
            exclude(group = "net.fabricmc.fabric-api")
        }
    }

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

val antlrTask = tasks.named<AntlrTask>("generateGrammarSource")
antlrTask.configure {
    val pkg = "org.anti_ad.mc.ipnext.gen"
    outputDirectory = project.layout.buildDirectory.dir("generated/sources/antlr/main/${pkg.replace('.', '/')}").get().asFile
    arguments = listOf("-visitor", "-package", pkg,
                       "-Xexact-output-dir")
}

plugins.withId("idea") {
    configure<org.gradle.plugins.ide.idea.model.IdeaModel> {
        afterEvaluate {
            module.sourceDirs.add(file("src/shared/antlr"))
            module.sourceDirs.add(project.layout.buildDirectory.dir("generated/sources/antlr/main").get().asFile)
        }
    }
}

/*
java {
    sourceSets.getByName("main") {
        java.srcDir(project.layout.buildDirectory.dir("generated/sources/antlr/main"))
    }
}
*/

tasks.named("compileKotlin") {
    dependsOn(antlrTask)
}

tasks.named("compileJava") {
    dependsOn(antlrTask)
}
tasks.named("sourcesJar") {
    dependsOn(antlrTask)
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


    sourceSets {
        getByName("main") {
            antlr {
                setSrcDirs(listOf("src/shared/antlr"))
            }
        }
    }

    project.gradle.taskGraph.whenReady {

        allTasks.forEach {
            if (it is net.neoforged.gradle.userdev.runtime.tasks.ClasspathSerializer) {
                it.doFirst {
                    it.targetFile.get().asFile.delete()
                }
                it.doLast {
                    logger.info("${it.path} -> ${it.javaClass.name}")
                    val file = it.targetFile.get().asFile
                    logger.info("${it.targetFile.get()}")
                    file.readLines().forEach {
                        logger.lifecycle("\t\t$it")
                    }
                }
            }
        }

    }

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

repositories { maven { url = uri("https://maven.isxander.dev/releases") } }

// Native 26.1 companion library is built from the adjacent source port.
configurations.configureEach { exclude(group = "org.anti_ad.mc", module = "libIPN-neoforge-26.1") }
dependencies { compileOnly(files("../../../libipn/libIPN/neoforge-26.1.2/build/libs/libIPN-neoforge-26.1.2-6.9.0-SNAPSHOT-shaded.jar")) }
