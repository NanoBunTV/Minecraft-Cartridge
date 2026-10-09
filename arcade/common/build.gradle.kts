import net.neoforged.moddevgradle.dsl.ModDevExtension
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension

// Cartridge Arcade's loader-independent code (D195), compiled against vanilla Minecraft and Cartridge's own common
// code, as Cartridge's common is (NeoForm from 1.20.2, MCPConfig for the backports).
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
    optionalProp("deps.parchment")?.let {
        parchment {
            minecraftVersion = prop("deps.parchment_mc")
            mappingsVersion = it
        }
    }
    accessTransformers.files.setFrom(listOfNotNull(rootProject.file("common/accesstransformer.cfg"),
        rootProject.file("common/accesstransformer-$mcVersion.cfg").takeIf { it.exists() })) // what this version adds
}

dependencies {
    compileOnly(project(cartridgePath("common")))
    compileOnly("org.spongepowered:mixin:${prop("deps.mixin")}")
    compileOnly("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")
    annotationProcessor("io.github.llamalad7:mixinextras-common:${prop("deps.mixinextras")}")
    // Up to 1.20.1 its Forge build has SRG names: the legacy plugin maps it back.
    (if (legacy) "modCompileOnly" else "compileOnly")(prop("deps.yacl_common")) { isTransitive = false }
}

// Hand the sources and resources to the add-on's loader projects, as Cartridge's common does.
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

// Unit tests for the plain-Java parts, against Cartridge's common code and the real MAME (arcade/cores).
dependencies {
    testImplementation(project(cartridgePath("common")))
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testRuntimeOnly("org.slf4j:slf4j-api:2.0.9")
}

tasks.test {
    useJUnitPlatform()
    // Tests that run MAME on a real game use the tester's own romsets from CARTRIDGE_ARCADE_ROMS, else skip.
    systemProperty("cartridge.root", rootProject.projectDir.absolutePath)
}

// MAME for this platform and cartridge-host (natives/, D218), laid out as in a dev run, so Natives finds them.
tasks.processTestResources {
    from(rootProject.file("natives")) { into("natives") }
    val windows = System.getProperty("os.name").startsWith("Windows")
    val zip = rootProject.file("arcade/cores/mame/mame_libretro.${if (windows) "dll" else "so"}.zip")
    from(zipTree(zip)) { into("cores/${if (windows) "windows" else "linux"}-x86_64") }
}

// The tests cover plain Java, the same on every Minecraft version: they run on 1.21.1's node only.
tasks.matching { it.name in setOf("compileTestJava", "processTestResources", "test") }.configureEach {
    onlyIf { mcVersion == "1.21.1" }
}
