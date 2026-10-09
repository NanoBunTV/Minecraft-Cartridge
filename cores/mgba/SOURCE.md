# mGBA (bundled emulator core)

Cartridge bundles the **mGBA** libretro core (D140, D183) for Windows x64 and Linux x64, for the Pocketron Advance
and the Pocketron Advance SP. Its license is the Mozilla Public License 2.0 (the text is in `LICENSE` beside this
file). It has its own built-in BIOS, so no Nintendo BIOS is needed or downloaded. It runs in its own process
(cartridge-host) and is not part of Cartridge's own code.

- **Upstream:** https://github.com/mgba-emu/mgba (libretro's copy: https://github.com/libretro/mgba)
- **Commit:** `7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6` (2026-09-16, "Libretro: Disable debug logging noise")
- **Source for exactly these binaries:** https://github.com/libretro/mgba/tree/7a12d6d4b9acb14c0ae62c9166b6a2f3d08007f6
- **Binaries:** the libretro buildbot's nightly builds of that commit, unchanged:
  - `mgba_libretro.dll.zip`: windows/x86_64, built 2026-10-02; the DLL's SHA-256 is
    `0f6919aa92f83d2e6b7cb830c6b19c1e34e032a517c655cf78be6d20e2fb23ac`
  - `mgba_libretro.so.zip`: linux/x86_64, built 2026-10-02; the library's SHA-256 is
    `c840f305825d07c9bec7a6ec18841073c4b539fd234f73623474f97c507f1cdc`

  Both libraries carry `0.11-212-7a12d6d` as their version.

## Notes
- `mgba_use_bios` stays OFF: the built-in BIOS for everyone, even if someone has a real one in the system folder.
- Its real-time clock would read the PC's clock; `emu/MgbaClock` switches it to emulated time after loading (D186).
- Its audio is 65536 Hz.

## Updating the core
Same as FCEUmm's (cores/fceumm/SOURCE.md): replace both zips with builds of one commit, update this file, change
the core's version and marker in `emu/CoreSpec`, run the tests. Check that a savestate still has the clock's extra
data where `MgbaClock` looks (the test `mgbaRunsGameBoyAdvance` does).
