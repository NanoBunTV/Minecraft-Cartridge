plugins {
    id("dev.kikugie.stonecutter")
}

// The version whose code lives in the shared src/ folders right now. Switch with the "Set active project" tasks.
stonecutter active "1.21.1"

// The sidecar (D7, D8): `zig build` in sidecar/ for every platform. The built binaries are committed in natives/
// (D218): the mod downloads its platform's from the public repository at runtime and checks its SHA-256, so a normal
// build needs no Zig. After changing sidecar/, run `gradlew updateNatives` and commit natives/. Zig comes from PATH,
// or from the ZIG environment variable (full path to zig/zig.exe).
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
tasks.register<Sync>("updateNatives") {
    group = "build"
    description = "Builds cartridge-host with Zig and copies it into natives/, to commit."
    dependsOn("buildSidecar")
    from(sidecar.resolve("zig-out/natives"))
    into(rootProject.file("natives"))
}
