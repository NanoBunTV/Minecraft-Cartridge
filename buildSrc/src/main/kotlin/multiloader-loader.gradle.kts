import java.security.MessageDigest

// Applied to the fabric and neoforge nodes: compiles the common sources into each loader's jar,
// so the shipped jars are self-contained and `common` itself is never published.
plugins {
    id("multiloader-common")
}

val commonJava = configurations.create("commonJava") { isCanBeConsumed = false }
val commonResources = configurations.create("commonResources") { isCanBeConsumed = false }

dependencies {
    compileOnly(project(commonPath))
    commonJava(project(path = commonPath, configuration = "commonJava"))
    commonResources(project(path = commonPath, configuration = "commonResources"))
}

tasks.named<JavaCompile>("compileJava") {
    dependsOn(commonJava)
    source(commonJava)
}

tasks.processResources {
    dependsOn(commonResources)
    from(commonResources)
    if (!isArcade) {
        // cartridge-host (D218): dev runs get the binaries from natives/; the jar leaves them out (below) and carries
        // natives/host.properties instead, the SHA-256 of each and where emu/Natives downloads them from.
        val natives = rootProject.file("natives")
        val version = modProp("version")
        val sources = modProp("sources")
        inputs.dir(natives)
        inputs.property("hostSources", "$sources@$version")
        from(natives) { into("natives") }
        doLast {
            val raw = sources.replace("https://github.com/", "https://raw.githubusercontent.com/")
            val lines = mutableListOf(
                "# cartridge-host for each platform, downloaded and checked by emu/Natives (D218).",
                // The release's tag first, then main: the file a version was built with stays at its tag.
                "url.0=$raw/v$version/natives/{platform}/{file}",
                "url.1=$raw/main/natives/{platform}/{file}",
            )
            natives.listFiles()!!.sorted().forEach { dir ->
                val host = dir.listFiles()!!.single { it.name.startsWith("cartridge-host") }
                val sha = MessageDigest.getInstance("SHA-256").digest(host.readBytes())
                lines += "sha256.${dir.name}=" + sha.joinToString("") { "%02x".format(it) }
            }
            destinationDir.resolve("natives/host.properties").writeText(lines.joinToString("\n", postfix = "\n"))
        }
    }
    // The bundled emulator cores (D114, D137), unzipped from the pinned buildbot archives, with their license texts
    // and source links. The add-on carries MAME alone (D195), from arcade/cores/.
    val coresDir = rootProject.file(if (isArcade) "arcade/cores" else "cores")
    val cores = if (isArcade) listOf("mame") else listOf("fceumm", "snes9x", "genesis_plus_gx", "sameboy", "mgba", "mupen64plus_next")
    for (core in cores) {
        from(zipTree(coresDir.resolve("$core/${core}_libretro.dll.zip"))) { into("cores/windows-x86_64") }
        from(zipTree(coresDir.resolve("$core/${core}_libretro.so.zip"))) { into("cores/linux-x86_64") }
        from(coresDir.resolve(core)) { include("COPYING", "LICENSE", "GPL-2.0", "SOURCE.md"); into("cores/$core") }
    }
}

// No executables in the jar (CurseForge refuses them, D218): emu/Natives downloads cartridge-host instead.
tasks.named<Jar>("jar") {
    exclude("natives/*/cartridge-host*")
}

tasks.named<Jar>("sourcesJar") {
    dependsOn(commonJava, commonResources)
    from(commonJava)
    from(commonResources)
}
