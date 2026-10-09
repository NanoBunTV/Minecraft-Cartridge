package com.nanobuntv.cartridge.emu;

import java.util.Locale;
import java.util.Optional;

/** The platforms cartridge-host and the cores exist for (D5): Windows x64 and Linux x64. */
public enum HostPlatform {
    WINDOWS_X86_64("windows-x86_64", "cartridge-host.exe", ".dll"),
    LINUX_X86_64("linux-x86_64", "cartridge-host", ".so");

    /** Folder name, as in the jar's natives/ and cores/ and the game dir's cartridge_mod/ folders. */
    public final String id;
    public final String hostFileName;
    /** Shared-library suffix of a libretro core. */
    public final String librarySuffix;

    HostPlatform(String id, String hostFileName, String librarySuffix) {
        this.id = id;
        this.hostFileName = hostFileName;
        this.librarySuffix = librarySuffix;
    }

    /** This machine's platform, or empty on an unsupported OS or CPU: the TV then says so instead of starting (D5). */
    public static Optional<HostPlatform> current() {
        return detect(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
    }

    static Optional<HostPlatform> detect(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String arch = osArch.toLowerCase(Locale.ROOT);
        if (!arch.equals("amd64") && !arch.equals("x86_64")) return Optional.empty();
        if (os.startsWith("windows")) return Optional.of(WINDOWS_X86_64);
        if (os.startsWith("linux")) return Optional.of(LINUX_X86_64);
        return Optional.empty();
    }

    /** The text the TV shows on an unsupported platform (D5). */
    public static String unsupportedMessage() {
        return "Unsupported operating system: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch")
                + ")";
    }
}
