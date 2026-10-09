# Genesis Plus GX (bundled emulator core)

Cartridge bundles the **Genesis Plus GX** libretro core (D151) for Windows x64 and Linux x64, for the Vortex Drive.
Its main license (the text is in `LICENSE` beside this file) allows redistribution **only if it isn't sold or used in
a commercial product or activity**, like Snes9x's; Cartridge is never monetized (D138). Parts of it carry their own
licenses, all in the same `LICENSE` file: Nuked OPN2 (LGPL 2.1), Tremor, minimp3, libchdr and others. It runs in its
own process (cartridge-host) and is not part of Cartridge's own code.

- **Upstream:** https://github.com/libretro/Genesis-Plus-GX
- **Commit:** `58c341487e5bfcf979ea68413c7987633adb0c56` (2026-10-02)
- **Source for exactly these binaries:** https://github.com/libretro/Genesis-Plus-GX/tree/58c341487e5bfcf979ea68413c7987633adb0c56
- **Binaries:** the libretro buildbot's nightly builds of that commit, unchanged:
  - `genesis_plus_gx_libretro.dll.zip`: windows/x86_64, built 2026-10-03; the DLL's SHA-256 is
    `347cc88aa43167b700621d5e24e48c833e5efbe6860702d3b82d37e140e57f42`
  - `genesis_plus_gx_libretro.so.zip`: linux/x86_64, built 2026-10-03; the library's SHA-256 is
    `de23e3c647e0b1a8767c8f91f2dad402a10de3f59016f0b321f963d549b481f5`

  Both libraries report `v1.7.4 58c3414` as their version.

## Rules that come with it
- No release of Cartridge may be sold, paywalled, or earn money through download rewards or monetization programs
  while it carries Genesis Plus GX (D138, D151).
- The `LICENSE` text ships beside the core in the jar (`cores/genesis_plus_gx/LICENSE`). The binaries are unmodified,
  so the source link above is the source offer for the LGPL parts.

## Updating the core
Same as FCEUmm's (cores/fceumm/SOURCE.md): replace both zips with builds of one commit, update this file, change
the Vortex Drive's core version and marker in `emu/CoreSpec`, run the tests.
