package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.config.ClientSettings;
import com.nanobuntv.cartridge.config.ControlProfile;
import com.nanobuntv.cartridge.config.ControlSet;
import com.nanobuntv.cartridge.config.GamepadInput;
import java.util.EnumSet;
import org.jetbrains.annotations.Nullable;
//? if >=26.3 {
/*import java.nio.IntBuffer;
import org.lwjgl.sdl.SDLGamepad;
import org.lwjgl.sdl.SDLInit;
import org.lwjgl.sdl.SDLStdinc;
*///?} else {
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;
//?}

/**
 * Gamepads (D118), read straight from Minecraft's own LWJGL: GLFW's gamepads up to 26.2, SDL's from 26.3 (where
 * Minecraft moved to SDL). Both map Xbox, PlayStation, Switch Pro and most other pads onto one standard layout, read
 * here into GLFW's ({@link GamepadInput}'s indices). The first connected pad counts. Play mode polls it once per tick
 * ({@link #poll}); the console's buttons come from its family's mapping in the client config (D119, D191), and the
 * left stick can work as the D-pad (D120). Render thread only (the joystick calls belong on the main thread).
 */
final class Gamepads {
    /** How far a trigger or a stick must move to count as pressed. */
    private static final float TRIGGER_THRESHOLD = 0.5F, STICK_DEADZONE = 0.5F;
    /** The Pixelon 64's analog stick (D189): below this it rests; past it, the rest of the range is rescaled. */
    private static final float ANALOG_DEADZONE = 0.12F;
    private static final int UP = 1 << 4, DOWN = 1 << 5, LEFT = 1 << 6, RIGHT = 1 << 7;
    private static final int LEFT_X = 0, LEFT_Y = 1;

    /** The pad's buttons and axes in GLFW's layout: sticks -1..1 (y down), triggers -1 (at rest) to 1. */
    private static final boolean[] BUTTONS = new boolean[15];
    private static final float[] AXES = new float[6];
    private static boolean connected;
    private static boolean bigViewHeld, leaveHeld, bigViewPressed, leavePressed;

    private Gamepads() {}

    /** Reads the first connected gamepad; with none (or gamepads off), nothing is held. */
    static void poll() {
        if (!read()) {
            bigViewHeld = leaveHeld = bigViewPressed = leavePressed = false;
            return;
        }
        ClientSettings c = CartridgeConfig.client();
        boolean bigView = held(c.padBigView), leave = held(c.padStopPlaying);
        bigViewPressed = bigView && !bigViewHeld;
        leavePressed = leave && !leaveHeld;
        bigViewHeld = bigView;
        leaveHeld = leave;
    }

    /**
     * Reads the pad again between ticks (every frame, for the Pixelon 64's stick, D189): what's held, without the
     * presses {@link #poll} hands out once.
     */
    static void refresh() {
        read();
    }

    private static boolean read() {
        connected = enabled() && readPad();
        return connected;
    }

    /**
     * The bits a console family's buttons set (D191): libretro joypad bits, or the Pixelon 64's {@code N64Pad} bits,
     * as {@link ControlSet.Button#bits()}; the left stick adds the D-pad where the set and the player allow it (D120).
     */
    static int buttons(ControlSet set, ControlProfile profile) {
        if (!connected) return 0;
        int bits = 0;
        for (ControlSet.Button button : set.buttons()) {
            if (button.pad() != null && held(profile.pad(button.name()))) bits |= button.bits();
        }
        if (set.stickAsDpad && profile.leftStickAsDpad) {
            float x = AXES[LEFT_X], y = AXES[LEFT_Y];
            if (y < -STICK_DEADZONE) bits |= UP;
            if (y > STICK_DEADZONE) bits |= DOWN;
            if (x < -STICK_DEADZONE) bits |= LEFT;
            if (x > STICK_DEADZONE) bits |= RIGHT;
        }
        return bits;
    }

    /** The left stick for the Pixelon 64 (D189): x and y, -1..1 (y down), its dead zone taken out; 0 with no pad. */
    static float[] leftStick() {
        if (!connected) return new float[2];
        float x = AXES[LEFT_X], y = AXES[LEFT_Y];
        float r = (float) Math.sqrt(x * x + y * y);
        if (r <= ANALOG_DEADZONE) return new float[2];
        float scale = Math.min(1F, (r - ANALOG_DEADZONE) / (1 - ANALOG_DEADZONE)) / r;
        return new float[] {x * scale, y * scale};
    }

    /** A gamepad was connected (and gamepads on) at the last poll. */
    static boolean connected() {
        return connected;
    }

    /** The Pixelon Blaster's trigger (D99), from the Pixelon Entertainment System's mapping. */
    static boolean fire() {
        return connected && held(CartridgeConfig.client().pixelon.pad(ControlSet.FIRE));
    }

    /** Pressed on this tick's poll: toggle the big view, or stop playing. */
    static boolean bigViewPressed() {
        return bigViewPressed;
    }

    static boolean leavePressed() {
        return leavePressed;
    }

