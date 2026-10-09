package com.nanobuntv.cartridge.emu;

import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.rom.RomFormats;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * The consoles (D11, D136): which core each runs and which kinds of game ({@link CartridgeContents#system()}) its
 * slot takes. One Blank Cartridge serves them all; the game burned onto it decides which console it fits (D143).
 * <p>
 * Cartridge's own are the constants here; an add-on registers more with {@link #builder} (D195), in its mod
 * initializer, on both sides, before any world loads. Kinds compare by identity.
 */
public final class ConsoleKind {
    /** How a console's networked game runs (D12, D189, D196). */
    public enum Netplay {
        /** Every frame on time, guessing the other players' inputs and rolling back when a guess was wrong. */
        ROLLBACK,
        /** Only frames whose inputs are all in, with an input delay covering the round trip. */
        LOCKSTEP,
        /** Rollback if this PC re-runs the rollback window fast enough, measured when the game boots; else lockstep. */
        BY_SPEED
    }

    /** What the desync checksum hashes (D12, D194). */
    public enum Checksum {
        /** The game's RAM: system, battery and video. */
        RAM,
        /** The last picture: for a core without RAM to look at whose savestates carry harmless differences (MAME). */
        PICTURE
    }

    /** The most ports any console has; netplay's codecs accept this many. */
    public static final int MAX_PORTS = 4;

    private static final List<ConsoleKind> ALL = new ArrayList<>();

    /** The Pixelon Entertainment System (D141): NES cartridges and Famicom Disk System games (D74). */
    public static final ConsoleKind PIXELON = builder("pixelon", CoreSpec.FCEUMM)
            .systems(CartridgeContents.NES, CartridgeContents.FDS).register();
    /** The Super Pixelon (D136, D141). */
    public static final ConsoleKind SUPER_PIXELON = builder("super_pixelon", CoreSpec.SNES9X)
            .systems(CartridgeContents.SNES).register();
    /** The Vortex Drive (D151, D152), the Sega Genesis stand-in. */
    public static final ConsoleKind VORTEX_DRIVE = builder("vortex_drive", CoreSpec.GENESIS_PLUS_GX)
            .systems(CartridgeContents.GENESIS).register();
    /** The Pocketron (D159, D162), the original Game Boy: Game Boy games and those that also run on the Color. */
    public static final ConsoleKind POCKETRON = builder("pocketron", CoreSpec.SAMEBOY)
            .systems(CartridgeContents.GAME_BOY, CartridgeContents.GAME_BOY_DUAL)
            .option("sameboy_model", "Game Boy").register();
    /** The Pocketron Color (D159): every Game Boy and Game Boy Color game. */
    public static final ConsoleKind POCKETRON_COLOR = builder("pocketron_color", CoreSpec.SAMEBOY)
            .systems(CartridgeContents.GAME_BOY, CartridgeContents.GAME_BOY_DUAL, CartridgeContents.GAME_BOY_COLOR)
            .option("sameboy_model", "Game Boy Color").register();
    /**
     * The Pocketron Adapter in the Super Pixelon (D166, D167): a Super Game Boy; Color-only games don't run. The Super
     * Game Boy 2 (D215): the first one runs the Game Boy off the SNES's clock, 2.4% fast and its sound that much higher.
     */
    public static final ConsoleKind POCKETRON_ADAPTER = builder("pocketron_adapter", CoreSpec.SAMEBOY)
            .systems(CartridgeContents.GAME_BOY, CartridgeContents.GAME_BOY_DUAL)
            .option("sameboy_model", "Super Game Boy 2").register();
    /**
     * Two Pocketrons on a link cable (D177): one SameBoy runs both, side by side. Each one's model comes with the game
     * (the handhelds' own), as {@code sameboy_model_1} and {@code _2}.
     */
    public static final ConsoleKind POCKETRON_LINK = builder("pocketron_link", CoreSpec.SAMEBOY)
            .systems(CartridgeContents.GAME_BOY_LINK)
            .option("sameboy_link", "enabled").option("sameboy_screen_layout", "left-right").register();
    /** The Pocketron Advance and the Pocketron Advance SP (D183, D184): Game Boy Advance games only. */
    public static final ConsoleKind POCKETRON_ADVANCE = builder("pocketron_advance", CoreSpec.MGBA)
            .systems(CartridgeContents.GAME_BOY_ADVANCE).register();
    /**
     * The Pixelon 64 (D189): Nintendo 64 games, four ports. Its savestates are too big to roll back, so it runs in
     * lockstep (only frames whose inputs are all in), and they travel deflated.
     */
    public static final ConsoleKind PIXELON_64 = builder("pixelon_64", CoreSpec.MUPEN64PLUS_NEXT)
            .systems(CartridgeContents.N64).ports(4).netplay(Netplay.LOCKSTEP).deflateStates().register();

    private final String id;
    private final CoreSpec core;
    private final Predicate<String> takes;
    private final Map<String, String> options;
    private final int ports;
    private final Netplay netplay;
    private final boolean deflatesStates;
    private final Checksum checksum;
    private final Function<String, String> gameFileName;
    private final boolean followsPictureShape;
    private final int bootFrames;
    @Nullable
    private final Predicate<byte[]> gameCheck;
    @Nullable
    private final InputMap inputMap;

    private ConsoleKind(Builder b) {
        this.id = b.id;
        this.core = b.core;
        this.takes = b.takes;
        Map<String, String> all = new HashMap<>(b.core.options());
        all.putAll(b.options);
        this.options = Map.copyOf(all);
        this.ports = b.ports;
        this.netplay = b.netplay;
        this.deflatesStates = b.deflatesStates;
        this.checksum = b.checksum;
        this.gameFileName = b.gameFileName;
        this.followsPictureShape = b.followsPictureShape;
        this.bootFrames = b.bootFrames;
        this.gameCheck = b.gameCheck;
        this.inputMap = b.inputMap;
    }

    /** Starts a kind with this id (unique, lower case) running this core. */
    public static Builder builder(String id, CoreSpec core) {
        return new Builder(id, core);
    }

    /** Every kind, Cartridge's own first, then the add-ons' in the order they registered. */
    public static List<ConsoleKind> values() {
        return Collections.unmodifiableList(ALL);
    }

    public static Optional<ConsoleKind> byId(String id) {
        for (ConsoleKind kind : ALL) {
            if (kind.id.equals(id)) return Optional.of(kind);
        }
        return Optional.empty();
    }

    public String id() {
        return id;
    }

    public CoreSpec core() {
        return core;
    }

    /** The core options this console runs with, the same for every player: the core's own plus the model's. */
    public Map<String, String> options() {
        return options;
    }

    /** Controller ports: four on the Pixelon 64, two on the rest (a handheld plays on the first). */
    public int ports() {
        return ports;
    }

    /** How its networked game runs; {@link Netplay#BY_SPEED} is decided by each client for itself (D196). */
    public Netplay netplay() {
        return netplay;
    }

    /**
     * Lockstep netplay (D189): every frame waits for everyone's inputs, with an input delay covering the round trip,
     * instead of guessing and rolling back. Only for a kind that always does; {@link Netplay#BY_SPEED} is measured.
     */
    public boolean lockstep() {
        return netplay == Netplay.LOCKSTEP;
    }

    /** Savestates are deflated wherever they leave the host: the network, Memory Cards, resume files (D189). */
    public boolean deflatesStates() {
        return deflatesStates;
    }

    public Checksum checksum() {
        return checksum;
    }

    /** The file name the core gets the game under: an extension it knows, or the game's own name (MAME's romsets). */
    public String gameFileName(String system) {
        return gameFileName.apply(system);
    }

    /**
     * The picture's shape on a TV or the big view (D65): 4:3, or for a console whose games stand upright or lie on
     * their side (an arcade, D198), 3:4 when the picture is taller than wide.
     */
    public double pictureAspect(int width, int height) {
        return followsPictureShape && height > width ? 3.0 / 4.0 : 4.0 / 3.0;
    }

    /**
     * Frames run silently with no input right after the game loads, before the power-on savestate: a core that
     * finishes starting up on its first frames (MAME) must have, or a savestate loaded into it plays differently.
     */
    public int bootFrames() {
        return bootFrames;
    }

    public boolean takes(String system) {
        return takes.test(system);
    }

    /**
     * For a kind whose games have no header to find (an arcade romset's zip, D197): whether an uploaded file can be
     * one of its games, which the server checks instead of {@link RomFormats#detect}. Null: games are found by header.
     */
    @Nullable
    public Predicate<byte[]> gameCheck() {
        return gameCheck;
    }

    /**
     * Changes the inputs of {@code frames} frames, {@link #ports()} per frame, in place, just before the core gets them:
     * after netplay, so every player's PC changes the same inputs the same way (D213).
     */
    public void mapInputs(int[] inputs, int frames) {
        if (inputMap == null) return;
        for (int f = 0; f < frames; f++) inputMap.map(inputs, f * ports, ports);
    }

    /** One frame's inputs changed in place: {@code ports} of them from {@code offset}. */
    @FunctionalInterface
    public interface InputMap {
        void map(int[] inputs, int offset, int ports);
    }

    /** The console a kind of game runs on: the first registered that takes it. */
    public static Optional<ConsoleKind> forSystem(String system) {
        for (ConsoleKind kind : ALL) {
            if (kind.takes(system)) return Optional.of(kind);
        }
        return Optional.empty();
    }

    @Override
    public String toString() {
        return id;
    }

    public static final class Builder {
        private final String id;
        private final CoreSpec core;
        private Predicate<String> takes = s -> false;
        private final Map<String, String> options = new HashMap<>();
        private int ports = 2;
        private Netplay netplay = Netplay.ROLLBACK;
        private boolean deflatesStates;
        private Checksum checksum = Checksum.RAM;
        private Function<String, String> gameFileName = RomFormats::gameFileName;
        private boolean followsPictureShape;
        private int bootFrames;
        private Predicate<byte[]> gameCheck;
        private InputMap inputMap;

        private Builder(String id, CoreSpec core) {
            this.id = Objects.requireNonNull(id);
            this.core = Objects.requireNonNull(core);
        }

        /** The kinds of game it takes, by {@link CartridgeContents#system()}. */
        public Builder systems(String... systems) {
            List<String> list = List.of(systems);
            takes = list::contains;
            return this;
        }

        /** The kinds of game it takes, by a test (an add-on's systems that carry the game's name, "mame/sf2"). */
        public Builder systems(Predicate<String> test) {
            takes = Objects.requireNonNull(test);
            return this;
        }

        /** A core option on top of the core's own. */
        public Builder option(String key, String value) {
            options.put(key, value);
            return this;
        }

        public Builder ports(int ports) {
            if (ports < 1 || ports > MAX_PORTS) throw new IllegalArgumentException("1 to " + MAX_PORTS + " ports");
            this.ports = ports;
            return this;
        }

        public Builder netplay(Netplay netplay) {
            this.netplay = Objects.requireNonNull(netplay);
            return this;
        }

        public Builder deflateStates() {
            deflatesStates = true;
            return this;
        }

        public Builder checksum(Checksum checksum) {
            this.checksum = Objects.requireNonNull(checksum);
            return this;
        }

        /** The file name the core gets each game under, from its system. */
        public Builder gameFileName(Function<String, String> name) {
            gameFileName = Objects.requireNonNull(name);
            return this;
        }

        /** Shows a picture taller than wide at 3:4 (see {@link ConsoleKind#pictureAspect}). */
        public Builder followPictureShape() {
            followsPictureShape = true;
            return this;
        }

        /** See {@link ConsoleKind#gameCheck()}. */
        public Builder gameCheck(Predicate<byte[]> check) {
            gameCheck = Objects.requireNonNull(check);
            return this;
        }

        /** See {@link ConsoleKind#mapInputs}. */
        public Builder inputMap(InputMap map) {
            inputMap = Objects.requireNonNull(map);
            return this;
        }

        /** See {@link ConsoleKind#bootFrames()}. */
        public Builder bootFrames(int frames) {
            if (frames < 0) throw new IllegalArgumentException("frames");
            bootFrames = frames;
            return this;
        }

        public ConsoleKind register() {
            synchronized (ALL) {
                if (byId(id).isPresent()) throw new IllegalStateException("console kind " + id + " is already registered");
                ConsoleKind kind = new ConsoleKind(this);
                ALL.add(kind);
                return kind;
            }
        }
    }
}
