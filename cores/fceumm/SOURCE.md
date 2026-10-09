# FCEUmm (bundled emulator core)

Cartridge bundles the **FCEUmm** libretro core (D114) for Windows x64 and Linux x64. FCEUmm is free software under
the **GNU General Public License, version 2** (the text is in `COPYING` beside this file). It runs in its own process
(cartridge-host) and is not part of Cartridge's own code.

- **Upstream:** https://github.com/libretro/libretro-fceumm
- **Commit:** `7a542dab1e87679921962a9f056186eca425c0c2` (2026-09-26, "Merge pull request #657 from cscd98/psp-fix")
- **Source for exactly these binaries:**
  https://github.com/libretro/libretro-fceumm/tree/7a542dab1e87679921962a9f056186eca425c0c2
  (archive: https://github.com/libretro/libretro-fceumm/archive/7a542dab1e87679921962a9f056186eca425c0c2.zip)
- **Binaries:** the libretro buildbot's nightly builds of that commit, unchanged:
  - `fceumm_libretro.dll.zip`: windows/x86_64, built 2026-10-04; the DLL's SHA-256 is
    `741b7863fc6c04492c4bd1eed1cfff17d6aa070f35f03521ad7bd8cb366ee071`
  - `fceumm_libretro.so.zip`: linux/x86_64, built 2026-10-05; the library's SHA-256 is
    `07f326a4f8c0379c777b35adaefc129010a6c0090b3e11739299af11ae378f5e`

  Both libraries report `(SVN) 7a542da` as their version.

## Release checklist (GPLv2 section 3)

Every published Cartridge release that carries these binaries also offers the matching source from the same place:
attach the source archive above to the release (or host it beside the download) for as long as that release is
offered.

## Updating the core

1. Download both platforms' `fceumm_libretro.*.zip` from the buildbot and check that both report the same
   `(SVN) <commit>`.
2. Replace the zips here, update this file and `COPYING` from that commit.
3. Change `CoreSpec.FCEUMM`'s version and marker (common/.../emu/CoreSpec.java). Savestates (Memory Cards, resume snapshots) made on
   another core version can't be loaded; games resume from power-on instead (D89).
4. Run the tests: NativesTest checks the bundled libraries carry the marker's commit.
