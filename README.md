# Cartridge

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/cartridge-mod?logo=Modrinth&label=Modrinth%20Downloads&color=00af5c)](https://modrinth.com/mod/cartridge-mod) [![CurseForge Downloads](https://img.shields.io/curseforge/dt/cartridge-mod?logo=CurseForge&label=CurseForge%20Downloads&color=f16436)
](https://www.curseforge.com/minecraft/mc-mods/cartridge-mod)

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
  `cartridge-host`, from this repository's [`natives/`](natives) folder. The file is about 76 KB for Windows and
  24 KB for Linux. The mod checks it against a SHA-256 hash built into the jar, so it never runs any other file. The
  download is a plain request to `raw.githubusercontent.com`; nothing about you or your game is sent.

  If your PC can't reach GitHub, download the file for your platform yourself. Put it at
  `.minecraft/cartridge_mod/natives/<windows-x86_64|linux-x86_64>/`. The game log names the exact path and hash.
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
- Nothing else. The Gradle wrapper (9.8.0) comes with the repository. The emulator cores and `cartridge-host` are
  committed as prebuilt binaries, so a normal build needs no C compiler.

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
Game folders are under `runs/` (git-ignored). Dev runs use `cartridge-host` straight from `natives/` instead of
downloading it.

### Rebuilding cartridge-host (optional)
`cartridge-host` is the small C program in [`sidecar/`](sidecar) that runs the libretro emulator cores in their own
process. Its builds for both platforms are committed in [`natives/`](natives), and the mod downloads them from
there.

After changing `sidecar/`:
1. Install [Zig](https://ziglang.org) **0.17.0**. Zig cross-compiles both platforms from either OS. Put it on `PATH`,
   or set `ZIG` to the full path of `zig`/`zig.exe`.
2. Run `./gradlew updateNatives`. It builds both platforms and copies them into `natives/`.
3. Commit `natives/`. The next build writes the new hashes into the jars.

The commit a release is tagged at (`v<version>`, e.g. `v1.0.0`) must contain the `natives/` that release was built
with. Released jars download from that tag first and then from `main`, and they accept only their own hash.

## Repository layout
| Folder | What's there |
|---|---|
| `common/`, `fabric/`, `neoforge/`, `forge/` | Cartridge: the shared code and each loader's entry points. Shared sources are written for 1.21.1; [Stonecutter](https://stonecutter.kikugie.dev) generates each version's variant. |
| `arcade/` | Cartridge: Arcade, laid out the same way. |
| `buildSrc/` | The shared Gradle build logic. |
| `stonecutter.properties.toml` | Mod metadata and dependency versions for each Minecraft version. |
| `sidecar/` | `cartridge-host`'s C source and Zig build. |
| `natives/` | `cartridge-host`'s prebuilt binaries, downloaded by the mod at runtime. |
| `cores/`, `arcade/cores/` | The bundled libretro cores, each with its license and `SOURCE.md` (the exact upstream source). |
| `models/` | Blockbench sources of the block and item models. |
| `tools/` | Asset scripts (`sounds.py` makes every sound effect from scratch). |

## Licenses
Cartridge and Cartridge: Arcade, their code, assets and `cartridge-host`, are free software under the
[GNU General Public License v3.0 only](LICENSE.md) (`GPL-3.0-only`).

The bundled emulator cores keep their own licenses. Each core's license text and the exact source commit are in
`cores/<core>/` and `arcade/cores/mame/`, and inside the jars:

| Core | License |
|---|---|
| FCEUmm | GPL-2.0 |
| Snes9x | Non-commercial |
| Genesis Plus GX | Non-commercial |
| SameBoy | MIT |
| mGBA | MPL-2.0 |
| Mupen64Plus-Next | GPL-2.0 |
| MAME | GPL-2.0+ |
