import net.fabricmc.loom.api.LoomGradleExtensionAPI

plugins {
    id("multiloader-loader")
}

val modId = prop("mod.id")

// Minecraft is obfuscated up to 1.21.11: fabric-loom-remap maps it (Mojang names + Parchment) and remaps mod
// dependencies. 26.1+ ships unobfuscated, so the plain fabric-loom plugin is used with ordinary configurations.
val obfuscated = sc.eval(mcVersion, "<26.1")
apply(plugin = if (obfuscated) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")
val loom = extensions.getByType<LoomGradleExtensionAPI>()
fun mod(configuration: String) = if (obfuscated) "mod" + configuration.replaceFirstChar(Char::uppercase) else configuration

dependencies {
    "minecraft"("com.mojang:minecraft:$mcVersion")
    if (obfuscated) {
        "mappings"(loom.layered {
            officialMojangMappings()
            optionalProp("deps.parchment")?.let {
                parchment("org.parchmentmc.data:parchment-${prop("deps.parchment_mc")}:$it@zip")
            }
        })
    }
    mod("implementation")("net.fabricmc:fabric-loader:${prop("deps.fabric_loader")}")
    mod("implementation")("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric_api")}")

    // D122: YetAnotherConfigLib, a required, separately installed dependency (not bundled).
    mod("implementation")(prop("deps.yacl_fabric"))
    // Optional: Mod Menu's Config button opens our YACL screen (fabric/ModMenuEntry); on the dev runtime to test it.
    mod("compileOnly")("com.terraformersmc:modmenu:${prop("deps.modmenu")}") { isTransitive = false }
    mod("localRuntime")("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
}

loom.apply {
    val aw = rootProject.file("common/src/main/resources/$modId.accesswidener")
    if (aw.exists()) accessWidenerPath = aw

    runs {
        named("client") {
            client()
            displayName = "Fabric Client"
            generateRunConfig = true
            runDirectory = devRunDir("client")
        }
        named("server") {
            server()
            displayName = "Fabric Server"
            generateRunConfig = true
            runDirectory = devRunDir("server")
            programArgs("--nogui")
        }
    }
}
