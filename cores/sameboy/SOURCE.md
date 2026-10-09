# SameBoy (bundled emulator core)

Cartridge bundles the **SameBoy** libretro core (D139, D173) for Windows x64 and Linux x64, for the Pocketron, the
Pocketron Color and the Pocketron Adapter (the Super Game Boy). Its license is MIT (the text is in `LICENSE` beside this
file). Its own free boot ROMs are built into the library, so no Nintendo boot ROM is needed or downloaded. It runs in
its own process (cartridge-host) and is not part of Cartridge's own code.

- **Upstream:** https://github.com/LIJI32/SameBoy (libretro's copy: https://github.com/libretro/SameBoy)
- **Commit:** `8230189896a8bb6598574d302ba0ad3658f98ab4` (2026-03-04, "Update libretro branch to v1.0.3")
- **Source for exactly these binaries:** https://github.com/libretro/SameBoy/tree/8230189896a8bb6598574d302ba0ad3658f98ab4
- **Binaries:** the libretro buildbot's nightly builds of that commit, unchanged:
  - `sameboy_libretro.dll.zip`: windows/x86_64, built 2026-10-04; the DLL's SHA-256 is
    `a60adfd6e3b7c2f34bdddd4aba4ac7aa8576c47df3f95d4f34ee5203b7541eeb`
  - `sameboy_libretro.so.zip`: linux/x86_64, built 2026-10-04; the library's SHA-256 is
    `26b3de38033e14cb2185f47811d38340bd47f56d55dd82cb14cdbd39a88a8208`

  Both libraries report `1.0.3 8230189` as their version.

## Notes
- Its real-time clock option stays on `accurate` (emulated time, never the PC's clock), or players' games would differ
  (D173). The model comes from the console: `Game Boy`, `Game Boy Color` or `Super Game Boy`.
- It can't make a savestate before its first frame; the host runs that frame silently right after loading.
- It gives one audio sample per two CPU clocks (about 2 MHz); the host averages that down to about 48 kHz.

## Updating the core
Same as FCEUmm's (cores/fceumm/SOURCE.md): replace both zips with builds of one commit, update this file, change
the core's version and marker in `emu/CoreSpec`, run the tests.
