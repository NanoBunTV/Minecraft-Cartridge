// Shared by the common, fabric and neoforge nodes (Jared's MultiLoader-Template layout, D32).
plugins {
    `java-library`
}

val modId = modProp("id")
val javaVersion = prop("java").toInt()

// Every node is named after its Minecraft version (":common:1.21.1", ":fabric:1.21.1"), so the group must differ
// per branch, or Gradle sees the three projects as one module and resolves the wrong one.
group = "${modProp("group")}.${sc.branch.id}"
version = "${modProp("version")}+$mcVersion"
base.archivesName = "$modId-$part"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(javaVersion)
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = javaVersion
    options.compilerArgs.addAll(listOf("-Xmaxerrs", "10000"))
}

repositories {
    mavenCentral()
    exclusiveContent {
        forRepository { maven("https://repo.spongepowered.org/repository/maven-public") { name = "Sponge" } }
        filter { includeGroupAndSubgroups("org.spongepowered") }
    }
    exclusiveContent {
        forRepositories(
            maven("https://maven.parchmentmc.org/") { name = "ParchmentMC" },
            maven("https://maven.neoforged.net/releases") { name = "NeoForged" },
        )
        filter { includeGroup("org.parchmentmc.data") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.terraformersmc.com/releases/") { name = "TerraformersMC" } }
        filter { includeGroup("com.terraformersmc") }
    }
    exclusiveContent {
        forRepository { maven("https://maven.isxander.dev/releases") { name = "isXander" } }
        filter { includeGroupAndSubgroups("dev.isxander") }
    }
    exclusiveContent {
        // YACL's JSON5 parser (D122)
        forRepository { maven("https://maven.quiltmc.org/repository/release/") { name = "QuiltMC" } }
        filter { includeGroup("org.quiltmc.parsers") }
    }
}

// YACL 3.1.1 (1.19.4) declares TwelveMonkeys snapshots that no repository has; its jar carries what it needs.
configurations.configureEach {
    exclude(group = "com.twelvemonkeys.common")
    exclude(group = "com.twelvemonkeys.imageio")
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE.md")) { rename { "LICENSE_$modId.md" } }
    manifest.attributes(
        "Specification-Title" to modProp("name"),
        "Specification-Vendor" to modProp("authors"),
        "Implementation-Title" to project.path,
        "Implementation-Version" to project.version,
        "Built-On-Minecraft" to mcVersion,
    )
}

// Renames between Minecraft versions that need no other change (Porting.md, Renames). src/ holds 1.21.1's names;
// Stonecutter rewrites them for other nodes, and back again when switching the active version. A rename also runs
// backwards on the versions where it is off, so the new name must never appear in src/ for any other reason.
fun rename(since: String, from: String, to: String) =
    sc.replacements.string(sc.eval(mcVersion, ">=$since")) { replace(from, to) }

// Backports: names that were different before (the first version with today's name).
fun renameBefore(since: String, from: String, to: String) =
    sc.replacements.string(sc.eval(mcVersion, "<$since")) { replace(from, to) }

// A name that changed more than once: each step is (first version with that name, the name). Stonecutter needs one
// replacement per source name, so this registers only the step for this version.
fun renames(from: String, vararg steps: Pair<String, String>) {
    val to = steps.lastOrNull { sc.eval(mcVersion, ">=${it.first}") }?.second ?: return
    sc.replacements.string(true) { replace(from, to) }
}

rename("1.21.11", "ResourceLocation", "Identifier")
// GameProfile became a record (authlib 7, 1.21.9).
rename("1.21.9", "getGameProfile().getName()", "getGameProfile().name()")
rename("1.21.9", "getProfile().getName()", "getProfile().name()")
// A resource key's id.
rename("1.21.11", "dimension().location()", "dimension().identifier()")
rename("1.21.11", "registry.key().location()", "registry.key().identifier()")
// The window's GLFW handle.
rename("1.21.9", "getWindow().getWindow()", "getWindow().handle()")
rename("1.21.9", "window.getWindow()", "window.handle()")
// Util moved into the util package.
rename("1.21.11", "import net.minecraft.Util;", "import net.minecraft.util.Util;")
// Block entity renderer contexts became records.
rename("1.21.9", "context.getFont()", "context.font()")
// 26.1: the GUI extracts render state; its context class was renamed (the screens' own overrides are in
// client/CartridgeContainerScreen and CartridgeScreen, text drawing in client/GuiDraw).
sc.replacements.regex(sc.eval(mcVersion, ">=26.1")) {
    replace("""\bGuiGraphics\b""", "GuiGraphicsExtractor", """\bGuiGraphicsExtractor\b""", "GuiGraphics")
}
// 26.1: light coordinates' helpers left LightTexture; the click type became the container input; two moves.
sc.replacements.regex(sc.eval(mcVersion, ">=26.1")) {
    replace("""\bLightTexture\.""", "LightCoordsUtil.", """\bLightCoordsUtil\.""", "LightTexture.")
}
rename("26.1", "import net.minecraft.client.renderer.LightTexture;", "import net.minecraft.util.LightCoordsUtil;")
sc.replacements.regex(sc.eval(mcVersion, ">=26.1")) {
    replace("""\bClickType\b""", "ContainerInput", """\bContainerInput\b""", "ClickType")
}
// 26.2: the open screen belongs to the GUI.
sc.replacements.regex(sc.eval(mcVersion, ">=26.2")) {
    replace("""\b(mc|minecraft|Minecraft\.getInstance\(\))\.screen\b(?!\()""", "$1.gui.screen()",
        """\b(mc|minecraft|Minecraft\.getInstance\(\))\.gui\.screen\(\)""", "$1.screen")
}
sc.replacements.regex(sc.eval(mcVersion, ">=26.2")) {
    replace("""\b(mc|minecraft|Minecraft\.getInstance\(\))\.setScreen\(""", "$1.gui.setScreen(",
        """\b(mc|minecraft|Minecraft\.getInstance\(\))\.gui\.setScreen\(""", "$1.setScreen(")
}
// 26.3: the GPU abstraction moved out of blaze3d into renderpearl.
rename("26.3", "import com.mojang.blaze3d.systems.GpuDevice;", "import com.mojang.renderpearl.api.device.GpuDevice;")
rename("26.3", "import com.mojang.blaze3d.textures.", "import com.mojang.renderpearl.api.textures.")
rename("26.3", "import com.mojang.blaze3d.GpuFormat;", "import com.mojang.renderpearl.api.GpuFormat;")
// 26.3: PoseStack rotates by a quaternion itself; the push reactions were renamed; a key event's second code.
rename("26.3", ".mulPose(Axis.", ".rotate(Axis.")
rename("26.3", "PushReaction.BLOCK", "PushReaction.IMMOVEABLE")
rename("26.3", "event.scancode()", "event.keycode()")
// Fabric API for 26.1 renamed its classes after Mojang's names.
rename("26.1", "itemgroup.v1.FabricItemGroup", "creativetab.v1.FabricCreativeModeTab")
rename("26.1", "FabricItemGroup.builder", "FabricCreativeModeTab.builder")
rename("26.1", "PayloadTypeRegistry.playC2S()", "PayloadTypeRegistry.serverboundPlay()")
rename("26.1", "PayloadTypeRegistry.playS2C()", "PayloadTypeRegistry.clientboundPlay()")
// NeoForge for 26.1: block tints are tint sources; "after entities" is after the opaque features.
rename("26.1", "RegisterColorHandlersEvent.Block ", "RegisterColorHandlersEvent.BlockTintSources ")
rename("26.1", "RegisterColorHandlersEvent.Block>", "RegisterColorHandlersEvent.BlockTintSources>")
rename("26.1", "RenderLevelStageEvent.AfterEntities", "RenderLevelStageEvent.AfterOpaqueFeatures")
rename("26.1", "net.minecraft.client.renderer.state.CameraRenderState", "net.minecraft.client.renderer.state.level.CameraRenderState")
rename("26.1", "import net.minecraft.world.level.BlockAndTintGetter;", "import net.minecraft.client.renderer.block.BlockAndTintGetter;")
// Before 1.20.5: no stream codecs or payload types. common/src/legacy holds look-alikes of the part the mod uses
// (Porting.md, "Networking before 1.20.5"); the vanilla types' own stream codecs are there too.
for (name in listOf("network.codec.StreamCodec", "network.codec.ByteBufCodecs",
        "network.protocol.common.custom.CustomPacketPayload")) {
    renameBefore("1.20.5", "import net.minecraft.$name;", "import com.nanobuntv.cartridge.net.codec.${name.substringAfterLast('.')};")
}
for ((from, to) in listOf("BlockPos.STREAM_CODEC" to "BLOCK_POS", "UUIDUtil.STREAM_CODEC" to "UUID",
        "GlobalPos.STREAM_CODEC" to "GLOBAL_POS", "ComponentSerialization.STREAM_CODEC" to "COMPONENT")) {
    renameBefore("1.20.5", from, "com.nanobuntv.cartridge.net.codec.ByteBufCodecs.$to")
}
// (One way: Component's own import is everywhere in src/.)
sc.replacements.regex(sc.eval(mcVersion, "<1.20.5")) {
    replace("""import net\.minecraft\.network\.chat\.ComponentSerialization;""", "import net.minecraft.network.chat.Component;",
        """(?!)ComponentSerialization""", "ComponentSerialization")
}
// Payloads read plain FriendlyByteBufs (the reverse never matches: FriendlyByteBuf is used in src/ as it is).
sc.replacements.regex(sc.eval(mcVersion, "<1.20.5")) {
    replace("""\bRegistryFriendlyByteBuf\b""", "FriendlyByteBuf", """(?!)RegistryFriendlyByteBuf""", "RegistryFriendlyByteBuf")
}
// 1.19.4: no GuiGraphics yet; common/src/legacy119 has a look-alike over PoseStack.
// (Only the package, one way, so the class name stays free for 26.1's GuiGraphicsExtractor rename.)
sc.replacements.regex(sc.eval(mcVersion, "<1.20")) {
    replace("""import net\.minecraft\.client\.gui\.(?=GuiGraphics;)""", "import com.nanobuntv.cartridge.legacy.",
        """(?!)import com""", "import com")
}
// 1.19.4: map colours were material colours (the block properties' Material is in block/CartridgeBlocks).
sc.replacements.regex(sc.eval(mcVersion, "<1.20")) {
    replace("""\bMapColor\b""", "MaterialColor", """(?!)MaterialColor""", "MapColor")
}
// 1.19.4: an entity's level was a field (every .level() in src/ is an entity's); YACL 3.1 named the formatter
// valueFormatter.
sc.replacements.regex(sc.eval(mcVersion, "<1.20")) {
    replace("""\.level\(\)""", ".level", """(?!)\.level""", ".level()")
}
renameBefore("1.20", ".formatValue(", ".valueFormatter(")
// Loot parameters were a loot context's builder.
sc.replacements.regex(sc.eval(mcVersion, "<1.20")) { // one way: LootContextParams is in src/
    replace("""\bLootParams\b""", "LootContext", """(?!)LootContext""", "LootParams")
}
// Java 17 on the backports: Java 21's Math.clamp is in common/src/legacy.
renameBefore("1.20.5", "Math.clamp(", "com.nanobuntv.cartridge.legacy.JavaMath.clamp(")
renameBefore("1.20.5", "isSameItemSameComponents", "isSameItemSameTags")
renameBefore("1.20.5", "StringUtil.filterText(", "net.minecraft.SharedConstants.filterText(")
// (A regex, one way: as a plain rename it would hold on to "ResourceLocation" where 1.21.11 renames that.)
sc.replacements.regex(sc.eval(mcVersion, "<1.21")) {
    replace("""ResourceLocation\.fromNamespaceAndPath\(""", "new ResourceLocation(", """(?!)new ResourceLocation\(""", "x")
}
// Before 1.21 a vertex is built with vertex/color/uv/uv2/overlayCoords/normal and finished with endVertex. Every
// vertex in src/ is one statement, so endVertex goes before its semicolon, and the setters are renamed only in such
// a statement (others have setColor too). The reverses never match: src/ uses .color( and .normal( for other things.
for ((from, to) in listOf("setColor" to "color", "setUv" to "uv", "setLight" to "uv2", "setOverlay" to "overlayCoords")) {
    sc.replacements.regex(sc.eval(mcVersion, "<1.21")) {
        replace("""(?<=\.addVertex\([^;]{0,400})\.$from\(""", ".$to(", """(?!)\.$to\(""", ".$from(")
    }
}
sc.replacements.regex(sc.eval(mcVersion, "<1.21")) {
    replace("""(?<=\.addVertex\([^;]{0,400})\.setNormal\((pose|last), """, ".normal($1.normal(), ", """(?!)\.normal\(""", ".setNormal(")
}
sc.replacements.regex(sc.eval(mcVersion, "<1.21")) {
    replace("""(?<=\.addVertex\([^;]{0,400})\.setNormal\(""", ".normal(", """(?!)\.normal\(""", ".setNormal(")
}
renameBefore("1.21", ".addVertex(", ".vertex(")
// Zero-width, before the statement's semicolon, so it doesn't overlap the renames inside the statement.
sc.replacements.regex(sc.eval(mcVersion, "<1.21")) {
    replace("""(?<=\.addVertex\([^;]{0,400})(?=;)""", ".endVertex()", """(?!)\.endVertex\(\)""", ".endVertex()")
}
// Level.isClientSide became a method; every read of the field becomes a call.
sc.replacements.regex(sc.eval(mcVersion, ">=1.21.9")) {
    replace("""\.isClientSide\b(?!\()""", ".isClientSide()", """\.isClientSide\(\)""", ".isClientSide")
}

tasks.processResources {
    val props = mapOf(
        "mod_id" to modId,
        "mod_name" to modProp("name"),
        "mod_version" to project.version.toString(),
        "mod_authors" to modProp("authors"),
        "mod_license" to modProp("license"),
        "mod_description" to modProp("description"),
        "mod_slug" to modProp("slug"),
        "mod_sources" to modProp("sources"),
        "mod_icon" to "assets/$modId/icon.png",
        "neoforge_icon_key" to (if (sc.eval(mcVersion, ">=26.3")) "iconFile" else "logoFile"), // logoFile deprecated in 26.3
        "cartridge_version" to prop("mod.version"),
        "java_version" to javaVersion.toString(),
        // Mixin's compatibility level: JAVA_17 on the Java 17 backports, JAVA_21 (the highest Mixin 0.8 knows) above.
        "mixin_java" to if (javaVersion < 21) "17" else "21",
        // Forge needs the refmap the mixin annotation processor writes (forge/build.gradle.kts); the others don't.
        "mixin_refmap" to if (part == "forge") "\"refmap\": \"$modId.refmap.json\"," else "",
        "minecraft_version" to mcVersion,
        "pack_meta" to prop("mc.pack_meta"),
        "mc_compat_fabric" to prop("mc.compat_fabric"),
        "mc_compat_neoforge" to (optionalProp("mc.compat_neoforge") ?: ""),
        "mc_compat_forge" to (optionalProp("mc.compat_forge") ?: ""),
        "fabric_loader_version" to prop("deps.fabric_loader"),
        "neoforge_range" to (optionalProp("deps.neoforge_range") ?: ""), // none where there is no NeoForge node
        "forge_range" to (optionalProp("deps.forge_range") ?: ""),
        "forge_loader_range" to (optionalProp("deps.forge_loader_range") ?: ""),
        "yacl_id" to prop("deps.yacl_id"),
        "yacl_range" to prop("deps.yacl_range"),
        // Client mixins that exist on this version only (the others' class files are commented out by Stonecutter).
        "mixins_client" to prop("mixins.client").split(" ").joinToString(", ") { "\"client.$it\"" },
    )
    inputs.properties(props)
    filesMatching(listOf("pack.mcmeta", "fabric.mod.json", "META-INF/neoforge.mods.toml", "META-INF/mods.toml", "*.mixins.json")) {
        expand(props)
    }
    // Without format fields for this version, the mod ships no pack.mcmeta and the loaders describe the pack.
    if (prop("mc.pack_meta").isEmpty()) exclude("pack.mcmeta")
    // Data is written in 1.21.1's formats; the build converts it for other versions (Porting.md).
    val formats = DataFormats(
        itemField = sc.eval(mcVersion, "<1.20.5"),
        pluralFolders = sc.eval(mcVersion, "<1.21"),
        ingredients = sc.eval(mcVersion, ">=1.21.2"),
        entityTypeKey = sc.eval(mcVersion, ">=26.2"),
        singleConditions = sc.eval(mcVersion, ">=26.3"),
    )
    inputs.property("dataFormats", formats.toString())
    // Item model definitions (assets/<mod>/items/) exist from 1.21.4 only.
    if (sc.eval(mcVersion, "<1.21.4")) exclude("assets/*/items/**")
    doLast {
        convertData(destinationDir, formats)
    }
}

// ModDevGradle's Minecraft setup is memory-hungry; never run two at once (common and neoforge both use it).
interface MinecraftArtifactsMutex : org.gradle.api.services.BuildService<org.gradle.api.services.BuildServiceParameters.None>

val mutex = gradle.sharedServices.registerIfAbsent("cartridgeMinecraftArtifactsMutex", MinecraftArtifactsMutex::class) {
    maxParallelUsages = 1
}
tasks.matching { it.name == "createMinecraftArtifacts" }.configureEach { usesService(mutex) }
