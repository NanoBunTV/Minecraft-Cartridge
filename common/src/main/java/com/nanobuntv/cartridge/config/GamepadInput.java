package com.nanobuntv.cartridge.config;

/**
 * A gamepad button, trigger or stick direction a console button can be mapped to (D118, D119, D191), in GLFW's
 * standard gamepad layout (the SDL controller database maps every known pad onto it). Face buttons are named by
 * position, since their letters differ between Xbox, PlayStation and Nintendo pads. The indices are GLFW's
 * {@code GLFW_GAMEPAD_BUTTON_*} and {@code GLFW_GAMEPAD_AXIS_*} values, kept as numbers so this class loads on servers,
 * which have no GLFW. The config screen shows the constant names (common code can't reach the loaders'
 * TranslatableEnum).
 */
public enum GamepadInput {
    NONE(-1, -1, 0),
    SOUTH(0, -1, 0),
    EAST(1, -1, 0),
    WEST(2, -1, 0),
    NORTH(3, -1, 0),
    LEFT_BUMPER(4, -1, 0),
    RIGHT_BUMPER(5, -1, 0),
    BACK(6, -1, 0),
    START(7, -1, 0),
    GUIDE(8, -1, 0),
    LEFT_STICK(9, -1, 0),
    RIGHT_STICK(10, -1, 0),
    DPAD_UP(11, -1, 0),
    DPAD_RIGHT(12, -1, 0),
    DPAD_DOWN(13, -1, 0),
    DPAD_LEFT(14, -1, 0),
    LEFT_TRIGGER(-1, 4, 0),
    RIGHT_TRIGGER(-1, 5, 0),
    // A stick pushed one way (D191): the Pixelon 64's C buttons are on the right stick by default.
    LEFT_STICK_UP(-1, 1, -1),
    LEFT_STICK_DOWN(-1, 1, 1),
    LEFT_STICK_LEFT(-1, 0, -1),
    LEFT_STICK_RIGHT(-1, 0, 1),
    RIGHT_STICK_UP(-1, 3, -1),
    RIGHT_STICK_DOWN(-1, 3, 1),
    RIGHT_STICK_LEFT(-1, 2, -1),
    RIGHT_STICK_RIGHT(-1, 2, 1);

    /** GLFW gamepad button index, or -1. */
    public final int button;
    /** GLFW gamepad axis index (a trigger or a stick's), or -1. */
    public final int axis;
    /** For a stick: which way along {@link #axis} (-1 or 1); 0 for a trigger, which rests at -1. */
    public final int direction;

    GamepadInput(int button, int axis, int direction) {
        this.button = button;
        this.axis = axis;
        this.direction = direction;
    }
}