    /** Forgets held buttons, so a button already down when play mode starts doesn't count as a fresh press. */
    static void reset() {
        poll();
        bigViewPressed = leavePressed = false;
    }

    /** For the config screen: every input held on the first gamepad right now (gamepads on or off), or none. */
    static EnumSet<GamepadInput> heldInputs() {
        EnumSet<GamepadInput> held = EnumSet.noneOf(GamepadInput.class);
        if (!readPad()) return held;
        for (GamepadInput input : GamepadInput.values()) {
            if (held(input)) held.add(input);
        }
        return held;
    }

    private static boolean held(@Nullable GamepadInput input) {
        if (input == null || input == GamepadInput.NONE) return false;
        if (input.button >= 0) return BUTTONS[input.button];
        if (input.direction != 0) return AXES[input.axis] * input.direction > STICK_DEADZONE;
        return AXES[input.axis] > TRIGGER_THRESHOLD * 2 - 1; // triggers rest at -1
    }

    private static boolean enabled() {
        return CartridgeConfig.client().gamepadEnabled;
    }

    //? if >=26.3 {
    /*/^* GLFW's button order, as SDL's buttons. ^/
    private static final int[] SDL_BUTTONS = {
            SDLGamepad.SDL_GAMEPAD_BUTTON_SOUTH, SDLGamepad.SDL_GAMEPAD_BUTTON_EAST, SDLGamepad.SDL_GAMEPAD_BUTTON_WEST,
            SDLGamepad.SDL_GAMEPAD_BUTTON_NORTH, SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_SHOULDER,
            SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_SHOULDER, SDLGamepad.SDL_GAMEPAD_BUTTON_BACK,
            SDLGamepad.SDL_GAMEPAD_BUTTON_START, SDLGamepad.SDL_GAMEPAD_BUTTON_GUIDE,
            SDLGamepad.SDL_GAMEPAD_BUTTON_LEFT_STICK, SDLGamepad.SDL_GAMEPAD_BUTTON_RIGHT_STICK,
            SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_UP, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_RIGHT,
            SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_DOWN, SDLGamepad.SDL_GAMEPAD_BUTTON_DPAD_LEFT};
    private static long pad;

    /^* The first SDL gamepad, kept open; 0 with none. Minecraft starts SDL without its gamepads. ^/
    private static long sdlPad() {
        if ((SDLInit.SDL_WasInit(SDLInit.SDL_INIT_GAMEPAD) & SDLInit.SDL_INIT_GAMEPAD) == 0
                && !SDLInit.SDL_InitSubSystem(SDLInit.SDL_INIT_GAMEPAD)) {
            return 0;
        }
        if (pad != 0 && SDLGamepad.SDL_GamepadConnected(pad)) return pad;
        if (pad != 0) SDLGamepad.SDL_CloseGamepad(pad);
        pad = 0;
        IntBuffer ids = SDLGamepad.SDL_GetGamepads();
        if (ids == null) return 0;
        try {
            if (ids.remaining() > 0) pad = SDLGamepad.SDL_OpenGamepad(ids.get(0));
        } finally {
            SDLStdinc.SDL_free(ids);
        }
        return pad;
    }

    private static boolean readPad() {
        long p = sdlPad();
        if (p == 0) return false;
        for (int i = 0; i < SDL_BUTTONS.length; i++) BUTTONS[i] = SDLGamepad.SDL_GetGamepadButton(p, SDL_BUTTONS[i]);
        // The axes are in GLFW's order; SDL's are 16-bit, its triggers resting at 0.
        for (int i = 0; i < 4; i++) AXES[i] = Math.max(-1F, SDLGamepad.SDL_GetGamepadAxis(p, i) / 32767F);
        for (int i = 4; i < 6; i++) AXES[i] = SDLGamepad.SDL_GetGamepadAxis(p, i) / 32767F * 2 - 1;
        return true;
    }

    /^* The first gamepad's name, or null with none connected. ^/
    @Nullable
    static String name() {
        long p = sdlPad();
        return p == 0 ? null : SDLGamepad.SDL_GetGamepadName(p);
    }
    *///?} else {
    private static final GLFWGamepadState STATE = GLFWGamepadState.create();

    private static boolean readPad() {
        int jid = firstGamepad();
        if (jid < 0 || !GLFW.glfwGetGamepadState(jid, STATE)) return false;
        for (int i = 0; i < BUTTONS.length; i++) BUTTONS[i] = STATE.buttons(i) == GLFW.GLFW_PRESS;
        for (int i = 0; i < AXES.length; i++) AXES[i] = STATE.axes(i);
        return true;
    }

    /** The first gamepad's name, or null with none connected. */
    @Nullable
    static String name() {
        int jid = firstGamepad();
        return jid < 0 ? null : GLFW.glfwGetGamepadName(jid);
    }

    private static int firstGamepad() {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            if (GLFW.glfwJoystickIsGamepad(jid)) return jid;
        }
        return -1;
    }
    //?}
}
