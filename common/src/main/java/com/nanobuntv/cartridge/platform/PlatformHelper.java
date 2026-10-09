package com.nanobuntv.cartridge.platform;

import java.nio.file.Path;
import com.mojang.brigadier.CommandDispatcher;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Facts about the running loader and environment. */
public interface PlatformHelper {
    /** "Fabric" or "NeoForge". */
    String loaderName();

    boolean isModLoaded(String modId);

    boolean isDevelopmentEnvironment();

    /** True on the physical client (including singleplayer's integrated server). */
    boolean isClient();

    Path gameDir();

    /** Runs {@code listener} on the server thread once a server (dedicated or integrated) has started. */
    void onServerStarted(Consumer<MinecraftServer> listener);

    /** Runs {@code listener} on the server thread when a player has joined (logged in). */
    void onPlayerJoin(Consumer<ServerPlayer> listener);

    /** Runs {@code listener} after a server (dedicated or integrated) has stopped, on its thread. */
    void onServerStopped(Runnable listener);

    /** Runs {@code listener} at the end of every server tick, on the server thread. */
    void onServerTick(Consumer<MinecraftServer> listener);

    /** Registers server commands, on every (re)load of them. */
    void onRegisterCommands(Consumer<CommandDispatcher<CommandSourceStack>> listener);
}
