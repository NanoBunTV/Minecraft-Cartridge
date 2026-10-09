plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "FabricMC" }
    maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
    maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
}

// Loader plugins are put on the buildSrc classpath (instead of versioned plugins {} blocks) so every
// branch shares one copy of them. Versions: https://fabricmc.net/develop, https://projects.neoforged.net
dependencies {
    fun plugin(id: String, version: String) = "$id:$id.gradle.plugin:$version"
    implementation(plugin("dev.kikugie.stonecutter", "0.9.8"))
    // fabric-loom-remap for obfuscated Minecraft (up to 1.21.11), fabric-loom for unobfuscated (26.1+).
    implementation(plugin("net.fabricmc.fabric-loom-remap", "1.18.2"))
    implementation(plugin("net.fabricmc.fabric-loom", "1.18.2"))
    implementation(plugin("net.neoforged.moddev", "2.0.148"))
}
