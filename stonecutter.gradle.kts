plugins {
    id("dev.kikugie.stonecutter")
}

// The version whose code lives in the shared src/ folders right now. Switch with the "Set active project" tasks.
stonecutter active "1.21.1"

// The sidecar (D7, D8): `zig build` in sidecar/ for every platform, into sidecar/zig-out/natives/. Every build compiles
// it from source (D220): the loader jars hash it, dev runs and tests use it, and CI attaches it to the GitHub Release
// the mod downloads it from (D218). Nothing built is committed. Zig comes from PATH, or from the ZIG environment
// variable (full path to zig/zig.exe).
val sidecar = rootProject.file("sidecar")
tasks.register<Exec>("buildSidecar") {
    group = "build"
    description = "Builds cartridge-host for all platforms with Zig."
    workingDir = sidecar
    commandLine(providers.environmentVariable("ZIG").getOrElse("zig"), "build")
    inputs.dir(sidecar.resolve("src"))
    inputs.dir(sidecar.resolve("include"))
    inputs.files(sidecar.resolve("build.zig"), sidecar.resolve("build.zig.zon"))
    outputs.dir(sidecar.resolve("zig-out/natives"))
}
