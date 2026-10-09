# Snes9x (bundled emulator core)

Cartridge bundles the **Snes9x** libretro core (D137) for Windows x64 and Linux x64, for the Super Pixelon. Snes9x
is **not** free software in the GPL sense: its license (the text is in `LICENSE` beside this file) allows use, copying
and distribution **for non-commercial purposes only**. That is why Cartridge is never monetized (D138). It runs in its
own process (cartridge-host) and is not part of Cartridge's own code.

- **Upstream:** https://github.com/libretro/snes9x
- **Commit:** `fae2fea08f74180759ef540ee94259213f503480` (2026-09-19)
- **Source for exactly these binaries:** https://github.com/libretro/snes9x/tree/fae2fea08f74180759ef540ee94259213f503480
- **Binaries:** the libretro buildbot's nightly builds of that commit, unchanged:
  - `snes9x_libretro.dll.zip`: windows/x86_64, built 2026-10-04; the DLL's SHA-256 is
    `4328fc8c9fb24c8ec258e959a1712a09525fb154c54edb23c3b89be36f82f8f3`
  - `snes9x_libretro.so.zip`: linux/x86_64, built 2026-10-05; the library's SHA-256 is
    `9b2b6ea9d88f96c156590443ddd05ee56aaf4e495329a889c979921942717a31`

  Both libraries report `1.63 fae2fea` as their version.

## Rules that come with it
- No release of Cartridge may be sold, paywalled, or earn money through download rewards or monetization programs
  (CurseForge rewards, Modrinth monetization) while it carries Snes9x (D138).
- The `LICENSE` text ships beside the core in the jar (`cores/snes9x/LICENSE`).

## Updating the core
Same as FCEUmm's (cores/fceumm/SOURCE.md): replace both zips with builds of one commit, update this file, change
the Super Pixelon's core version in `emu/CoreSpec`, run the tests.
