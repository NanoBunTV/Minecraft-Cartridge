package com.nanobuntv.cartridge.arcade.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** Reading what MAME says about a romset, and building a board's zip (D206), from the spike's real MAME output. */
class MameLogTest {
    @Test
    void aGoodSet() {
        MameLog.Report r = MameLog.parse(true, List.of(
                "[core info] PARENT: 0", "[core info] NAME: sf2",
                "[core info] DESCRIPTION: Street Fighter II: The World Warrior (World 910522)"));
        assertTrue(r.loaded());
        assertEquals("sf2", r.name());
        assertEquals("", r.parent());
        assertEquals("Street Fighter II: The World Warrior (World 910522)", r.description());
    }

    @Test
    void aCloneWithoutItsParent() {
        MameLog.Report r = MameLog.parse(false, List.of(
                "[core info] PARENT: puckman", "[core info] NAME: pacman", "[core info] DESCRIPTION: Pac-Man (Midway)",
                "[core error] pacman.6e NOT FOUND (tried in pacman puckman)",
                "pacman.6f NOT FOUND (tried in pacman puckman)",
                "pacman.6e NOT FOUND (tried in pacman puckman)",
                "[core error] Fatal error: Required files are missing, the machine cannot be run."));
        assertFalse(r.loaded());
        assertEquals("puckman", r.parent());
        assertEquals(List.of("pacman.6e", "pacman.6f"), new ArrayList<>(r.missing().keySet()));
        assertEquals(List.of("pacman", "puckman"), r.missing().get("pacman.6e"));
    }

    @Test
    void aSetFromAnotherVersion() {
        MameLog.Report r = MameLog.parse(false, List.of("[core error] buf1 NOT FOUND (tried in sf2)"));
        assertEquals(List.of("sf2"), r.missing().get("buf1"));
    }

    @Test
    void mergingPutsOtherSetsInFolders() throws Exception {
        Map<String, byte[]> others = new LinkedHashMap<>();
        others.put("neogeo", zip(Map.of("sp-s2.sp1", "bios")));
        byte[] merged = MameLog.merge(zip(Map.of("game.p1", "game")), others);
        Map<String, String> entries = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(merged))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) entries.put(e.getName(), new String(in.readAllBytes()));
        }
        assertEquals(Map.of("game.p1", "game", "neogeo/sp-s2.sp1", "bios"), entries);
        assertEquals(java.util.Arrays.toString(merged), java.util.Arrays.toString(MameLog.merge(zip(Map.of("game.p1", "game")), others)),
                "the same files make the same zip");
    }

    @Test
    void marqueesLeaveOutTheVersion() {
        assertEquals("Pac-Man", MameLog.marqueeTitle("Pac-Man (Midway)"));
        assertEquals("Street Fighter II: The World Warrior",
                MameLog.marqueeTitle("Street Fighter II: The World Warrior (World 910522)"));
        assertEquals("Street Fighter II: The World Warrior", MameLog.marqueeTitle("Street Fighter II: The World Warrior (World 9105"));
        assertEquals("(Untitled)", MameLog.marqueeTitle("(Untitled)"));
    }

    private static byte[] zip(Map<String, String> files) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (var f : files.entrySet()) {
                z.putNextEntry(new ZipEntry(f.getKey()));
                z.write(f.getValue().getBytes());
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
