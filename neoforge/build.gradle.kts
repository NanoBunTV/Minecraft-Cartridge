plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev")
}

val modId = prop("mod.id")

neoForge {
    version = prop("deps.neoforge_version")
    // Parchment's parameter names, where a release exists for this version (none for 26.1+ yet).
    optionalProp("deps.parchment")?.let {
        parchment {
            minecraftVersion = prop("deps.parchment_mc")
            mappingsVersion = it
        }
    }
    val at = rootProject.file("common/src/main/resources/META-INF/accesstransformer.cfg")
    accessTransformers.files.setFrom(listOfNotNull(at.takeIf { it.exists() })) // the source file, never the generated copy

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
            ideName = "NeoForge ${name.replaceFirstChar(Char::uppercase)}"
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    // D122: YetAnotherConfigLib, a required dependency.
    implementation(prop("deps.yacl_neoforge"))
}
