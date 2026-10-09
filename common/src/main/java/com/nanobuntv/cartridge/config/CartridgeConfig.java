package com.nanobuntv.cartridge.config;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.platform.Services;
import java.nio.file.Path;

/**
 * Settings (D122), the same JSON5 files on every loader ({@link SettingsFile}):
 * <ul>
 *   <li>{@link #server()}: {@code config/cartridge_mod-server.json5}, this side's server settings (the dedicated
 *       server's, or singleplayer's integrated one). Server code reads these.</li>
 *   <li>{@link #synced()}: the settings of the server this client is connected to, as it sent them on join (D123,
 *       net/ServerSettingsPayload); defaults until they arrive. Client code reads these.</li>
 *   <li>{@link #client()}: {@code config/cartridge_mod-client.json5}, this player's own settings, clients only.</li>
 * </ul>
 * The screen is client/CartridgeConfigScreen. Values are plain fields; after changing them, {@link #saveServer} or
 * {@link #saveClient}.
 */
public final class CartridgeConfig {
    private static final SettingsFile<ServerSettings> SERVER = new SettingsFile<>(
            configDir().resolve(Cartridge.MOD_ID + "-server.json5"), ServerSettings.class, ServerSettings::new);
    private static final SettingsFile<ClientSettings> CLIENT = new SettingsFile<>(
            configDir().resolve(Cartridge.MOD_ID + "-client.json5"), ClientSettings.class, ClientSettings::new);

    private static volatile ServerSettings synced = new ServerSettings();
    private static boolean clientLoaded;

    private CartridgeConfig() {}

    /** Common init: the server settings, on both sides (singleplayer runs a server too). Writes the file if missing. */
    public static void loadServer() {
        SERVER.load();
        SERVER.instance().clamp();
        SERVER.save(); // fills in new keys and comments
    }

    /** Client init. */
    public static void loadClient() {
        CLIENT.load();
        CLIENT.instance().clean();
        CLIENT.save();
        clientLoaded = true;
    }

    public static ServerSettings server() {
        return SERVER.instance();
    }

    public static ServerSettings serverDefaults() {
        return SERVER.defaults();
    }

    public static void saveServer() {
        SERVER.instance().clamp();
        SERVER.save();
    }

    public static ServerSettings synced() {
        return synced;
    }

    /** Client: the connected server's settings arrived (D123), or (null) the connection ended. */
    public static void setSynced(ServerSettings settings) {
        synced = settings != null ? settings.clamp() : new ServerSettings();
    }

    public static ClientSettings client() {
        if (!clientLoaded) loadClient();
        return CLIENT.instance();
    }

    public static ClientSettings clientDefaults() {
        return CLIENT.defaults();
    }

    public static void saveClient() {
        CLIENT.instance().clean();
        CLIENT.save();
    }

    private static Path configDir() {
        return Services.PLATFORM.gameDir().resolve("config");
    }
}
