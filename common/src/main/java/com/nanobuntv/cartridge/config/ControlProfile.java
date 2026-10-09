package com.nanobuntv.cartridge.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One {@link ControlSet}'s bindings in the client config (D191): a key and a gamepad input for each of its buttons, by
 * the button's name. A hand-edited file may leave buttons out (they get their defaults) or name a gamepad input that
 * doesn't exist (it reads as none).
 */
public final class ControlProfile {
    public Map<String, String> keys = new LinkedHashMap<>();
    public Map<String, GamepadInput> pad = new LinkedHashMap<>();
    /** The left stick also works as the D-pad (D120), where the set allows it. */
    public boolean leftStickAsDpad = true;

    public String key(String button) {
        return keys.getOrDefault(button, ControlSet.NO_KEY);
    }

    public GamepadInput pad(String button) {
        return pad.getOrDefault(button, GamepadInput.NONE);
    }

    /** Fills in what's missing: a button left out gets its default, a broken value nothing. */
    ControlProfile fill(ControlSet set) {
        if (keys == null) keys = new LinkedHashMap<>();
        if (pad == null) pad = new LinkedHashMap<>();
        Map<String, String> keysInOrder = new LinkedHashMap<>();
        Map<String, GamepadInput> padInOrder = new LinkedHashMap<>();
        for (ControlSet.Button button : set.buttons()) {
            String key = keys.containsKey(button.name()) ? keys.get(button.name()) : button.key();
            keysInOrder.put(button.name(), key == null || key.isBlank() ? ControlSet.NO_KEY : key);
            if (button.pad() == null) continue;
            GamepadInput input = pad.containsKey(button.name()) ? pad.get(button.name()) : button.pad();
            padInOrder.put(button.name(), input == null ? GamepadInput.NONE : input);
        }
        keys = keysInOrder;
        pad = padInOrder;
        return this;
    }
}
