# MAME (bundled emulator core)

Cartridge Arcade bundles the **MAME** libretro core (D194) for Windows x64 and Linux x64, for its Arcade Cabinets.
MAME as a whole is licensed under the GNU General Public License, version 2 (`COPYING` beside this file says how,
and `GPL-2.0` holds the text); some of its source files carry less restrictive licenses, as their headers say. It
runs in its own process (cartridge-host) and is not part of Cartridge's or Cartridge Arcade's own code. MAME is a
registered trademark of Gregory Ember; Cartridge Arcade is not affiliated with MAMEdev.

No games, BIOS or other ROMs come with it: players burn their own romsets onto Arcade Boards (D197).

- **Upstream:** https://github.com/libretro/mame (libretro's fork of https://github.com/mamedev/mame)
- **Version:** MAME 0.289, commit `9069f39340f2d2b1795df8e71bb1d3d0fbc76598` (2026-09-24)
- **Source for exactly these binaries:**
  https://github.com/libretro/mame/tree/9069f39340f2d2b1795df8e71bb1d3d0fbc76598
- **Binaries:** the libretro buildbot's nightly builds of that commit, unchanged:
  - `mame_libretro.dll.zip`: windows/x86_64, built 2026-10-05; the zip's SHA-256 is
    `fafe2bfb18bfb2cf12a96f3f001b40a02a6d1e8ff8abb2c301c9c7e6f4dcc621`, the DLL's
    `e2dd0da4b431513b487a5135cb3325856c3ce592713829889568165e7b35c6a1`
  - `mame_libretro.so.zip`: linux/x86_64, built 2026-10-05; the zip's SHA-256 is
    `1f465c062a086c0278bf58151d7f3a63d8a764066f6f588bfed3bf142d2cc1f6`, the library's
    `b7982bb57b03de7b07956755707ad6133a8e1dd5675a4bd74252f8d0de37e3ee`

  Both libraries carry `0.289 (9069f393)` as their version.

## Notes
- MAME loads games only from files (`need_fullpath`), and knows a game by its file's name: cartridge-host writes the
  board's zip as `<set>.zip` into a folder of its own.
- MAME finds a set's files by CRC whatever they're called or where they sit in the zip, so an Arcade Board's one
  zip also carries its parent's and BIOS's files (D197).
- The options Cartridge Arcade sets (the same for every player): no config files read or written, no booting from
  a command line, no software lists or cheats, no throttling; the joystick's dead zone, saturation and threshold
  fixed; vertical games turned upright inside MAME (`mame_rotation_mode=internal`); and **no per-game button
  layouts** (`mame_buttons_profiles=disabled`), so buttons 1 to 6 are always RetroPad B, A, Y, X, L and R, Coin is
  Select and Start is Start.
- MAME exposes no RAM to the frontend, and two processes' savestates differ in a few harmless bytes (timing, and
  some games' sound simulation after a load), so netplay's desync check hashes the picture (D194).

## Updating the core
Replace both zips with builds of one commit (check both libraries carry the same version string), update this
file, change the core's version and marker in Cartridge Arcade's `ArcadeCore`, and run the tests and the self-test
with a few romsets (the versions of romsets that work change with MAME's).
