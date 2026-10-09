import net.neoforged.moddevgradle.dsl.ModDevExtension
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension

// Loader-independent code, compiled against vanilla Minecraft (Mojang mappings + Parchment): NeoForm from 1.20.2,
// MCPConfig through ModDevGradle's legacy plugin for the Forge-era backports (1.19.4, 1.20.1).
plugins {
    id("multiloader-common")
}

val legacy = sc.eval(mcVersion, "<1.20.2")
apply(plugin = if (legacy) "net.neoforged.moddev.legacyforge" else "net.neoforged.moddev")
if (legacy) {
    extensions.configure<LegacyForgeExtension> { mcpVersion = prop("deps.mcp") }
} else {
    extensions.configure<NeoForgeExtension> { neoFormVersion = prop("deps.neoform") }
}
extensions.configure<ModDevExtension> {
    // Parchment's parameter names, where a release exists for this version (none for 26.1+ yet).
    optionalProp("deps.parchment")?.let {
        parchment {
            minecraftVersion = prop("deps.parchment_mc")
            mappingsVersion = it
        }
    }
    // Compile-time only: what every loader already opens up at runtime (see the file). Kept out of resources so it
    // isn't shipped.
    accessTransformers.files.setFrom(listOfNotNull(rootProject.file("common/accesstransformer.cfg"),
        rootProject.file("common/accesstransformer-$mcVersion.cfg").takeIf { it.exists() })) // what this version adds
}

dependencies {
    compileOnly("org.spongepowered:mixin:${prop("deps.mixin")}")
    compileOnly("org.ow2.asm:asm-tree:9.7.1") // mixin plugins (mixin/compat); every loader ships ASM
    // Fabric, NeoForge and Forge all ship MixinExtras, so common code may use it.
    compileOnly("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")
    annotationProcessor("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")
    // D122: YetAnotherConfigLib for settings and the config screen. Its NeoForge / Forge build has Mojang names, as
    // common does; each loader ships its own build.
    // Up to 1.20.1 its Forge build has SRG names: the legacy plugin maps it back.
    (if (legacy) "modCompileOnly" else "compileOnly")(prop("deps.yacl_common")) { isTransitive = false }
}

// Before 1.20.5: look-alikes of the newer Minecraft API the mod is written against (Porting.md), plain Java that
// Stonecutter doesn't touch.
if (sc.eval(mcVersion, "<1.20.5")) {
    sourceSets.main { java.srcDir(rootProject.file("common/src/legacy/java")) }
}
if (sc.eval(mcVersion, "<1.20")) {
    sourceSets.main { java.srcDir(rootProject.file("common/src/legacy119/java")) } // 1.19.4: GuiGraphics
}

// Hand the common sources and resources to the loader projects. For an inactive Stonecutter version these are
// the generated copies, so the loaders also wait for stonecutterGenerate.
val commonJava = configurations.consumable("commonJava")
val commonResources = configurations.consumable("commonResources")

afterEvaluate {
    sourceSets.main.get().java.sourceDirectories.files.forEach {
        artifacts.add(commonJava.name, it) { builtBy("stonecutterGenerate") }
    }
    sourceSets.main.get().resources.sourceDirectories.files.forEach {
        artifacts.add(commonResources.name, it) { builtBy("stonecutterGenerate") }
    }
}

// Unit tests for the plain-Java parts (emu/). HostProcessTest drives the real sidecar, the committed natives/ (D218).
dependencies {
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Minecraft provides slf4j in game; tests need it on their own classpath.
    testRuntimeOnly("org.slf4j:slf4j-api:2.0.9")
}

tasks.test {
    useJUnitPlatform()
    systemProperty("cartridge.root", rootProject.projectDir.absolutePath)
}

tasks.processTestResources {
    // natives/<os>-<arch>/, laid out as in a dev run's resources, for NativesTest.
    from(rootProject.file("natives")) { into("natives") }
    // The bundled cores (D114, D137), laid out as in the loader jars.
    for (core in listOf("fceumm", "snes9x", "genesis_plus_gx", "sameboy", "mgba", "mupen64plus_next")) {
        from(zipTree(rootProject.file("cores/$core/${core}_libretro.dll.zip"))) { into("cores/windows-x86_64") }
        from(zipTree(rootProject.file("cores/$core/${core}_libretro.so.zip"))) { into("cores/linux-x86_64") }
    }
}

// The tests cover plain Java (emu/, rom/...), the same on every Minecraft version: they run on 1.21.1's node only.
tasks.matching { it.name in setOf("compileTestJava", "processTestResources", "test") }.configureEach {
    onlyIf { mcVersion == "1.21.1" }
}
