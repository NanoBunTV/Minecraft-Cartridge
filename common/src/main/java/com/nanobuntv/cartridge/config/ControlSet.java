package com.nanobuntv.cartridge.config;

import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.emu.N64Pad;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Each console family's own controls (D191): its buttons, each with a key and a gamepad input, set apart in the client
 * config ({@link ClientSettings#controls}) so a player sets up only the consoles they play, each its own way. The
 * Pocketron and the Pocketron Color share one set (the same buttons), as do the Pocketron Advance and its SP; the
 * Pocketron Adapter plays on the Super Pixelon's. Keys are named as Minecraft names them ({@code key.keyboard.space},
 * {@code key.mouse.left}). The defaults are the old shared ones (D22, D119, D145, D146, D152, D190).
 * <p>
 * Cartridge's own are the constants here; an add-on registers more with {@link #register} (D195), on the client and
 * the server alike and in the same order, as the console menu sends a set's {@link #index()}. Each gets its own group
 * in the Controls tab, named by {@code cartridge_mod.controls.<id>} and its buttons by
 * {@code cartridge_mod.controls.button.<name>}.
 */
public final class ControlSet {
    private static final List<ControlSet> ALL = new ArrayList<>();
    private static final Map<ConsoleKind, ControlSet> BY_KIND = new IdentityHashMap<>();

    public static final ControlSet PIXELON = register(new ControlSet("pixelon", true, buttons(dpad(), List.of(
            joy("a", "space", GamepadInput.EAST, 8),
            joy("b", "left.shift", GamepadInput.SOUTH, 0),
            joy("select", "right.shift", GamepadInput.BACK, 2),
            joy("start", "enter", GamepadInput.START, 3),
            new Button("fire", "key.mouse.left", GamepadInput.RIGHT_TRIGGER, 0)))), ConsoleKind.PIXELON);
    public static final ControlSet SUPER_PIXELON = register(new ControlSet("super_pixelon", true, buttons(dpad(), List.of(
            joy("a", "space", GamepadInput.EAST, 8),
            joy("b", "left.shift", GamepadInput.SOUTH, 0),
            joy("x", "left.alt", GamepadInput.NORTH, 9),
            joy("y", "left.control", GamepadInput.WEST, 1),
            joy("l", "q", GamepadInput.LEFT_BUMPER, 10),
            joy("r", "e", GamepadInput.RIGHT_BUMPER, 11),
            joy("select", "right.shift", GamepadInput.BACK, 2),
            joy("start", "enter", GamepadInput.START, 3)))), ConsoleKind.SUPER_PIXELON, ConsoleKind.POCKETRON_ADAPTER);
    /** Genesis Plus GX's RetroPad layout for the 6-button pad (D152). */
    public static final ControlSet VORTEX_DRIVE = register(new ControlSet("vortex_drive", true, buttons(dpad(), List.of(
            joy("a", "left.control", GamepadInput.WEST, 1),
            joy("b", "left.shift", GamepadInput.SOUTH, 0),
            joy("c", "space", GamepadInput.EAST, 8),
            joy("x", "q", GamepadInput.LEFT_BUMPER, 10),
            joy("y", "left.alt", GamepadInput.NORTH, 9),
            joy("z", "e", GamepadInput.RIGHT_BUMPER, 11),
            joy("mode", "right.shift", GamepadInput.BACK, 2),
            joy("start", "enter", GamepadInput.START, 3)))), ConsoleKind.VORTEX_DRIVE);
    /**
     * The Pixelon 64 Pad (D190): the keys tilt the stick all the way; a gamepad's left stick is always the analog stick,
     * so the stick's rows have no gamepad input. The bits are {@link N64Pad}'s.
     */
    public static final ControlSet PIXELON_64 = register(new ControlSet("pixelon_64", false, List.of(
            new Button("stick_up", key("w"), null, 0),
            new Button("stick_down", key("s"), null, 0),
            new Button("stick_left", key("a"), null, 0),
            new Button("stick_right", key("d"), null, 0),
            n64("a", "space", GamepadInput.SOUTH, N64Pad.A),
            n64("b", "left.shift", GamepadInput.WEST, N64Pad.B),
            n64("z", "left.control", GamepadInput.LEFT_TRIGGER, N64Pad.Z),
            n64("l", "q", GamepadInput.LEFT_BUMPER, N64Pad.L),
            n64("r", "e", GamepadInput.RIGHT_BUMPER, N64Pad.R),
            n64("start", "enter", GamepadInput.START, N64Pad.START),
            n64("c_up", "up", GamepadInput.RIGHT_STICK_UP, N64Pad.C_UP),
            n64("c_down", "down", GamepadInput.RIGHT_STICK_DOWN, N64Pad.C_DOWN),
            n64("c_left", "left", GamepadInput.RIGHT_STICK_LEFT, N64Pad.C_LEFT),
            n64("c_right", "right", GamepadInput.RIGHT_STICK_RIGHT, N64Pad.C_RIGHT),
            n64("dpad_up", "i", GamepadInput.DPAD_UP, N64Pad.UP),
            n64("dpad_down", "k", GamepadInput.DPAD_DOWN, N64Pad.DOWN),
            n64("dpad_left", "j", GamepadInput.DPAD_LEFT, N64Pad.LEFT),
            n64("dpad_right", "l", GamepadInput.DPAD_RIGHT, N64Pad.RIGHT))), ConsoleKind.PIXELON_64);
    public static final ControlSet POCKETRON = register(new ControlSet("pocketron", true, buttons(dpad(), List.of(
            joy("a", "space", GamepadInput.EAST, 8),
            joy("b", "left.shift", GamepadInput.SOUTH, 0),
            joy("select", "right.shift", GamepadInput.BACK, 2),
            joy("start", "enter", GamepadInput.START, 3)))),
            ConsoleKind.POCKETRON, ConsoleKind.POCKETRON_COLOR, ConsoleKind.POCKETRON_LINK);
    public static final ControlSet POCKETRON_ADVANCE = register(new ControlSet("pocketron_advance", true, buttons(dpad(), List.of(
            joy("a", "space", GamepadInput.EAST, 8),
            joy("b", "left.shift", GamepadInput.SOUTH, 0),
            joy("l", "q", GamepadInput.LEFT_BUMPER, 10),
            joy("r", "e", GamepadInput.RIGHT_BUMPER, 11),
            joy("select", "right.shift", GamepadInput.BACK, 2),
            joy("start", "enter", GamepadInput.START, 3)))), ConsoleKind.POCKETRON_ADVANCE);

    /** The Pixelon Blaster's trigger (D99), in the Pixelon Entertainment System's set: no bit, the gun encodes it. */
    public static final String FIRE = "fire";
    /** The Pixelon 64's stick on the keyboard: no bits, the stick's position. */
    public static final String STICK_UP = "stick_up", STICK_DOWN = "stick_down", STICK_LEFT = "stick_left",
            STICK_RIGHT = "stick_right";
    /** A key name for no key. */
    public static final String NO_KEY = "key.keyboard.unknown";

    /**
     * A console button: its name (the lang key's last part and the config's map key), its default key and gamepad
     * input ({@code pad} null: no gamepad input can be set), and the input bits it sets while held.
     */
    public record Button(String name, String key, GamepadInput pad, int bits) {}

    public final String id;
    /** The gamepad's left stick can also work as the D-pad (D120); not where it's an analog stick. */
    public final boolean stickAsDpad;
    private final List<Button> buttons;

    private int index = -1;

    /** A set with this id (unique, lower case) and these buttons, in the order the Controls tab lists them. */
    public ControlSet(String id, boolean stickAsDpad, List<Button> buttons) {
        this.id = Objects.requireNonNull(id);
        this.stickAsDpad = stickAsDpad;
        this.buttons = List.copyOf(buttons);
    }

    /** Adds a set, played on these consoles; returns it. */
    public static ControlSet register(ControlSet set, ConsoleKind... kinds) {
        synchronized (ALL) {
            for (ControlSet other : ALL) {
                if (other.id.equals(set.id)) throw new IllegalStateException("control set " + set.id + " is already registered");
            }
            for (ConsoleKind kind : kinds) {
                if (BY_KIND.putIfAbsent(kind, set) != null) throw new IllegalStateException(kind + " already has controls");
            }
            set.index = ALL.size();
            ALL.add(set);
            return set;
        }
    }

    /** Every set, Cartridge's own first. */
    public static List<ControlSet> values() {
        return Collections.unmodifiableList(ALL);
    }

    /** Its place in {@link #values()}, the same on the server and the client. */
    public int index() {
        return index;
    }

    /** The set at an {@link #index()}, or the Pixelon's for one this side doesn't know. */
    public static ControlSet byIndex(int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : PIXELON;
    }

    public String id() {
        return id;
    }

    public List<Button> buttons() {
        return buttons;
    }

    /** The set a console or handheld plays with: the Pocketron Adapter is played on the Super Pixelon's pads. */
    public static ControlSet of(ConsoleKind kind) {
        synchronized (ALL) {
            return BY_KIND.getOrDefault(kind, PIXELON);
        }
    }

    /** The defaults, as a fresh profile. */
    public ControlProfile defaults() {
        ControlProfile profile = new ControlProfile();
        profile.fill(this);
        return profile;
    }

    /** The D-pad on WASD and the gamepad's D-pad, as RetroPad buttons. */
    public static List<Button> dpad() {
        return List.of(joy("up", "w", GamepadInput.DPAD_UP, 4), joy("down", "s", GamepadInput.DPAD_DOWN, 5),
                joy("left", "a", GamepadInput.DPAD_LEFT, 6), joy("right", "d", GamepadInput.DPAD_RIGHT, 7));
    }

    public static List<Button> buttons(List<Button> first, List<Button> rest) {
        List<Button> all = new ArrayList<>(first);
        all.addAll(rest);
        return List.copyOf(all);
    }

    /** A RetroPad button: libretro's joypad id (RETRO_DEVICE_ID_JOYPAD_*). */
    public static Button joy(String name, String key, GamepadInput pad, int id) {
        return new Button(name, key(key), pad, 1 << id);
    }

    private static Button n64(String name, String key, GamepadInput pad, int bit) {
        return new Button(name, key(key), pad, bit);
    }

    /** A keyboard key by Minecraft's short name ("space", "left.shift"). */
    public static String key(String name) {
        return "key.keyboard." + name;
    }
}
