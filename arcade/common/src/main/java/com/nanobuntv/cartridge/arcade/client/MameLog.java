package com.nanobuntv.cartridge.arcade.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** What MAME says about a romset as it boots, read from its log (D206), and a board's zip built from several sets. */
final class MameLog {
    private static final Pattern NOT_FOUND = Pattern.compile("^(?:\\[core error] )?(\\S+) NOT FOUND(?: \\(tried in ([^)]*)\\))?");
    private static final Pattern INFO = Pattern.compile("^\\[core info] (NAME|PARENT|DESCRIPTION): (.*)$");

    private MameLog() {}

    /** What MAME said about a set: whether it runs, its name, parent and title, and each missing file's sets tried. */
    record Report(boolean loaded, String name, String parent, String description, Map<String, List<String>> missing) {}

    /** MAME's log lines, read: its NAME, PARENT and DESCRIPTION lines and its "NOT FOUND (tried in ...)" ones. */
    static Report parse(boolean loaded, List<String> lines) {
        String name = "", parent = "", description = "";
        Map<String, List<String>> missing = new LinkedHashMap<>();
        for (String line : lines) {
            Matcher info = INFO.matcher(line);
            if (info.matches()) {
                switch (info.group(1)) {
                    case "NAME" -> name = info.group(2).strip();
                    case "PARENT" -> parent = info.group(2).strip().equals("0") ? "" : info.group(2).strip();
                    default -> description = info.group(2).strip();
                }
                continue;
            }
            Matcher m = NOT_FOUND.matcher(line.strip());
            if (m.find()) {
                List<String> tried = m.group(2) == null ? List.of() : List.of(m.group(2).trim().split("\\s+"));
                missing.putIfAbsent(m.group(1), tried);
            }
        }
        return new Report(loaded && missing.isEmpty(), name, parent, description, missing);
    }

    /** The set's own files, then each other set's in a folder named after it (D206). */
    static byte[] merge(byte[] main, Map<String, byte[]> others) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(main.length * 2);
        Set<String> names = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            copy(main, "", zip, names);
            for (Map.Entry<String, byte[]> other : others.entrySet()) copy(other.getValue(), other.getKey() + "/", zip, names);
        }
        return out.toByteArray();
    }

    private static void copy(byte[] source, String folder, ZipOutputStream zip, Set<String> names) throws IOException {
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(source))) {
            for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                if (entry.isDirectory()) continue;
                String name = folder + entry.getName();
                if (!names.add(name)) continue;
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(315532800000L); // 1980-01-01: the same zip from the same files, for everyone
                zip.putNextEntry(copy);
                in.transferTo(zip);
                zip.closeEntry();
            }
        }
    }

    /**
     * The title as a marquee shows it: without MAME's notes on the version ("Street Fighter II: The World Warrior
     * (World 910522)" reads "Street Fighter II: The World Warrior").
     */
    static String marqueeTitle(String title) {
        String t = title.replaceAll("(\\s*\\([^()]*(\\)|$))+$", "").strip();
        return t.isEmpty() ? title : t;
    }
}
