// Cartridge Arcade for NeoForge (D195): its own jar, needing Cartridge's.
plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev")
}

val modId = modProp("id")
val cartridge = project(cartridgePath("neoforge"))
evaluationDependsOn(cartridge.path)

neoForge {
    version = prop("deps.neoforge_version")
    optionalProp("deps.parchment")?.let {
        parchment {
            minecraftVersion = prop("deps.parchment_mc")
            mappingsVersion = it
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = devRunDir("client")
        }
        register("server") {
            server()
            gameDirectory = devRunDir("server")
            programArgument("--nogui")
        }
        configureEach {
            ideName = "Arcade NeoForge ${name.replaceFirstChar(Char::uppercase)}"
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
        // Cartridge itself, beside the add-on in the dev runs.
        register(prop("mod.id")) {
            sourceSet(cartridge.extensions.getByType<SourceSetContainer>()["main"])
        }
    }
}


dependencies {
    implementation(prop("deps.yacl_neoforge"))
    implementation(cartridge)
}
