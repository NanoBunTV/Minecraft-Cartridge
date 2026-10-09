package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nanobuntv.cartridge.emu.SharedMemory;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
//? if >=1.21.11 && <26.1 {
/*import net.minecraft.world.level.gamerules.GameRules;
*///?} else if <1.21.11 {
import net.minecraft.world.level.GameRules;
//?}
import org.jetbrains.annotations.Nullable;

/**
 * Dev runs only: what an add-on's self-test needs of Cartridge's client (Cartridge: Arcade's, D195), as
 * {@link DevSelfTest} uses it: the open Writer, a console's view and its picture, and play mode's keys.
 */
public final class DevHooks {
    private DevHooks() {}

    /** A screenshot of the game window, {@code screenshots/<fileName>}. */
    public static void screenshot(String fileName) {
        Minecraft mc = Minecraft.getInstance();
        //? if >=26.2 {
        /*Screenshot.grab(mc.gameDirectory, fileName, mc.gameRenderer.mainRenderTarget(), 1, message -> {});
        *///?} else if >=1.21.9 {
        /*Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(), 1, message -> {});
        *///?} else {
        Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(), message -> {});
        //?}
    }

    /** Opens the self-test's world, a creative flat world it creates the first time. */
    public static void openTestWorld(Minecraft mc, String world, Runnable onFail) {
        java.nio.file.Path dir = mc.getLevelSource().getBaseDir().resolve(world);
        if (mc.getLevelSource().levelExists(world) && !java.nio.file.Files.exists(dir.resolve("level.dat"))) {
            // A run that died while creating it left a folder without a world: start again.
            try (var files = java.nio.file.Files.walk(dir)) {
                files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            } catch (java.io.IOException e) {
                onFail.run();
                return;
            }
        }
        if (mc.getLevelSource().levelExists(world)) {
            //? if >=1.20.5 {
            mc.createWorldOpenFlows().openWorld(world, onFail);
            //?} else {
            /*mc.createWorldOpenFlows().loadLevel(mc.screen, world);
            *///?}
            return;
        }
        //? if >=26.1 {
        /*LevelSettings settings = new LevelSettings(world, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        *///?} else if >=1.21.11 {
        /*LevelSettings settings = new LevelSettings(world, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(WorldDataConfiguration.DEFAULT.enabledFeatures()), WorldDataConfiguration.DEFAULT);
        *///?} else {
        LevelSettings settings = new LevelSettings(world, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
                WorldDataConfiguration.DEFAULT);
        //?}
        mc.createWorldOpenFlows().createFreshLevel(world, settings,
                new WorldOptions(1L, false, false),
                //? if >=1.21.2 {
                /*registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions()
                *///?} else {
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions()
                //?}
                //? if >=1.20.5 {
                , mc.screen
                //?}
        );
    }

    @Nullable
    private static WriterScreen writer() {
        return Minecraft.getInstance().screen instanceof WriterScreen writer ? writer : null;
    }

    /** Picks a game file in the open Writer, as dropping it on the window does. False if no Writer is open. */
    public static boolean writerPick(Path file) {
        WriterScreen writer = writer();
        if (writer != null) writer.pickGame(file);
        return writer != null;
    }

    /** A further file the Writer asked for (an arcade romset's parent or BIOS, D206). */
    public static boolean writerPickExtra(Path file) {
        WriterScreen writer = writer();
        if (writer != null) writer.pickExtra(file);
        return writer != null;
    }

    public static boolean writerBurn() {
        WriterScreen writer = writer();
        if (writer != null) writer.burn();
        return writer != null;
    }

    /** Checking or uploading a file. */
    public static boolean writerBusy() {
        WriterScreen writer = writer();
        return writer != null && (writer.preparing() || writer.uploading());
    }

    public static String writerStatus() {
        WriterScreen writer = writer();
        return writer == null ? "" : writer.status().getString();
    }

    /** The console's view state on this client (OFF, STARTING, PLAYING, ...), or "NONE". */
    public static String consoleState(BlockPos console) {
        ConsoleView view = ClientConsoles.view(console);
        return view == null ? "NONE" : view.state().name();
    }

    /** The console's picture's width and height now, or zeros. */
    public static int[] frameSize(BlockPos console) {
        ConsoleView view = ClientConsoles.view(console);
        SharedMemory shm = view == null || view.instance() == null ? null : view.instance().sharedMemory();
        var f = shm == null ? null : shm.readFrame(ByteBuffer.allocateDirect(640 * 480 * 4));
        return f == null ? new int[2] : new int[] {f.width(), f.height()};
    }

    public static boolean playing(BlockPos console) {
        return PlayMode.isPlaying(console);
    }

    /** A key going down (or up) in play mode, as the keyboard sends it. */
    public static void key(int glfwKey, boolean down) {
        PlayMode.onKey(glfwKey, 0, down ? InputConstants.PRESS : InputConstants.RELEASE);
    }
}
