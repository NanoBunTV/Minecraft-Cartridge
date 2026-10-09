# Mupen64Plus-Next (bundled emulator core)

Cartridge bundles the **Mupen64Plus-Next** libretro core (D189) for Windows x64 and Linux x64, for the Pixelon 64. Its
license is the GNU General Public License, version 2 (the text is in `LICENSE` beside this file). It runs in its own
process (cartridge-host) and is not part of Cartridge's own code. No N64 BIOS or other firmware is needed.

- **Upstream:** https://github.com/libretro/mupen64plus-libretro-nx
- **Commit:** `12edd2c74a517ff86dfa8cfc71ad75e4c10486d5` (2026-10-01, "Fix Android dynarec offsets and stale ARM64
  headers")
- **Source for exactly these binaries:**
  https://github.com/libretro/mupen64plus-libretro-nx/tree/12edd2c74a517ff86dfa8cfc71ad75e4c10486d5
- **Binaries:** the libretro buildbot's nightly builds of that commit, unchanged:
  - `mupen64plus_next_libretro.dll.zip`: windows/x86_64, built 2026-10-05; the DLL's SHA-256 is
    `1bc66bfac8303fb0db99a9f2cadb133d1b39312560244b64d20e2f10f56a0e12`
  - `mupen64plus_next_libretro.so.zip`: linux/x86_64, built 2026-10-05; the library's SHA-256 is
    `beb4598e0999e022fac0ff206bf9728d3210adfdaa93468bcaa8e81ad64fdc72`

  Both libraries carry `3.0-Vulkan 12edd2c` as their version.

## Notes
- Cartridge's host has no GPU context, so the core runs with the **Angrylion** software renderer
  (`mupen64plus-rdp-plugin=angrylion`, on all CPU threads) and the ParaLLEl RSP. GLideN64, its default, refuses to
  start without OpenGL.
- The Windows DLL imports only KERNEL32, msvcrt and OPENGL32 (Vulkan is loaded on demand and never used here); the
  Linux library needs libGL.so.1.
- Savestates are about 16 MiB (8 MiB of RDRAM with the Expansion Pak); Cartridge deflates them for the network and
  the Memory Cards. They hold a few dozen bytes of host pointers, which differ between processes but don't affect the
  game: the game's memory replays the same from a savestate in any process.
- The battery save (`RETRO_MEMORY_SAVE_RAM`, 296,960 bytes) holds the EEPROM, SRAM, FlashRAM and all four Controller
  Paks; it isn't in the savestate.

## Updating the core
Same as FCEUmm's (cores/fceumm/SOURCE.md): replace both zips with builds of one commit, update this file, change the
core's version and marker in `emu/CoreSpec`, run the tests.
