// Forge, 1.19.4 and 1.20.1 (D210). The 1.20.1 jar is also listed for NeoForge 1.20.1 (NeoForge 47.1 loads Forge
// mods). ModDevGradle's legacy plugin handles the Forge-era toolchain: SRG names at runtime, so the jar is
// reobfuscated and mixins need a refmap.
plugins {
    id("multiloader-loader")
    id("net.neoforged.moddev.legacyforge")
}

val modId = prop("mod.id")

legacyForge {
    version = prop("deps.forge_version")
    optionalProp("deps.parchment")?.let {
        parchment {
            minecraftVersion = prop("deps.parchment_mc")
            mappingsVersion = it
        }
    }
    // Compile-time, as for common (see the file); Forge itself opens these up at runtime.
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
            ideName = "Forge ${name.replaceFirstChar(Char::uppercase)}"
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

// The mixin annotation processor writes the refmap (named-to-SRG names) that Forge needs in production.
mixin {
    add(sourceSets.main.get(), "$modId.refmap.json")
    config("$modId.mixins.json")
}

// No Controlify for these Forge versions: its compat mixin (common's mixin/compat) stays out.
tasks.compileJava { exclude("**/mixin/compat/**") }
tasks.processResources { exclude("$modId.controlify.mixins.json") }

dependencies {
    annotationProcessor("org.spongepowered:mixin:${prop("deps.mixin")}:processor")
    // D122: YetAnotherConfigLib, a required dependency (remapped to SRG names with the game).
    "modImplementation"(prop("deps.yacl_forge"))
}

tasks.jar {
    manifest.attributes("MixinConfigs" to "$modId.mixins.json")
}
