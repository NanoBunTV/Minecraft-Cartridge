pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
    }
}

plugins {
    // https://stonecutter.kikugie.dev — keep in sync with buildSrc/build.gradle.kts
    id("dev.kikugie.stonecutter") version "0.9.8"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// D32: MultiLoader-style common / fabric / neoforge projects, each a Stonecutter branch, plus forge for the backports.
// Minecraft versions per loader (D210, Have A Smoke's list): Fabric everywhere; NeoForge from 1.21.1; Forge for 1.19.4
// and 1.20.1 (the 1.20.1 Forge jar is also listed for NeoForge 1.20.1).
// Gradle paths are :<branch>:<version>, e.g. :fabric:1.21.1. Per-version sources are generated from <branch>/src.
val all = arrayOf("1.19.4", "1.20.1", "1.21.1", "1.21.11", "26.1.2", "26.2", "26.3")
val neo = arrayOf("1.21.1", "1.21.11", "26.1.2", "26.2", "26.3")
val legacy = arrayOf("1.19.4", "1.20.1")
stonecutter {
    create(rootProject) {
        branch("common") { versions(*all) }
        branch("fabric") { versions(*all) }
        branch("neoforge") { versions(*neo) }
        branch("forge") { versions(*legacy) }
        // D195: the Cartridge Arcade add-on, its own jars, the same layout under arcade/ (D210: every version).
        branch("arcade-common") { versions(*all) }
        branch("arcade-fabric") { versions(*all) }
        branch("arcade-neoforge") { versions(*neo) }
        branch("arcade-forge") { versions(*legacy) }
        vcsVersion = "1.21.1"
    }
}

for ((part, versions) in listOf("common" to all, "fabric" to all, "neoforge" to neo, "forge" to legacy)) {
    project(":arcade-$part").projectDir = file("arcade/$part")
    for (version in versions) {
        project(":arcade-$part:$version").projectDir = file("arcade/$part/versions/$version").apply { mkdirs() }
    }
}

rootProject.name = "Cartridge"
