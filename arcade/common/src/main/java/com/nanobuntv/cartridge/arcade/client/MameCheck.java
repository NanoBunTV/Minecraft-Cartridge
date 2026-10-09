package com.nanobuntv.cartridge.arcade.client;

import com.nanobuntv.cartridge.arcade.ArcadeCore;
import com.nanobuntv.cartridge.arcade.CartridgeArcade;
import com.nanobuntv.cartridge.client.GamePreparer;
import com.nanobuntv.cartridge.emu.HostPlatform;
import com.nanobuntv.cartridge.emu.HostProcess;
import com.nanobuntv.cartridge.emu.Natives;
import com.nanobuntv.cartridge.emu.SharedMemory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;

/**
 * The Writer's check of a romset before it's burned onto an Arcade Board (D197, D206): the zip is test-booted in the
 * bundled MAME, which names the set's parent, its full title and every file it can't find, with the sets it looked
 * in. A file looked for only in the set itself means a romset from another MAME version; one looked for in another
 * set too (the parent, or a BIOS such as {@code neogeo}) asks for that set's zip, whose files then go into the board's
 * zip in a folder named after it (MAME finds files by CRC, wherever they are). Runs off the render thread.
 */
public final class MameCheck implements GamePreparer {
    /** MAME's set names: lower case letters, digits and underscores. */
    private static final Pattern SET_NAME = Pattern.compile("[a-z0-9_]{1,32}");
    /** The frames run once it loads: MAME starts the machine on the first. */
    private static final int CHECK_FRAMES = 3;
    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final Path gameDir;

    public MameCheck(Path gameDir) {
        this.gameDir = gameDir;
    }

    @Override
    public Prepared prepare(Path game, byte[] data, List<Path> extras) throws IOException {
        String file = game.getFileName().toString();
        String set = file.toLowerCase(Locale.ROOT).endsWith(".zip") ? file.substring(0, file.length() - 4).toLowerCase(Locale.ROOT) : "";
        if (!SET_NAME.matcher(set).matches()) return Prepared.error(Component.translatable("cartridge_arcade.check.not_set", file));
        if (!CartridgeArcade.looksLikeZip(data)) return Prepared.error(Component.translatable("cartridge_arcade.check.not_zip", file));

        Map<String, byte[]> extraSets = new LinkedHashMap<>();
        for (Path extra : extras) {
            String name = extra.getFileName().toString().toLowerCase(Locale.ROOT).replaceFirst("\\.zip$", "");
            extraSets.put(name, Files.readAllBytes(extra));
        }
        byte[] board = extraSets.isEmpty() ? data : MameLog.merge(data, extraSets);
        MameLog.Report report = run(set, board);

        if (report.loaded()) {
            String title = report.description().isEmpty() ? set : report.description();
            return Prepared.ready(board, CartridgeArcade.system(set), title,
                    Component.translatable("cartridge_arcade.check.ready", title));
        }
        if (report.name().isEmpty() && report.missing().isEmpty()) {
            return Prepared.error(Component.translatable("cartridge_arcade.check.unknown", set, ArcadeCore.MAME.marker()));
        }
        // Another set's files are wanted: the first such set not given yet.
        Set<String> wanted = new LinkedHashSet<>();
        List<String> ownMissing = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : report.missing().entrySet()) {
            boolean elsewhere = false;
            for (String tried : e.getValue()) {
                if (!tried.equals(set) && !extraSets.containsKey(tried)) {
                    wanted.add(tried);
                    elsewhere = true;
                }
            }
            if (!elsewhere) ownMissing.add(e.getKey());
        }
        if (!wanted.isEmpty()) {
            String next = wanted.iterator().next();
            return Prepared.needs(List.of(next + ".zip"), Component.translatable("cartridge_arcade.check.needs",
                    report.description().isEmpty() ? set : report.description(), next + ".zip"));
        }
        String files = String.join(", ", ownMissing.subList(0, Math.min(4, ownMissing.size())))
                + (ownMissing.size() > 4 ? ", ..." : "");
        return Prepared.error(Component.translatable("cartridge_arcade.check.other_version", ArcadeCore.MAME.marker(), files));
    }

    /** Boots the set in MAME and collects what it says. */
    private MameLog.Report run(String set, byte[] zip) throws IOException {
        HostPlatform platform = HostPlatform.current().orElseThrow(() -> new IOException(HostPlatform.unsupportedMessage()));
        Path hostExe, core;
        synchronized (MameCheck.class) {
            hostExe = Natives.extractHost(gameDir, platform);
            core = Natives.extractCore(gameDir, platform, ArcadeCore.MAME);
        }
        ConcurrentLinkedQueue<String> log = new ConcurrentLinkedQueue<>();
        Path shm = SharedMemory.directory(gameDir).resolve("console-check-" + ProcessHandle.current().pid() + "-"
                + COUNTER.incrementAndGet() + SharedMemory.FILE_SUFFIX);
        boolean loaded;
        try (HostProcess host = HostProcess.start(hostExe, shm, gameDir.resolve("cartridge_mod").resolve("system"), log::add)) {
            host.loadCore(core);
            for (var option : CartridgeArcade.ARCADE.options().entrySet()) host.setOption(option.getKey(), option.getValue());
            host.setOption("mame_buttons_profiles", "enabled"); // only here: MAME then names the set, its parent and title
            try {
                host.loadGame(set + ".zip", zip);
                host.run(CHECK_FRAMES, HostProcess.RUN_MUTE | HostProcess.RUN_NO_VIDEO, 2, new int[CHECK_FRAMES * 2]);
                loaded = true;
            } catch (HostProcess.HostException e) {
                loaded = false;
            }
        }
        return MameLog.parse(loaded, List.copyOf(log));
    }
}
