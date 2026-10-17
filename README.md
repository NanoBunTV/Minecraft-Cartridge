# Cartridge

Real retro consoles for Minecraft: burn your own ROMs onto cartridges, play them on in-world TVs with friends over
automatic netplay. This repository also holds **Cartridge: Arcade**, the add-on with MAME arcade cabinets.

> [!NOTE]
> **Download the mod from Modrinth:** [Cartridge](https://modrinth.com/mod/cartridge-mod) and
> [Cartridge: Arcade](https://modrinth.com/mod/cartridge-arcade). This repository is the source code. Jars you
> build yourself are not releases. Please don't share them as releases.

- **No games are included.** You burn your own game files. Please don't ask for ROMs or BIOS files in issues; such
  requests will be closed.
- **Windows x64 and Linux x64 only.** macOS isn't supported.
- **Minecraft versions:**
  - 1.19.4 and 1.20.1 on Fabric and Forge.
  - 1.21.1, 1.21.11, 26.1.2, 26.2 and 26.3 on Fabric and NeoForge.
- **One download at runtime.** The first time a console starts, the mod downloads its emulator host,
  `cartridge-host`, from this repository's [Releases](https://github.com/NanoBunTV/Minecraft-Cartridge/releases)
  (see [Releases](#releases)). The file is about 76 KB for Windows and 24 KB for Linux. The mod checks it against a
  SHA-256 hash built into the jar, so it never runs any other file. The download is a plain request to GitHub;
  nothing about you or your game is sent.

  If your PC can't reach GitHub, download the file for your platform from your version's Release yourself
  (`windows-x86_64-cartridge-host.exe` or `linux-x86_64-cartridge-host`). Rename it to `cartridge-host.exe` or
  `cartridge-host` and put it in `.minecraft/cartridge_mod/natives/<windows-x86_64|linux-x86_64>/`. The game log
  names the exact path and hash.
- **Bugs and ideas:** [Issues](https://github.com/NanoBunTV/Minecraft-Cartridge/issues). Please include your
  Minecraft version, your loader, and `logs/latest.log`.

The consoles are invented brands. Neither mod is affiliated with any console maker.

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

## Building from source

### Requirements
- **Git** and **[Git LFS](https://git-lfs.com).** The add-on's MAME builds (`arcade/cores/mame/*.zip`, about 220 MB)
  are LFS files. Run `git lfs install` once, before cloning.
- **A JDK, 17 or newer,** to run Gradle. Gradle's toolchains download the JDKs each Minecraft version compiles with
  (17, 21 and 25) by themselves.
- **[Zig](https://ziglang.org) 0.17.0,** exactly: every build compiles `cartridge-host` from [`sidecar/`](sidecar).
  Zig cross-compiles both platforms from Windows or Linux. Put it on `PATH`, or set `ZIG` to the full path of
  `zig`/`zig.exe`.
- Nothing else. The Gradle wrapper (9.8.0) comes with the repository. The emulator cores are third-party prebuilt
  binaries, committed with their sources cited (see [Licenses](#licenses)).

### Build
```sh
git clone https://github.com/NanoBunTV/Minecraft-Cartridge.git
cd Minecraft-Cartridge
./gradlew build            # Windows: gradlew build
```
This builds every loader on every Minecraft version, for both mods, and runs the unit tests. The first build sets up
each Minecraft version, which takes a while and a few GB of disk space.

The jars land in:
- `<loader>/versions/<minecraft>/build/libs/`: Cartridge, e.g. `fabric/versions/1.21.1/build/libs/`.
- `arcade/<loader>/versions/<minecraft>/build/libs/`: Cartridge: Arcade.

Use the jar without `-sources` in its name.

To build one loader on one version, name its Gradle project, `:<loader>:<minecraft>`:
```sh
./gradlew :fabric:1.21.1:build
./gradlew :arcade-neoforge:26.3:build
```
The loaders are `fabric`, `neoforge` (1.21.1 and later) and `forge` (1.19.4 and 1.20.1). The add-on's are
`arcade-fabric`, `arcade-neoforge` and `arcade-forge`.

### Run in a dev client
```sh
./gradlew :fabric:1.21.1:runClient
./gradlew :neoforge:1.21.1:runServer
```
Game folders are under `runs/` (git-ignored). Dev runs use the `cartridge-host` the build just compiled, in
`sidecar/zig-out/natives/`, instead of downloading it.

### cartridge-host
`cartridge-host` is the small C program in [`sidecar/`](sidecar) that runs the libretro emulator cores in their own
process. Gradle's `buildSidecar` task builds it with Zig into `sidecar/zig-out/natives/` before anything else.
The jars leave it out (CurseForge doesn't allow executables in a jar) and carry its SHA-256 instead. The mod
downloads it from the GitHub Release of its version.

## Releases
Every jar on Modrinth and CurseForge is built by [GitHub Actions](.github/workflows/build.yml) from this
repository, never on a personal PC. Each run builds `cartridge-host` and every jar from the commit it names, lists
the SHA-256 of each file in its summary, and signs them with
[build provenance](https://docs.github.com/actions/security-for-github-actions/using-artifact-attestations). A
version tag (`v1.0.0`) publishes that run's files as the
[GitHub Release](https://github.com/NanoBunTV/Minecraft-Cartridge/releases) for that version: the jars and the two
`cartridge-host` files the mod downloads.

To check that a jar was built here from this source, with the [GitHub CLI](https://cli.github.com):
```sh
gh attestation verify cartridge_mod-fabric-1.0.0+1.21.1.jar --repo NanoBunTV/Minecraft-Cartridge
```

## Repository layout
| Folder | What's there |
|---|---|
| `common/`, `fabric/`, `neoforge/`, `forge/` | Cartridge: the shared code and each loader's entry points. Shared sources are written for 1.21.1; [Stonecutter](https://stonecutter.kikugie.dev) generates each version's variant. |
| `arcade/` | Cartridge: Arcade, laid out the same way. |
| `buildSrc/` | The shared Gradle build logic. |
| `stonecutter.properties.toml` | Mod metadata and dependency versions for each Minecraft version. |
| `sidecar/` | `cartridge-host`'s C source and Zig build. |
| `.github/workflows/` | The CI build that makes every release. |
| `cores/`, `arcade/cores/` | The bundled libretro cores, each with its license and `SOURCE.md` (the exact upstream source). |
| `models/` | Blockbench sources of the block and item models. |
| `tools/` | Asset scripts (`sounds.py` makes every sound effect from scratch). |

## Licenses
Cartridge and Cartridge: Arcade, their code, assets and `cartridge-host`, are free software under the
[GNU General Public License v3.0 only](LICENSE.md) (`GPL-3.0-only`).

The bundled emulator cores keep their own licenses. Each core's license text and the exact source commit are in
`cores/<core>/` and `arcade/cores/mame/`, and inside the jars. The cores are the [libretro buildbot](https://buildbot.libretro.com)'s builds, unchanged. Each `SOURCE.md` names the
upstream repository, the exact commit and the SHA-256 of each library; the CI summary lists the same hashes.

| Core | License | Source |
|---|---|---|
| FCEUmm | GPL-2.0 | [`libretro/libretro-fceumm` at `7a542dab`](https://github.com/libretro/libretro-fceumm/tree/7a542dab1e87679921962a9f056186eca425c0c2) |
| Snes9x | Non-commercial | [`libretro/snes9x` at `fae2fea0`](https://github.com/libretro/snes9x/tree/fae2fea08f74180759ef540ee94259213f503480) |
| Genesis Plus GX | Non-commercial | [`libretro/Genesis-Plus-GX` at `58c34148`](https://github.com/libretro/Genesis-Plus-GX/tree/58c341487e5bfcf979ea68413c7987633adb0c56) |
| SameBoy | MIT | [`libretro/SameBoy` at `82301898`](https://github.com/libretro/SameBoy/tree/8230189896a8bb6598574d302ba0ad3658f98ab4) |
| mGBA | MPL-2.0 | [`libretro/mgba` at `7a12d6d4`](https://github.com/libretro/mgba/tree/7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6) |
| Mupen64Plus-Next | GPL-2.0 | [`libretro/mupen64plus-libretro-nx` at `12edd2c7`](https://github.com/libretro/mupen64plus-libretro-nx/tree/12edd2c74a517ff86dfa8cfc71ad75e4c10486d5) |
| MAME 0.289 | GPL-2.0+ | [`libretro/mame` at `9069f393`](https://github.com/libretro/mame/tree/9069f39340f2d2b1795df8e71bb1d3d0fbc76598) |
