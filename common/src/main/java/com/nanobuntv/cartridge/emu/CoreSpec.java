package com.nanobuntv.cartridge.emu;

import java.util.Map;

/**
 * A bundled emulator core (D114, D137): its libretro name, its identity, and the options it always runs with.
 * {@code version} is compared between players and stored with savestates (D58), so it names the exact upstream commit;
 * {@code marker} is how that commit shows inside the library (NativesTest checks both platforms carry it). Change both
 * with the files in {@code cores/<name>}.
 *
 * @param options core options set before every game, the same for everyone (netplay needs that; D127)
 * @param jar a class from the jar that carries the core's files ({@link Natives}): Cartridge's own, or an add-on's (D195)
 */
public record CoreSpec(String name, String version, String marker, Map<String, String> options, Class<?> jar) {
    /** A core in Cartridge's own jar. */
    public CoreSpec(String name, String version, String marker, Map<String, String> options) {
        this(name, version, marker, options, CoreSpec.class);
    }

    /** The Pixelon Entertainment System's (D10, D114). The light gun is aimed by coordinates, no crosshair drawn (D99). */
    public static final CoreSpec FCEUMM = new CoreSpec("fceumm", "fceumm 7a542da", "(SVN) 7a542da",
            Map.of("fceumm_zapper_mode", "clightgun", "fceumm_show_crosshair", "disabled"));
    /** The Super Pixelon's (D137). Non-commercial license: Cartridge is never monetized (D138). */
    public static final CoreSpec SNES9X = new CoreSpec("snes9x", "snes9x fae2fea", "1.63 fae2fea", Map.of());
    /**
     * The Vortex Drive's (D151). Non-commercial license, like Snes9x's (D138). Its own BIOS boot stays off: Genesis
     * cartridges run without one.
     */
    public static final CoreSpec GENESIS_PLUS_GX = new CoreSpec("genesis_plus_gx", "genesis_plus_gx 58c3414",
            "v1.7.4 58c3414", Map.of("genesis_plus_gx_bios", "disabled"));
    /**
     * The Pocketrons' and the Pocketron Adapter's (D139, D173), MIT. The real-time clock runs on emulated time, never
     * the PC's (netplay: every player's clock must agree); the model comes from the {@link ConsoleKind}. No rumble, no
     * link cable (D161), the border only on the Super Game Boy (D167).
     */
    public static final CoreSpec SAMEBOY = new CoreSpec("sameboy", "sameboy 8230189", "1.0.3 8230189", Map.of(
            "sameboy_rtc", "accurate",
            "sameboy_border", "Super Game Boy only",
            "sameboy_rumble", "never",
            "sameboy_link", "disabled",
            "sameboy_audio_interference", "0",
            "sameboy_high_pass_filter_mode", "accurate"));
    /**
     * The Pocketron Advances' (D183), MPL 2.0. Its own built-in BIOS, never a real one (none is needed or downloaded);
     * no rumble, no solar sensor light, no frame skipping or blending, the same for everyone. Its real-time clock is
     * switched to emulated time after loading ({@link MgbaClock}); the core has no option for that.
     */
    public static final CoreSpec MGBA = new CoreSpec("mgba", "mgba 7a12d6d", "0.11-212-7a12d6d", Map.of(
            "mgba_use_bios", "OFF",
            "mgba_skip_bios", "OFF",
            "mgba_force_gbp", "OFF",
            "mgba_solar_sensor_level", "0",
            "mgba_frameskip", "disabled",
            "mgba_interframe_blending", "OFF",
            "mgba_color_correction", "OFF",
            "mgba_audio_low_pass_filter", "disabled",
            "mgba_idle_optimization", "Remove Known"));
    /**
     * The Pixelon 64's (D189), GPLv2. The host has no GPU, so the Angrylion software renderer on every CPU thread and
     * the ParaLLEl RSP; the dynamic recompiler. The alternate mapping puts every N64 button on a RetroPad button
     * ({@link N64Pad}); the left analog stick is the control stick, with no dead zone of the core's own. A Controller
     * Pak in every controller, the Expansion Pak in, every frame drawn, the same for everyone.
     */
    public static final CoreSpec MUPEN64PLUS_NEXT = new CoreSpec("mupen64plus_next", "mupen64plus_next 12edd2c",
            "3.0-Vulkan 12edd2c", Map.ofEntries(
                    Map.entry("mupen64plus-rdp-plugin", "angrylion"),
                    Map.entry("mupen64plus-rsp-plugin", "parallel"),
                    Map.entry("mupen64plus-cpucore", "dynamic_recompiler"),
                    Map.entry("mupen64plus-angrylion-multithread", "all threads"),
                    Map.entry("mupen64plus-angrylion-sync", "Low"),
                    Map.entry("mupen64plus-angrylion-vioverlay", "Filtered"),
                    Map.entry("mupen64plus-angrylion-overscan", "disabled"),
                    Map.entry("mupen64plus-alt-map", "True"),
                    Map.entry("mupen64plus-astick-deadzone", "0"),
                    Map.entry("mupen64plus-astick-sensitivity", "100"),
                    Map.entry("mupen64plus-pak1", "memory"),
                    Map.entry("mupen64plus-pak2", "memory"),
                    Map.entry("mupen64plus-pak3", "memory"),
                    Map.entry("mupen64plus-pak4", "memory"),
                    Map.entry("mupen64plus-ForceDisableExtraMem", "False"),
                    Map.entry("mupen64plus-IgnoreTLBExceptions", "False"),
                    Map.entry("mupen64plus-CountPerOp", "0"),
                    Map.entry("mupen64plus-CountPerOpDenomPot", "0"),
                    Map.entry("mupen64plus-virefresh", "Auto"),
                    Map.entry("mupen64plus-Framerate", "Original"),
                    Map.entry("mupen64plus-FrameDuping", "False"),
                    Map.entry("mupen64plus-aspect", "4:3")));

    public String fileName(HostPlatform platform) {
        return name + "_libretro" + platform.librarySuffix;
    }
}
