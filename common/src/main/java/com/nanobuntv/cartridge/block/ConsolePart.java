package com.nanobuntv.cartridge.block;

/**
 * The clickable parts of a console (D28, D62): where each one sits is the console's {@link ConsoleLayout}. BODY is
 * anywhere else.
 */
public enum ConsolePart {
    POWER, RESET, PORT_1, PORT_2, PORT_3, PORT_4, BODY;

    /** The port index (0-3; the Pixelon 64 has four, D189), or -1 for parts that aren't ports. */
    public int port() {
        return switch (this) {
            case PORT_1 -> 0;
            case PORT_2 -> 1;
            case PORT_3 -> 2;
            case PORT_4 -> 3;
            default -> -1;
        };
    }
}
