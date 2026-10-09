import net.fabricmc.loom.api.LoomGradleExtensionAPI

// Cartridge Arcade for Fabric (D195): its own jar, needing Cartridge's.
plugins {
    id("multiloader-loader")
}

// As Cartridge's fabric: fabric-loom-remap up to 1.21.11, the plain fabric-loom from 26.1 (unobfuscated).
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
    mod("implementation")(prop("deps.yacl_fabric"))
    // Cartridge itself: compiled against, and beside the add-on in the dev runs.
    val cartridge = if (obfuscated) project(path = cartridgePath("fabric"), configuration = "namedElements") else project(cartridgePath("fabric"))
    "compileOnly"(cartridge)
    "localRuntime"(cartridge)
    mod("localRuntime")("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
}

loom.apply {
    runs {
        named("client") {
            client()
            displayName = "Arcade Fabric Client"
            generateRunConfig = true
            runDirectory = devRunDir("client")
        }
        named("server") {
            server()
            displayName = "Arcade Fabric Server"
            generateRunConfig = true
            runDirectory = devRunDir("server")
            programArgs("--nogui")
        }
    }
}
