// Cartridge Arcade for Forge, 1.19.4 and 1.20.1 (D195, D210): its own jar, needing Cartridge's.
plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev.legacyforge")
}

val modId = modProp("id")
val cartridge = project(cartridgePath("forge"))
evaluationDependsOn(cartridge.path)

legacyForge {
    version = prop("deps.forge_version")
    optionalProp("deps.parchment")?.let {
        parchment {
            minecraftVersion = prop("deps.parchment_mc")
            mappingsVersion = it
        }
    }
    accessTransformers.files.setFrom(rootProject.file("common/accesstransformer.cfg"))

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
            ideName = "Arcade Forge ${name.replaceFirstChar(Char::uppercase)}"
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
    "modImplementation"(prop("deps.yacl_forge"))
    implementation(cartridge)
}
