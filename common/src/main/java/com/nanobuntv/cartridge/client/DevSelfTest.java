package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.block.CartridgeBlocks;
import com.nanobuntv.cartridge.block.Console8BitBlock;
import com.nanobuntv.cartridge.block.DisplayBlock;
import com.nanobuntv.cartridge.block.DisplayMount;
import com.nanobuntv.cartridge.block.FlatscreenTvBlock;
import com.nanobuntv.cartridge.block.Screen;
import com.nanobuntv.cartridge.blockentity.CartridgeWriterBlockEntity;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.blockentity.TvBlockEntity;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.emu.RomCache;
import com.nanobuntv.cartridge.emu.SharedMemory;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.CartridgeItem;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.HandheldItem;
import com.nanobuntv.cartridge.item.HandheldLink;
import com.nanobuntv.cartridge.item.HandheldState;
import com.nanobuntv.cartridge.item.Handhelds;
import com.nanobuntv.cartridge.item.PocketronAdapterItem;
import com.nanobuntv.cartridge.menu.WriterMenu;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.rom.GameBoyAdvanceTestRom;
import com.nanobuntv.cartridge.rom.GameBoyTestRom;
import com.nanobuntv.cartridge.rom.GenesisTestRom;
import com.nanobuntv.cartridge.rom.SnesTestRom;
import com.nanobuntv.cartridge.save.SaveServer;
import com.nanobuntv.cartridge.util.Compat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dev runs only: an end-to-end check of M3 and M4 without anyone at the keyboard. It runs when
 * {@code <run dir>/cartridge_dev/selftest} exists: creates (or opens) a flat creative world, builds a Cartridge Writer,
 * a console and a TV, burns {@code cartridge_dev/selftest.nes} through the Writer's real upload path, copies and
 * erases a cart, checks that a disk game asks for the BIOS, then links, plugs, inserts, powers on (downloading the ROM
 * again from the server) and takes a pad through the real click paths, presses Start, opens the big view, pulls the
 * cartridge mid-game and leaves play mode. It takes screenshots ({@code screenshots/selftest-*.png}), writes
 * {@code cartridge_dev/selftest-result.txt} and quits.
 */
final class DevSelfTest {
    private static final String WORLD = "cartridge-selftest";
    private static int step = -1;
    private static int wait;
    private static int waited;
    private static boolean sawLoading;
    private static final List<String> LOG = new ArrayList<>();
    private static BlockPos base, consolePos, tvPos, writerPos, largePos, wallPos, snesPos, snesTvPos, vortexPos, vortexTvPos,
            adapterPos, n64Pos, n64TvPos;
    /** The Game Boy game's cart, the handheld's id, and the Super Game Boy's light screen (D158, D166). */
    private static ItemStack gameBoyCart = ItemStack.EMPTY;
    private static ItemStack advanceCart;
    private static UUID handheldId;
    private static int light;
    private static String romSha1;
    private static UUID firstCartId;

    private DevSelfTest() {}

    private static Path dir() {
        return Services.PLATFORM.gameDir().resolve("cartridge_dev");
    }

    static void tick() {
        if (step == -2) return;
        Minecraft mc = Minecraft.getInstance();
        if (step == -1) {
            if (!Services.PLATFORM.isDevelopmentEnvironment() || !Files.exists(dir().resolve("selftest"))) {
                step = -2;
                return;
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            step = 0;
            log("self-test starting on " + Services.PLATFORM.loaderName());
            mc.options.pauseOnLostFocus = false; // the test window usually isn't focused
            DevHooks.openTestWorld(mc, WORLD, () -> fail("could not open world"));
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        try {
            run(mc);
        } catch (Exception e) {
            fail(e.toString());
            Cartridge.LOGGER.error("self-test failed", e);
        }
    }

    private static void run(Minecraft mc) throws Exception {
        MinecraftServer server = mc.getSingleplayerServer();
        switch (step) {
            case 0 -> { // wait for the world
                if (mc.player == null || server == null || mc.screen != null) return;
                wait = 40;
                step++;
            }
            case 1 -> {
                UUID id = mc.player.getUUID();
                romSha1 = RomCache.sha1(Files.readAllBytes(dir().resolve("selftest.nes")));
                server.execute(() -> build(server, id));
                wait = 20;
                step++;
            }
            case 2 -> { // the Writer is open with a blank in it: burn the ROM through the upload path
                if (!(mc.screen instanceof WriterScreen screen)) {
                    if (++waited > 100) fail("the Writer screen didn't open");
                    return;
                }
                waited = 0;
                check("Writer offers burning", screen.mode() == WriterScreen.Mode.BURN);
                screen.pickGame(dir().resolve("selftest.nes"));
                wait = 10;
                step++;
            }
            case 3 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                shot(mc, "1-writer-file");
                screen.burn();
                step++;
            }
            case 4 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                if (screen.uploading() || screen.mode() == WriterScreen.Mode.BURN) {
                    if (++waited > 200) fail("timed out burning: " + screen.status().getString());
                    return;
                }
                waited = 0;
                log("Writer says: " + screen.status().getString());
                check("burned onto the cart", screen.mode() == WriterScreen.Mode.ERASE);
                Path stored = server.getWorldPath(LevelResource.ROOT).resolve(Cartridge.MOD_ID).resolve("roms").resolve(romSha1);
                check("ROM stored on the server", Files.isRegularFile(stored));
                shot(mc, "2-writer-burned");
                server.execute(() -> {
                    CartridgeWriterBlockEntity writer = writer(server);
                    ItemStack burned = writer.removeItemNoUpdate(CartridgeWriterBlockEntity.MAIN);
                    CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(burned);
                    firstCartId = contents.cartId();
                    check("cart holds the ROM's hash", romSha1.equals(contents.romSha1()));
                    check("cart named after the file", "selftest".equals(contents.title()));
                    try { // a save on the original, which its copy mustn't get (D216)
                        SaveServer.store(server).writeSram(firstCartId, new byte[] {1, 2, 3});
                    } catch (IOException e) {
                        fail("couldn't write a save: " + e);
                    }
                    ItemStack adapter = new ItemStack(CartridgeItems.POCKETRON_ADAPTER.get());
                    check("the Writer doesn't take the Pocketron Adapter (D217)",
                            !writer.canPlaceItem(CartridgeWriterBlockEntity.MAIN, adapter)
                                    && !writer.canPlaceItem(CartridgeWriterBlockEntity.SOURCE, adapter));
                    writer.setItem(CartridgeWriterBlockEntity.SOURCE, burned);
                    writer.setItem(CartridgeWriterBlockEntity.MAIN, new ItemStack(CartridgeItems.BLANK_CARTRIDGE.get()));
                });
                wait = 10;
                step++;
            }
            case 5 -> { // copy (D73)
                WriterScreen screen = (WriterScreen) mc.screen;
                check("Writer offers copying", screen.mode() == WriterScreen.Mode.COPY);
                mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, WriterMenu.BUTTON_COPY);
                wait = 10;
                step++;
            }
            case 6 -> {
                server.execute(() -> {
                    CartridgeContents copy = CartridgeComponents.CARTRIDGE.get(writer(server).getItem(CartridgeWriterBlockEntity.MAIN));
                    check("copy holds the same game", copy != null && romSha1.equals(copy.romSha1()));
                    check("copy has its own ID", copy != null && !copy.cartId().equals(firstCartId));
                    check("copy starts without the original's save (D216)", copy != null
                            && SaveServer.store(server).readSram(copy.cartId()).isEmpty()
                            && SaveServer.store(server).readSram(firstCartId).isPresent());
                    SaveServer.store(server).deleteSram(firstCartId); // the cart plays on later, from power-on
                });
                WriterScreen screen = (WriterScreen) mc.screen;
                mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, WriterMenu.BUTTON_ERASE); // D78
                wait = 10;
                step++;
            }
            case 7 -> { // a disk game on a server without the BIOS asks for it (D74)
                WriterScreen screen = (WriterScreen) mc.screen;
                check("erased back to a blank", CartridgeWriterBlockEntity.isBlank(screen.getMenu().main()));
                Path fds = dir().resolve("selftest-fake.fds");
                byte[] side = new byte[65500];
                side[0] = 1;
                System.arraycopy("*NINTENDO-HVC*".getBytes(), 0, side, 1, 14);
                byte[] image = new byte[16 + side.length];
                System.arraycopy(new byte[] {'F', 'D', 'S', 0x1A, 1}, 0, image, 0, 5);
                System.arraycopy(side, 0, image, 16, side.length);
                Files.write(fds, image);
                screen.pickGame(fds);
                wait = 10;
                step++;
            }
            case 8 -> {
                ((WriterScreen) mc.screen).burn();
                wait = 20;
                step++;
            }
            case 9 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                check("server asks for the BIOS", screen.needsBios());
                shot(mc, "3-writer-needs-bios");
                Path fakeBios = dir().resolve("selftest-fake-disksys.rom");
                Files.write(fakeBios, new byte[8192]);
                screen.pickBios(fakeBios);
                wait = 10;
                step++;
            }
            case 10 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                check("a wrong BIOS is refused", !screen.uploading() && screen.needsBios()
                        && screen.status().getString().contains("BIOS"));
                Files.deleteIfExists(dir().resolve("selftest-fake.fds"));
                Files.deleteIfExists(dir().resolve("selftest-fake-disksys.rom"));
                mc.player.closeContainer();
                server.execute(() -> {
                    CartridgeWriterBlockEntity writer = writer(server);
                    writer.copy(); // leave a burned cart in the socket for the screenshot (green LED)
                });
                lookAt(mc, Vec3.atCenterOf(writerPos).add(0, 0.1, -0.5));
                wait = 10;
                step++;
            }
            case 11 -> {
                shot(mc, "4-writer-socket");
                // Forget the client's copy, so the console has to download it from the server (D16, D70).
                Files.deleteIfExists(RomCache.directory(Services.PLATFORM.gameDir()).resolve(romSha1));
                UUID id = mc.player.getUUID();
                server.execute(() -> play(server, id));
                lookAt(mc, Vec3.atCenterOf(tvPos).add(0, 0, -0.3));
                wait = 2;
                step++;
            }
            case 12 -> { // wait for the game to run (the ROM and maybe the core are downloading)
                check("play mode entered", PlayMode.isPlaying(consolePos));
                step++;
            }
            case 13 -> {
                ConsoleView view = ClientConsoles.view(consolePos);
                if (view != null && view.state() == ConsoleView.State.LOADING) sawLoading = true;
                if (view != null && view.state() == ConsoleView.State.ERROR) {
                    fail("console error: " + view.message().getString());
                    return;
                }
                if (view == null || view.state() != ConsoleView.State.PLAYING) {
                    if (++waited > 20 * 90) fail("timed out waiting for PLAYING, state " + (view == null ? null : view.state()));
                    return;
                }
                log("PLAYING after " + waited + " ticks" + (sawLoading ? " (saw the download screen)" : ""));
                check("ROM downloaded into the client cache",
                        RomCache.get(Services.PLATFORM.gameDir(), romSha1).isPresent());
                wait = 60;
                step++;
            }
            case 14 -> {
                shot(mc, "5-tv");
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.PRESS); // Start: nestest runs its tests
                wait = 4;
                step++;
            }
            case 15 -> {
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.RELEASE);
                wait = 60;
                step++;
            }
            case 16 -> {
                shot(mc, "6-after-start");
                audioLog(mc, "playing");
                PlayMode.onKey(InputConstants.KEY_ESCAPE, 0, InputConstants.PRESS); // put the pad down: the game pauses (D88)
                wait = 60;
                step = 100;
            }
            case 100 -> {
                check("paused with nobody holding a pad", ClientConsoles.view(consolePos).paused());
                audioLog(mc, "paused");
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    hold(player, ItemStack.EMPTY);
                    click(player, server.overworld(), consolePos, 4.25, 0.75, Direction.NORTH); // take port 1 again
                });
                wait = 20;
                step++;
            }
            case 101 -> {
                audioLog(mc, "1 s after taking the pad again");
                wait = 40;
                step++;
            }
            case 102 -> {
                ConsoleInstance instance = ClientConsoles.view(consolePos).instance();
                int before = instance.audio().plays;
                audioLog(mc, "3 s after taking the pad again");
                check("playing again after the pause", PlayMode.isPlaying(consolePos) && !ClientConsoles.view(consolePos).paused());
                check("audio playing after the pause", instance.audio().lastState == org.lwjgl.openal.AL10.AL_PLAYING);
                log("audio restarts so far: " + before);
                UUID id = mc.player.getUUID();
                server.execute(() -> displays(server, id));
                wait = 40;
                step = 130;
            }
            case 130 -> { // D129-D134: the flatscreens and a video wall show the game
                lookAt(mc, Vec3.atCenterOf(base).add(0, 1.2, 7));
                Screen wall = DisplayBlock.screenAt(mc.level, wallPos);
                check("wall is one 3 x 2 screen", wall != null && wall.problem() == null
                        && Math.abs(wall.width() - (3 - 1 / 16.0)) < 1e-6 && Math.abs(wall.height() - (2 - 1 / 16.0)) < 1e-6);
                wait = 5;
                step++;
            }
            case 131 -> {
                shot(mc, "11-displays");
                lookAt(mc, DisplayBlock.screenAt(mc.level, wallPos).center()); // the camera turns on the next frame
                wait = 2;
                step = 140;
            }
            case 140 -> {
                check("the Blaster aims at the wall", LightGunAim.aim(List.of(wallPos), 16) != null);
                lookAt(mc, DisplayBlock.screenAt(mc.level, largePos).center());
                wait = 2;
                step++;
            }
            case 141 -> {
                check("the Blaster aims at the Large Flatscreen", LightGunAim.aim(List.of(largePos), 16) != null);
                server.execute(() -> server.overworld().removeBlock(wallPos.below(), false)); // a hole: not a rectangle
                wait = 10;
                step = 132;
            }
            case 132 -> {
                Screen wall = DisplayBlock.screenAt(mc.level, wallPos);
                check("a wall with a hole says so", wall != null && wall.problem() != null);
                lookAt(mc, Vec3.atCenterOf(base).add(0, 1.2, 7));
                server.execute(() -> {
                    ServerLevel level = server.overworld();
                    check("the wall stays lit", level.getBlockState(wallPos.relative(Direction.EAST)).getValue(DisplayBlock.LIT));
                    level.removeBlock(largePos.above(), false); // breaking one space takes the whole set
                    check("Large Flatscreen removed whole", level.getBlockState(largePos).isAir()
                            && level.getBlockState(largePos.relative(Direction.EAST).above()).isAir());
                });
                wait = 5;
                step++;
            }
            case 133 -> {
                shot(mc, "12-wall-hole");
                lookAt(mc, Vec3.atCenterOf(tvPos).add(0, 0, -0.3));
                PlayMode.onKey(InputConstants.KEY_F, 0, InputConstants.PRESS);
                wait = 10;
                step = 17;
            }
            case 17 -> {
                shot(mc, "7-big-view");
                PlayMode.onKey(InputConstants.KEY_F, 0, InputConstants.PRESS);
                server.execute(() -> {
                    ConsoleBlockEntity console = (ConsoleBlockEntity) server.overworld().getBlockEntity(consolePos);
                    console.removeItem(0, 1); // D30: pulled mid-game
                });
                wait = 4;
                step++;
            }
            case 18 -> {
                ConsoleView view = ClientConsoles.view(consolePos);
                check("glitch after pulling the cart", view != null && view.state() == ConsoleView.State.GLITCH);
                wait = 40;
                step++;
            }
            case 19 -> {
                ConsoleView view = ClientConsoles.view(consolePos);
                check("black after the glitch", view != null && view.state() == ConsoleView.State.BLACK);
                PlayMode.onKey(InputConstants.KEY_ESCAPE, 0, InputConstants.PRESS);
                wait = 10;
                step++;
            }
            case 20 -> {
                check("left play mode", !PlayMode.isPlaying(consolePos));
                server.execute(() -> {
                    ConsoleBlockEntity console = (ConsoleBlockEntity) server.overworld().getBlockEntity(consolePos);
                    check("server released the pad", console.holder(0) == null);
                });
                wait = 10;
                step++;
            }
            case 21 -> { // D124-D126: the game file vanishes from the server; the Writer restores it
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    try {
                        Files.deleteIfExists(server.getWorldPath(LevelResource.ROOT).resolve(Cartridge.MOD_ID).resolve("roms").resolve(romSha1));
                    } catch (IOException e) {
                        fail("delete: " + e);
                    }
                    writer(server).setChanged(); // as a cart going in: the server checks its file
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    hold(player, ItemStack.EMPTY);
                    click(player, server.overworld(), writerPos, 8, 3, Direction.NORTH); // opens the Writer
                });
                wait = 20;
                step = 120;
            }
            case 120 -> {
                if (!(mc.screen instanceof WriterScreen screen)) {
                    fail("the Writer didn't open for the restore");
                    return;
                }
                check("Writer offers a restore", screen.mode() == WriterScreen.Mode.RESTORE);
                ItemStack cart = screen.getMenu().main();
                //? if >=1.20.5 {
                List<net.minecraft.network.chat.Component> lines = cart.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.EMPTY,
                        mc.player, net.minecraft.world.item.TooltipFlag.NORMAL);
                //?} else {
                /*List<net.minecraft.network.chat.Component> lines = cart.getTooltipLines(mc.player,
                        net.minecraft.world.item.TooltipFlag.NORMAL);
                *///?}
                check("tooltip says the game is missing", lines.stream().anyMatch(l -> l.getString().contains("missing")));
                shot(mc, "8-writer-restore");
                screen.pickRestore(dir().resolve("selftest.nes"));
                wait = 10;
                step++;
            }
            case 121 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                if (screen.uploading()) {
                    if (++waited > 200) fail("timed out restoring");
                    return;
                }
                waited = 0;
                log("Writer says: " + screen.status().getString());
                Path stored = server.getWorldPath(LevelResource.ROOT).resolve(Cartridge.MOD_ID).resolve("roms").resolve(romSha1);
                check("game file restored on the server", Files.isRegularFile(stored));
                wait = 5;
                step++;
            }
            case 122 -> {
                check("Writer back to normal after the restore", ((WriterScreen) mc.screen).mode() == WriterScreen.Mode.ERASE);
                // The Super Pixelon (D136): burn an SNES game written for the test, then play it.
                Files.write(dir().resolve("selftest.sfc"), SnesTestRom.build());
                server.execute(() -> writer(server).setItem(CartridgeWriterBlockEntity.MAIN,
                        new ItemStack(CartridgeItems.BLANK_CARTRIDGE.get())));
                wait = 10;
                step = 150;
            }
            case 150 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                check("Writer offers burning a blank again", screen.mode() == WriterScreen.Mode.BURN);
                screen.pickGame(dir().resolve("selftest.sfc"));
                wait = 10;
                step++;
            }
            case 151 -> {
                ((WriterScreen) mc.screen).burn();
                step++;
            }
            case 152 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                if (screen.uploading() || screen.mode() == WriterScreen.Mode.BURN) {
                    if (++waited > 200) fail("timed out burning the SNES game: " + screen.status().getString());
                    return;
                }
                waited = 0;
                log("Writer says: " + screen.status().getString());
                ItemStack cart = screen.getMenu().main();
                CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(cart);
                check("SNES game burned as a Super Pixelon cart",
                        contents != null && CartridgeContents.SNES.equals(contents.system()));
                shot(mc, "13-writer-snes");
                mc.player.closeContainer();
                UUID id = mc.player.getUUID();
                server.execute(() -> snes(server, id));
                wait = 10;
                step++;
            }
            case 153 -> {
                ConsoleView view = ClientConsoles.view(snesPos);
                if (view != null && view.state() == ConsoleView.State.ERROR) {
                    fail("Super Pixelon error: " + view.message().getString());
                    return;
                }
                if (view == null || view.state() != ConsoleView.State.PLAYING || view.instance() == null
                        || view.instance().sharedMemory() == null) {
                    if (++waited > 20 * 30) fail("timed out waiting for the Super Pixelon, state " + (view == null ? null : view.state()));
                    return;
                }
                waited = 0;
                check("playing the Super Pixelon", PlayMode.isPlaying(snesPos));
                lookAt(mc, Vec3.atCenterOf(snesTvPos).add(0, 0, -0.3));
                wait = 30;
                step++;
            }
            case 154 -> {
                int[] rgb = centre(snesPos);
                check("Super Pixelon shows red", rgb[0] > 200 && rgb[1] < 60);
                shot(mc, "14-snes-red");
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.PRESS); // Start
                wait = 4;
                step++;
            }
            case 155 -> {
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.RELEASE);
                wait = 30;
                step++;
            }
            case 156 -> {
                int[] rgb = centre(snesPos);
                check("Start turned it green", rgb[1] > 200 && rgb[0] < 60);
                shot(mc, "15-snes-green");
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    hold(player, new ItemStack(CartridgeItems.MOUSE_16BIT.get()));
                    clickAt(player, server.overworld(), snesPos, 10.25, 1.1, 3, Direction.NORTH); // port 2
                    ConsoleBlockEntity snes = (ConsoleBlockEntity) server.overworld().getBlockEntity(snesPos);
                    check("mouse refused by port 2 (D214)", !snes.hasMouse(1) && snes.padItem(1).isEmpty()
                            && player.getMainHandItem().is(CartridgeItems.MOUSE_16BIT.get()));
                    hold(player, ItemStack.EMPTY);
                });
                wait = 40;
                step++;
            }
            case 157 -> {
                ConsoleView view = ClientConsoles.view(snesPos);
                check("still playing after the refused mouse", view != null && view.state() == ConsoleView.State.PLAYING);
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    player.teleportTo(snesPos.getX() - 0.6, snesPos.getY(), snesPos.getZ() - 1.3);
                    // The new items' icons in the hotbar, for the screenshot.
                    ConsoleBlockEntity snes = (ConsoleBlockEntity) server.overworld().getBlockEntity(snesPos);
                    List<ItemStack> show = List.of(new ItemStack(CartridgeItems.CONSOLE_16BIT.get()),
                            new ItemStack(CartridgeItems.CONTROLLER_16BIT.get()), new ItemStack(CartridgeItems.MOUSE_16BIT.get()),
                            snes.cartridge().copy(), new ItemStack(CartridgeItems.FLATSCREEN_TV.get()),
                            new ItemStack(CartridgeItems.LARGE_FLATSCREEN_TV.get()), new ItemStack(CartridgeItems.DISPLAY_PANEL.get()));
                    for (int i = 0; i < show.size(); i++) player.getInventory().setItem(2 + i, show.get(i));
                });
                wait = 10;
                step = 158;
            }
            case 158 -> {
                lookAt(mc, Vec3.atBottomCenterOf(snesPos).add(0, 0.2, 0));
                wait = 5;
                step++;
            }
            case 159 -> {
                shot(mc, "16-snes-close");
                PlayMode.onKey(InputConstants.KEY_ESCAPE, 0, InputConstants.PRESS);
                // The Vortex Drive (D151): burn a Genesis game written for the test, then play it.
                Files.write(dir().resolve("selftest.md"), GenesisTestRom.build());
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    player.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
                    hold(player, ItemStack.EMPTY);
                    click(player, server.overworld(), writerPos, 8, 3, Direction.NORTH); // opens the Writer
                    writer(server).setItem(CartridgeWriterBlockEntity.MAIN, new ItemStack(CartridgeItems.BLANK_CARTRIDGE.get()));
                });
                wait = 20;
                step = 160;
            }
            case 160 -> {
                if (!(mc.screen instanceof WriterScreen screen)) {
                    fail("the Writer did not open again");
                    step = 22;
                    return;
                }
                check("Writer offers burning a blank for the Genesis game", screen.mode() == WriterScreen.Mode.BURN);
                screen.pickGame(dir().resolve("selftest.md"));
                wait = 10;
                step++;
            }
            case 161 -> {
                ((WriterScreen) mc.screen).burn();
                step++;
            }
            case 162 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                if (screen.uploading() || screen.mode() == WriterScreen.Mode.BURN) {
                    if (++waited > 200) fail("timed out burning the Genesis game: " + screen.status().getString());
                    return;
                }
                waited = 0;
                log("Writer says: " + screen.status().getString());
                CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(screen.getMenu().main());
                check("Genesis game burned as a Vortex Drive cart",
                        contents != null && CartridgeContents.GENESIS.equals(contents.system()));
                mc.player.closeContainer();
                UUID id = mc.player.getUUID();
                server.execute(() -> vortex(server, id));
                lookAt(mc, Vec3.atCenterOf(base.offset(-3, 0, 3))); // frames are only read for TVs on screen
                wait = 10;
                step++;
            }
            case 163 -> {
                ConsoleView view = ClientConsoles.view(vortexPos);
                if (view != null && view.state() == ConsoleView.State.ERROR) {
                    fail("Vortex Drive error: " + view.message().getString());
                    step = 22;
                    return;
                }
                if (view == null || view.state() != ConsoleView.State.PLAYING || view.instance() == null
                        || view.instance().sharedMemory() == null) {
                    if (++waited > 20 * 30) fail("timed out waiting for the Vortex Drive, state " + (view == null ? null : view.state())
                            + ", instance " + (view == null || view.instance() == null ? null : view.instance().status()));
                    return;
                }
                waited = 0;
                check("playing the Vortex Drive", PlayMode.isPlaying(vortexPos));
                lookAt(mc, Vec3.atCenterOf(vortexTvPos).add(0, 0, -0.3));
                wait = 30;
                step++;
            }
            case 164 -> {
                int[] rgb = centre(vortexPos);
                check("Vortex Drive shows red", rgb[0] > 200 && rgb[1] < 60);
                shot(mc, "17-vortex-red");
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.PRESS); // Start
                wait = 4;
                step++;
            }
            case 165 -> {
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.RELEASE);
                wait = 30;
                step++;
            }
            case 166 -> {
                int[] rgb = centre(vortexPos);
                check("Start turned it green", rgb[1] > 200 && rgb[0] < 60);
                PlayMode.onKey(InputConstants.KEY_E, 0, InputConstants.PRESS); // the R key: the Vortex Pad's Z (D152)
                wait = 6;
                step++;
            }
            case 167 -> {
                PlayMode.onKey(InputConstants.KEY_E, 0, InputConstants.RELEASE);
                wait = 30;
                step++;
            }
            case 168 -> {
                int[] rgb = centre(vortexPos);
                check("Z (the E key) turned it blue: the 6-button pad works", rgb[2] > 200 && rgb[0] < 60 && rgb[1] < 60);
                shot(mc, "18-vortex-blue");
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    player.teleportTo(vortexPos.getX() + 1.6, vortexPos.getY(), vortexPos.getZ() - 1.3);
                    ConsoleBlockEntity vortex = (ConsoleBlockEntity) server.overworld().getBlockEntity(vortexPos);
                    List<ItemStack> show = List.of(new ItemStack(CartridgeItems.CONSOLE_VORTEX.get()),
                            new ItemStack(CartridgeItems.CONTROLLER_VORTEX.get()), vortex.cartridge().copy());
                    for (int i = 0; i < show.size(); i++) player.getInventory().setItem(2 + i, show.get(i));
                });
                wait = 10;
                step++;
            }
            case 169 -> {
                lookAt(mc, Vec3.atBottomCenterOf(vortexPos).add(0, 0.2, 0));
                wait = 5;
                step++;
            }
            case 170 -> {
                shot(mc, "19-vortex-close");
                PlayMode.onKey(InputConstants.KEY_ESCAPE, 0, InputConstants.PRESS);
                // The Pocketron Color (D158): burn a Game Boy game written for the test, then play it in hand.
                Files.write(dir().resolve("selftest.gbc"), GameBoyTestRom.build());
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    player.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
                    hold(player, ItemStack.EMPTY);
                    click(player, server.overworld(), writerPos, 8, 3, Direction.NORTH); // opens the Writer
                    writer(server).setItem(CartridgeWriterBlockEntity.MAIN, new ItemStack(CartridgeItems.BLANK_CARTRIDGE.get()));
                });
                wait = 20;
                step = 171;
            }
            case 171 -> {
                if (!(mc.screen instanceof WriterScreen screen)) {
                    fail("the Writer did not open for the Game Boy game");
                    step = 22;
                    return;
                }
                screen.pickGame(dir().resolve("selftest.gbc"));
                wait = 10;
                step++;
            }
            case 172 -> {
                ((WriterScreen) mc.screen).burn();
                step++;
            }
            case 173 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                if (screen.uploading() || screen.mode() == WriterScreen.Mode.BURN) {
                    if (++waited > 200) fail("timed out burning the Game Boy game: " + screen.status().getString());
                    return;
                }
                waited = 0;
                CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(screen.getMenu().main());
                check("Game Boy game burned as a black cart (also plays in color, D170)",
                        contents != null && CartridgeContents.GAME_BOY_DUAL.equals(contents.system()));
                mc.player.closeContainer();
                UUID id = mc.player.getUUID();
                server.execute(() -> pocketron(server, id));
                lookAt(mc, mc.player.getEyePosition().add(0, -0.2, 1));
                wait = 10;
                step++;
            }
            case 174 -> {
                ConsoleView view = ClientConsoles.handheldView(mc.player.getMainHandItem());
                if (view != null && view.state() == ConsoleView.State.ERROR) {
                    fail("Pocketron error: " + view.message().getString());
                    step = 22;
                    return;
                }
                if (view == null || view.state() != ConsoleView.State.PLAYING || view.instance() == null
                        || view.instance().sharedMemory() == null) {
                    if (++waited > 20 * 30) {
                        fail("timed out waiting for the Pocketron, state " + (view == null ? null : view.state()));
                        step = 22;
                    }
                    return;
                }
                waited = 0;
                check("playing the Pocketron Color", PlayMode.isPlaying(view.key));
                wait = 20 * 8; // SameBoy's boot animation
                step++;
            }
            case 175 -> {
                int[] rgb = centre(ClientConsoles.handheldView(mc.player.getMainHandItem()));
                check("Pocketron Color shows red", rgb[0] > 150 && rgb[1] < 100);
                shot(mc, "20-pocketron-red");
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.PRESS); // Start
                wait = 4;
                step++;
            }
            case 176 -> {
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.RELEASE);
                wait = 30;
                step++;
            }
            case 177 -> {
                int[] rgb = centre(ClientConsoles.handheldView(mc.player.getMainHandItem()));
                check("Start turned the Pocketron green", rgb[1] > 150 && rgb[0] < 100);
                PlayMode.onKey(InputConstants.KEY_F, 0, InputConstants.PRESS); // the big view, in the Game Boy's shape
                wait = 10;
                step++;
            }
            case 178 -> {
                shot(mc, "21-pocketron-big-view");
                PlayMode.onKey(InputConstants.KEY_F, 0, InputConstants.PRESS);
                PlayMode.onKey(InputConstants.KEY_ESCAPE, 0, InputConstants.PRESS); // stop playing: it pauses (D164)
                wait = 30;
                step++;
            }
            case 179 -> {
                ConsoleView view = ClientConsoles.handheldView(mc.player.getMainHandItem());
                check("the Pocketron paused when put down", view != null && view.paused());
                handheldId = view != null && view.key.handheld() != null ? view.key.handheld() : null;
                select(mc, 8); // put it away: its clients send the server where it got to
                wait = 60;
                step++;
            }
            case 180 -> {
                check("the Pocketron stopped once put away", ClientConsoles.handheldView(mc.player.getInventory().getItem(0)) == null);
                Path resume = server.getWorldPath(LevelResource.ROOT).resolve(Cartridge.MOD_ID).resolve("resume_handheld")
                        .resolve(handheldId + ".state");
                check("the server kept where the Pocketron's game got to", Files.exists(resume));
                select(mc, 0); // take it out again
                wait = 10;
                step++;
            }
            case 181 -> {
                ConsoleView view = ClientConsoles.handheldView(mc.player.getMainHandItem());
                if (view == null || view.state() != ConsoleView.State.PLAYING) {
                    if (++waited > 20 * 30) {
                        fail("timed out waiting for the Pocketron to resume");
                        step = 22;
                    }
                    return;
                }
                waited = 0;
                wait = 20;
                step++;
            }
            case 182 -> {
                int[] rgb = centre(ClientConsoles.handheldView(mc.player.getMainHandItem()));
                check("taken out again, it resumed where it was (green, not a fresh red boot)", rgb[1] > 150 && rgb[0] < 100);
                shot(mc, "22-pocketron-resumed");
                // The link cable (D177): this player's two hands, a Pocketron Color and a Pocketron, linked.
                UUID id = mc.player.getUUID();
                server.execute(() -> linkHands(server, id));
                wait = 10;
                step = 190;
            }
            case 190 -> {
                ConsoleView view = ClientConsoles.handheldView(mc.player.getMainHandItem());
                if (view == null || view.state() != ConsoleView.State.PLAYING || view.instance() == null
                        || view.instance().sharedMemory() == null) {
                    if (view != null && view.state() == ConsoleView.State.ERROR) {
                        fail("linked game error: " + view.message().getString());
                        step = 22;
                    } else if (++waited > 20 * 30) {
                        fail("timed out waiting for the linked game, state " + (view == null ? null : view.state()));
                        step = 22;
                    }
                    return;
                }
                waited = 0;
                check("both hands' Pocketrons share one linked game",
                        view == ClientConsoles.handheldView(mc.player.getOffhandItem()) && view.linkedCarts != null);
                check("playing the linked game on port 1", PlayMode.isPlaying(view.key));
                wait = 20 * 8;
                step++;
            }
            case 191 -> {
                ConsoleView view = ClientConsoles.handheldView(mc.player.getMainHandItem());
                int[] size = frameSize(view);
                check("the linked game's picture is two screens side by side (320 x 144)", size[0] == 320 && size[1] == 144);
                int[] left = pixel(view, 80, 72), right = pixel(view, 240, 72);
                check("the Color (port 1) shows red", left[0] > 150 && left[1] < 100);
                light = right[0] + right[1] + right[2];
                shot(mc, "26-linked");
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.PRESS); // Start, on port 1 only
                wait = 4;
                step++;
            }
            case 192 -> {
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.RELEASE);
                wait = 30;
                step++;
            }
            case 193 -> {
                ConsoleView view = ClientConsoles.handheldView(mc.player.getMainHandItem());
                int[] left = pixel(view, 80, 72), right = pixel(view, 240, 72);
                check("Start turned port 1's Game Boy green", left[1] > 150 && left[0] < 100);
                check("and left port 2's as it was", Math.abs(right[0] + right[1] + right[2] - light) < 30);
                shot(mc, "27-linked-start");
                PlayMode.onKey(InputConstants.KEY_ESCAPE, 0, InputConstants.PRESS);
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(player.getMainHandItem());
                    Handhelds.unlink(server, link, null);
                    check("unlinked: neither handheld has the cable any more",
                            !CartridgeComponents.HANDHELD_LINK.has(player.getMainHandItem())
                                    && !CartridgeComponents.HANDHELD_LINK.has(player.getOffhandItem()));
                    player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
                    HandheldItem.offhandAllowed = false;
                    adapter(server, id);
                });
                lookAt(mc, Vec3.atCenterOf(base.offset(-5, 0, 3)).add(0, 0, -0.3));
                wait = 10;
                step = 183;
            }
            case 183 -> {
                ConsoleView view = ClientConsoles.view(adapterPos);
                if (view != null && view.state() == ConsoleView.State.ERROR) {
                    fail("Pocketron Adapter error: " + view.message().getString());
                    step = 22;
                    return;
                }
                if (view == null || view.state() != ConsoleView.State.PLAYING || view.instance() == null
                        || view.instance().sharedMemory() == null) {
                    if (++waited > 20 * 30) {
                        fail("timed out waiting for the Super Game Boy, state " + (view == null ? null : view.state()));
                        step = 22;
                    }
                    return;
                }
                waited = 0;
                check("playing the Super Game Boy", PlayMode.isPlaying(adapterPos));
                wait = 20 * 8;
                step++;
            }
            case 184 -> {
                int[] rgb = centre(ClientConsoles.view(adapterPos));
                light = rgb[0] + rgb[1] + rgb[2];
                check("the Super Game Boy shows a light screen", light > 300);
                shot(mc, "23-adapter-light");
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.PRESS); // Start
                wait = 4;
                step++;
            }
            case 185 -> {
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.RELEASE);
                wait = 30;
                step++;
            }
            case 186 -> {
                int[] rgb = centre(ClientConsoles.view(adapterPos));
                check("Start darkened it", rgb[0] + rgb[1] + rgb[2] < light - 150);
                var f = frameSize(ClientConsoles.view(adapterPos));
                check("the Super Game Boy's border is drawn (256 x 224)", f[0] == 256 && f[1] == 224);
                shot(mc, "24-adapter-dark");
                PlayMode.onKey(InputConstants.KEY_ESCAPE, 0, InputConstants.PRESS);
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    player.teleportTo(adapterPos.getX() + 1.6, adapterPos.getY(), adapterPos.getZ() - 1.3);
                    ConsoleBlockEntity snes = (ConsoleBlockEntity) server.overworld().getBlockEntity(adapterPos);
                    ItemStack dyed = new ItemStack(CartridgeItems.POCKETRON_COLOR.get());
                    Compat.setDye(dyed, 0x2EA8A0); // teal (D169)
                    List<ItemStack> show = List.of(new ItemStack(CartridgeItems.POCKETRON.get()), dyed, snes.cartridge().copy(),
                            CartridgeItem.create(new CartridgeContents(CartridgeContents.GAME_BOY, romSha1, "grey", UUID.randomUUID())),
                            CartridgeItem.create(new CartridgeContents(CartridgeContents.GAME_BOY_DUAL, romSha1, "black", UUID.randomUUID())),
                            CartridgeItem.create(new CartridgeContents(CartridgeContents.GAME_BOY_COLOR, romSha1, "clear", UUID.randomUUID())));
                    for (int i = 0; i < show.size(); i++) player.getInventory().setItem(2 + i, show.get(i));
                });
                wait = 10;
                step++;
            }
            case 187 -> {
                lookAt(mc, Vec3.atBottomCenterOf(adapterPos).add(0, 0.2, 0));
                wait = 5;
                step++;
            }
            case 188 -> {
                shot(mc, "25-adapter-close");
                // The Pocketron Advance (D183): burn a Game Boy Advance game written for the test, then play it in hand.
                Files.write(dir().resolve("selftest.gba"), GameBoyAdvanceTestRom.build());
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    player.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
                    hold(player, ItemStack.EMPTY);
                    player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(CartridgeItems.POCKETRON.get())); // D187
                    click(player, server.overworld(), writerPos, 8, 3, Direction.NORTH); // opens the Writer
                    writer(server).setItem(CartridgeWriterBlockEntity.MAIN, new ItemStack(CartridgeItems.BLANK_CARTRIDGE.get()));
                });
                wait = 20;
                step = 200;
            }
            case 200 -> {
                if (!(mc.screen instanceof WriterScreen screen)) {
                    fail("the Writer did not open for the Game Boy Advance game");
                    step = 22;
                    return;
                }
                UUID player = mc.player.getUUID();
                server.execute(() -> {
                    Inventory inventory = server.getPlayerList().getPlayer(player).getInventory();
                    check("a Pocketron put in the off hand went back into the inventory (D187)",
                            !HandheldItem.isHandheld(inventory.player.getOffhandItem())
                                    && Compat.mainItems(inventory).stream().anyMatch(HandheldItem::isHandheld));
                });
                screen.pickGame(dir().resolve("selftest.gba"));
                wait = 10;
                step++;
            }
            case 201 -> {
                ((WriterScreen) mc.screen).burn();
                step++;
            }
            case 202 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                if (screen.uploading() || screen.mode() == WriterScreen.Mode.BURN) {
                    if (++waited > 200) fail("timed out burning the Game Boy Advance game: " + screen.status().getString());
                    return;
                }
                waited = 0;
                CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(screen.getMenu().main());
                check("Game Boy Advance game burned as a Pocketron Advance cart (D185)",
                        contents != null && CartridgeContents.GAME_BOY_ADVANCE.equals(contents.system()));
                mc.player.closeContainer();
                UUID id = mc.player.getUUID();
                server.execute(() -> advance(server, id, CartridgeItems.POCKETRON_ADVANCE.get()));
                lookAt(mc, mc.player.getEyePosition().add(0, -0.2, 1));
                wait = 10;
                step++;
            }
            case 203, 207 -> {
                ConsoleView view = ClientConsoles.handheldView(mc.player.getMainHandItem());
                if (view != null && view.state() == ConsoleView.State.ERROR) {
                    fail("Pocketron Advance error: " + view.message().getString());
                    step = 22;
                    return;
                }
                if (view == null || view.state() != ConsoleView.State.PLAYING || view.instance() == null
                        || view.instance().sharedMemory() == null) {
                    if (++waited > 20 * 30) {
                        fail("timed out waiting for the Pocketron Advance, state " + (view == null ? null : view.state()));
                        step = 22;
                    }
                    return;
                }
                waited = 0;
                check(step == 203 ? "playing the Pocketron Advance" : "playing the Pocketron Advance SP", PlayMode.isPlaying(view.key));
                wait = 40;
                step++;
            }
            case 204 -> {
                ConsoleView view = ClientConsoles.handheldView(mc.player.getMainHandItem());
                int[] rgb = centre(view), size = frameSize(view);
                if (!(rgb[0] > 150 && rgb[1] < 100) && ++waited < 40) return; // a frame mid-write reads as none
                waited = 0;
                check("the Pocketron Advance's picture is 240 x 160", size[0] == 240 && size[1] == 160);
                check("the Pocketron Advance shows red", rgb[0] > 150 && rgb[1] < 100);
                shot(mc, "28-advance-red");
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.PRESS); // Start, held
                PlayMode.onKey(InputConstants.KEY_F, 0, InputConstants.PRESS); // the big view, 3:2
                wait = 20;
                step++;
            }
            case 205 -> {
                int[] rgb = centre(ClientConsoles.handheldView(mc.player.getMainHandItem()));
                check("green while Start is held", rgb[1] > 150 && rgb[0] < 100);
                shot(mc, "29-advance-big-view");
                PlayMode.onKey(InputConstants.KEY_RETURN, 0, InputConstants.RELEASE);
                PlayMode.onKey(InputConstants.KEY_F, 0, InputConstants.PRESS);
                PlayMode.onKey(InputConstants.KEY_ESCAPE, 0, InputConstants.PRESS);
                wait = 10;
                step++;
            }
            case 206 -> {
                UUID id = mc.player.getUUID();
                server.execute(() -> advance(server, id, CartridgeItems.POCKETRON_ADVANCE_SP.get()));
                wait = 10;
                step++;
            }
            case 208 -> {
                int[] rgb = centre(ClientConsoles.handheldView(mc.player.getMainHandItem()));
                check("the Pocketron Advance SP shows red", rgb[0] > 150 && rgb[1] < 100);
                shot(mc, "30-advance-sp");
                PlayMode.onKey(InputConstants.KEY_ESCAPE, 0, InputConstants.PRESS);
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    ItemStack dyed = new ItemStack(CartridgeItems.POCKETRON_ADVANCE.get());
                    Compat.setDye(dyed, 0xC03030); // red (D185)
                    List<ItemStack> show = List.of(new ItemStack(CartridgeItems.POCKETRON_ADVANCE.get()), dyed,
                            new ItemStack(CartridgeItems.POCKETRON_ADVANCE_SP.get()), advanceCart.copy());
                    for (int i = 0; i < show.size(); i++) player.getInventory().setItem(2 + i, show.get(i));
                });
                wait = 10;
                step++;
            }
            case 209 -> {
                shot(mc, "31-advance-hotbar");
                // The Pixelon with its cart in (D188): the door stands open, the cart lies flat in the bay.
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    hold(player, ItemStack.EMPTY);
                    player.teleportTo(consolePos.getX() + 0.5, consolePos.getY(), consolePos.getZ() - 1.2);
                    ConsoleBlockEntity nes = (ConsoleBlockEntity) server.overworld().getBlockEntity(consolePos);
                    if (nes.cartridge().isEmpty()) {
                        nes.setItem(ConsoleBlockEntity.CART_SLOT, CartridgeItem.create(new CartridgeContents(
                                CartridgeContents.NES, romSha1, "Self-test", UUID.randomUUID())));
                    }
                });
                wait = 10;
                step++;
            }
            case 210 -> {
                lookAt(mc, Vec3.atBottomCenterOf(consolePos).add(0, 0.15, -0.1));
                BlockState state = mc.level.getBlockState(consolePos);
                check("the Pixelon's door is open with a cart in (D188)",
                        state.hasProperty(Console8BitBlock.LOADED) && state.getValue(Console8BitBlock.LOADED));
                wait = 5;
                step++;
            }
            case 211 -> {
                shot(mc, "32-pixelon-door-open");
                // The Pixelon 64 (D189), with a game you own: CARTRIDGE_N64_ROM (no N64 game can be written for the test).
                String rom = System.getenv("CARTRIDGE_N64_ROM");
                if (rom == null || !Files.exists(Path.of(rom))) {
                    log("SKIP the Pixelon 64: CARTRIDGE_N64_ROM not set");
                    step = 219;
                    return;
                }
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    player.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
                    hold(player, ItemStack.EMPTY);
                    click(player, server.overworld(), writerPos, 8, 3, Direction.NORTH); // opens the Writer
                    writer(server).setItem(CartridgeWriterBlockEntity.MAIN, new ItemStack(CartridgeItems.BLANK_CARTRIDGE.get()));
                });
                wait = 20;
                step++;
            }
            case 212 -> {
                if (!(mc.screen instanceof WriterScreen screen)) {
                    fail("the Writer did not open for the N64 game");
                    step = 219;
                    return;
                }
                screen.pickGame(Path.of(System.getenv("CARTRIDGE_N64_ROM")));
                wait = 40;
                step++;
            }
            case 213 -> {
                ((WriterScreen) mc.screen).burn();
                step++;
            }
            case 214 -> {
                WriterScreen screen = (WriterScreen) mc.screen;
                if (screen.uploading() || screen.mode() == WriterScreen.Mode.BURN) {
                    if (++waited > 20 * 60) {
                        fail("timed out burning the N64 game: " + screen.status().getString());
                        step = 219;
                    }
                    return;
                }
                waited = 0;
                log("Writer says: " + screen.status().getString());
                CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(screen.getMenu().main());
                check("N64 game burned as a Pixelon 64 cart", contents != null && CartridgeContents.N64.equals(contents.system()));
                mc.player.closeContainer();
                UUID id = mc.player.getUUID();
                server.execute(() -> n64(server, id));
                lookAt(mc, Vec3.atCenterOf(base.offset(5, 0, 3)));
                wait = 10;
                step++;
            }
            case 215 -> {
                ConsoleView view = ClientConsoles.view(n64Pos);
                if (view != null && view.state() == ConsoleView.State.ERROR) {
                    fail("Pixelon 64 error: " + view.message().getString());
                    step = 219;
                    return;
                }
                if (view == null || view.state() != ConsoleView.State.PLAYING || view.instance() == null
                        || view.instance().sharedMemory() == null) {
                    if (++waited > 20 * 60) {
                        fail("timed out waiting for the Pixelon 64, state " + (view == null ? null : view.state()));
                        step = 219;
                    }
                    return;
                }
                waited = 0;
                check("playing the Pixelon 64", PlayMode.isPlaying(n64Pos));
                lookAt(mc, Vec3.atCenterOf(n64TvPos).add(0, 0, -0.3));
                wait = 20 * 12; // past the logo
                step++;
            }
            case 216 -> {
                int[] rgb = centre(n64Pos);
                check("the Pixelon 64 shows its game", rgb[0] + rgb[1] + rgb[2] > 30);
                ConsoleView view = ClientConsoles.view(n64Pos);
                check("the Pixelon 64 game runs in time", view != null && view.state() == ConsoleView.State.PLAYING);
                shot(mc, "33-pixelon-64-tv");
                PlayMode.onKey(InputConstants.KEY_W, 0, InputConstants.PRESS); // the stick, all the way up
                wait = 10;
                step++;
            }
            case 217 -> {
                PlayMode.onKey(InputConstants.KEY_W, 0, InputConstants.RELEASE);
                UUID id = mc.player.getUUID();
                server.execute(() -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(id);
                    player.teleportTo(n64Pos.getX() + 1.6, n64Pos.getY(), n64Pos.getZ() - 1.3);
                    ConsoleBlockEntity n64 = (ConsoleBlockEntity) server.overworld().getBlockEntity(n64Pos);
                    List<ItemStack> show = List.of(new ItemStack(CartridgeItems.CONSOLE_64.get()),
                            new ItemStack(CartridgeItems.CONTROLLER_64.get()), n64.cartridge().copy(),
                            new ItemStack(CartridgeItems.POCKETRON.get()), new ItemStack(CartridgeItems.POCKETRON_COLOR.get()),
                            new ItemStack(CartridgeItems.MOUSE_16BIT.get())); // their icons too
                    for (int i = 0; i < show.size(); i++) player.getInventory().setItem(2 + i, show.get(i));
                });
                wait = 10;
                step++;
            }
            case 218 -> {
                lookAt(mc, Vec3.atBottomCenterOf(n64Pos).add(0, 0.2, 0));
                wait = 5;
                step = 220;
            }
            case 220 -> {
                shot(mc, "34-pixelon-64-close");
                // The pad in the player's hands while playing (D104), in third person, by daylight.
                server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 6000"));
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
                wait = 10;
                step = 221;
            }
            case 221 -> {
                shot(mc, "35-playing-third-person-front");
                mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                wait = 10;
                step = 222;
            }
            case 222 -> {
                shot(mc, "36-playing-third-person-back");
                mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                PlayMode.onKey(InputConstants.KEY_ESCAPE, 0, InputConstants.PRESS);
                step = 219;
            }
            case 219 -> {
                mc.setScreen(CartridgeConfigScreen.create(null)); // the config screen (D122)
                wait = 20;
                step = 110;
            }
            case 110 -> {
                shot(mc, "9-config");
                check("config screen open", mc.screen != null);
                // D191: opened from a Pixelon 64's GUI, on the Controls tab with its group unfolded.
                mc.setScreen(CartridgeConfigScreen.create(null, com.nanobuntv.cartridge.config.ControlSet.PIXELON_64));
                wait = 20;
                step++;
            }
            case 111 -> {
                shot(mc, "10-controls");
                mc.setScreen(null);
                step = 22;
            }
            case 22 -> {
                log(LOG.stream().anyMatch(l -> l.startsWith("FAIL")) ? "RESULT: FAILED" : "RESULT: PASSED");
                write();
                step = -2;
                mc.stop();
            }
            default -> {}
        }
    }

    private static CartridgeWriterBlockEntity writer(MinecraftServer server) {
        return (CartridgeWriterBlockEntity) server.overworld().getBlockEntity(writerPos);
    }

    /** Server thread: build the setup, then open the Writer with a blank in it, as real clicks do. */
    private static void build(MinecraftServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            base = player.blockPosition();
            consolePos = base.offset(1, 0, 2);
            tvPos = base.offset(0, 0, 3);
            writerPos = base.offset(-1, 0, 2);
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-3, 0, -1), base.offset(3, 3, 5))) level.removeBlock(p, false);
            player.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
            for (BlockPos p : List.of(consolePos, tvPos, writerPos)) {
                var block = p == consolePos ? CartridgeBlocks.CONSOLE_8BIT.get() : p == tvPos ? CartridgeBlocks.CRT_TV.get()
                        : CartridgeBlocks.CARTRIDGE_WRITER.get();
                level.setBlockAndUpdate(p, block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            }
            // Start from an empty store so the upload really happens (the world is reused between runs).
            Files.deleteIfExists(server.getWorldPath(LevelResource.ROOT).resolve(Cartridge.MOD_ID).resolve("roms").resolve(romSha1));

            hold(player, ItemStack.EMPTY);
            click(player, level, writerPos, 8, 3, Direction.NORTH); // opens the Writer
            writer(server).setItem(CartridgeWriterBlockEntity.MAIN, new ItemStack(CartridgeItems.BLANK_CARTRIDGE.get()));
        } catch (IOException | RuntimeException e) {
            fail("build: " + e);
            Cartridge.LOGGER.error("self-test build failed", e);
        }
    }

    /** Server thread: a Super Pixelon and a CRT, linked; a pad in port 1; the burned SNES cart in; power on; play. */
    private static void snes(MinecraftServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            snesPos = base.offset(3, 0, 2);
            snesTvPos = base.offset(3, 0, 3);
            level.setBlockAndUpdate(snesPos, CartridgeBlocks.CONSOLE_16BIT.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(snesTvPos, CartridgeBlocks.CRT_TV.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            ConsoleBlockEntity console = (ConsoleBlockEntity) level.getBlockEntity(snesPos);
            check("a Super Pixelon is a SUPER_PIXELON", console.kind() == ConsoleKind.SUPER_PIXELON);

            hold(player, new ItemStack(CartridgeItems.AV_CABLE.get()));
            clickAt(player, level, snesPos, 8, 2, 8, Direction.UP);
            clickAt(player, level, snesTvPos, 9, 7, 0, Direction.NORTH);
            check("TV linked to the Super Pixelon", console.tvs().contains(snesTvPos));

            hold(player, new ItemStack(CartridgeItems.CONTROLLER_8BIT.get()));
            clickAt(player, level, snesPos, 12.25, 1.1, 3, Direction.NORTH);
            check("a Pixelon Pad doesn't fit", !console.hasPad(0));
            hold(player, new ItemStack(CartridgeItems.CONTROLLER_16BIT.get()));
            clickAt(player, level, snesPos, 12.25, 1.1, 3, Direction.NORTH); // port 1
            check("Super Pixelon Pad in port 1", console.hasPad(0));

            hold(player, CartridgeItem.create(new CartridgeContents(CartridgeContents.NES, romSha1, "nes", UUID.randomUUID())));
            clickAt(player, level, snesPos, 8, 2.5, 3, Direction.NORTH);
            check("a NES cart doesn't fit", console.cartridge().isEmpty());
            hold(player, writer(server).removeItemNoUpdate(CartridgeWriterBlockEntity.MAIN)); // the SNES cart
            clickAt(player, level, snesPos, 8, 2.5, 3, Direction.NORTH);
            check("SNES cart inserted", !console.cartridge().isEmpty());

            hold(player, ItemStack.EMPTY);
            clickAt(player, level, snesPos, 10.75, 4, 5.25, Direction.UP); // POWER, the slider on top
            check("Super Pixelon powered on", console.powered());
            clickAt(player, level, snesPos, 12.25, 1.1, 3, Direction.NORTH); // take port 1
            check("player holds the Super Pixelon's port 1", playerId.equals(console.holder(0)));
        } catch (RuntimeException e) {
            fail("snes: " + e);
            Cartridge.LOGGER.error("self-test SNES setup failed", e);
        }
    }

    /** Server thread: a Vortex Drive and a CRT, linked; a pad in port 1; the burned Genesis cart in; power on; play. */
    /** The Pixelon 64 (D189): four ports, its own pad only, the N64 cart from the Writer; player 1 plays. */
    private static void n64(MinecraftServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            n64Pos = base.offset(5, 0, 2);
            n64TvPos = base.offset(5, 0, 3);
            level.removeBlock(n64Pos, false); // a world from an earlier run
            level.setBlockAndUpdate(n64Pos, CartridgeBlocks.CONSOLE_64.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(n64TvPos, CartridgeBlocks.CRT_TV.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            ConsoleBlockEntity console = (ConsoleBlockEntity) level.getBlockEntity(n64Pos);
            check("a Pixelon 64 is a PIXELON_64 with four ports", console.kind() == ConsoleKind.PIXELON_64 && console.ports() == 4);

            hold(player, new ItemStack(CartridgeItems.AV_CABLE.get()));
            clickAt(player, level, n64Pos, 8, 4, 9, Direction.UP);
            clickAt(player, level, n64TvPos, 9, 7, 0, Direction.NORTH);
            check("TV linked to the Pixelon 64", console.tvs().contains(n64TvPos));

            hold(player, new ItemStack(CartridgeItems.CONTROLLER_VORTEX.get()));
            clickAt(player, level, n64Pos, 11, 1.1, 3, Direction.NORTH);
            check("a Vortex Pad doesn't fit", !console.hasPad(0));
            hold(player, new ItemStack(CartridgeItems.CONTROLLER_64.get()));
            clickAt(player, level, n64Pos, 5, 1.1, 3, Direction.NORTH); // port 4, at the viewer's right
            check("Pixelon 64 Pad in port 4", console.hasPad(3));
            hold(player, new ItemStack(CartridgeItems.CONTROLLER_64.get()));
            clickAt(player, level, n64Pos, 11, 1.1, 3, Direction.NORTH); // port 1
            check("Pixelon 64 Pad in port 1", console.hasPad(0));

            hold(player, writer(server).removeItemNoUpdate(CartridgeWriterBlockEntity.MAIN)); // the N64 cart
            clickAt(player, level, n64Pos, 13, 1.5, 3, Direction.NORTH); // the front, beside the ports
            check("N64 cart inserted", !console.cartridge().isEmpty());

            hold(player, ItemStack.EMPTY);
            clickAt(player, level, n64Pos, 12.25, 3.4, 5.25, Direction.UP); // POWER, the slider on the viewer's left
            check("Pixelon 64 powered on", console.powered());
            clickAt(player, level, n64Pos, 11, 1.1, 3, Direction.NORTH); // take port 1
            check("player holds the Pixelon 64's port 1", playerId.equals(console.holder(0)));
        } catch (RuntimeException e) {
            fail("n64: " + e);
            Cartridge.LOGGER.error("self-test Pixelon 64 setup failed", e);
        }
    }

    private static void vortex(MinecraftServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            vortexPos = base.offset(-3, 0, 2);
            vortexTvPos = base.offset(-3, 0, 3);
            level.setBlockAndUpdate(vortexPos, CartridgeBlocks.CONSOLE_VORTEX.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(vortexTvPos, CartridgeBlocks.CRT_TV.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            ConsoleBlockEntity console = (ConsoleBlockEntity) level.getBlockEntity(vortexPos);
            check("a Vortex Drive is a VORTEX_DRIVE", console.kind() == ConsoleKind.VORTEX_DRIVE);

            hold(player, new ItemStack(CartridgeItems.AV_CABLE.get()));
            clickAt(player, level, vortexPos, 8, 4, 8.5, Direction.UP);
            clickAt(player, level, vortexTvPos, 9, 7, 0, Direction.NORTH);
            check("TV linked to the Vortex Drive", console.tvs().contains(vortexTvPos));

            hold(player, new ItemStack(CartridgeItems.CONTROLLER_16BIT.get()));
            clickAt(player, level, vortexPos, 11.9, 1.1, 3, Direction.NORTH);
            check("a Super Pixelon Pad doesn't fit", !console.hasPad(0));
            hold(player, new ItemStack(CartridgeItems.CONTROLLER_VORTEX.get()));
            clickAt(player, level, vortexPos, 11.9, 1.1, 3, Direction.NORTH); // port 1
            check("Vortex Pad in port 1", console.hasPad(0));

            hold(player, CartridgeItem.create(new CartridgeContents(CartridgeContents.SNES, romSha1, "snes", UUID.randomUUID())));
            clickAt(player, level, vortexPos, 7, 1.5, 3, Direction.NORTH);
            check("an SNES cart doesn't fit", console.cartridge().isEmpty());
            hold(player, writer(server).removeItemNoUpdate(CartridgeWriterBlockEntity.MAIN)); // the Genesis cart
            clickAt(player, level, vortexPos, 7, 1.5, 3, Direction.NORTH);
            check("Genesis cart inserted", !console.cartridge().isEmpty());

            hold(player, ItemStack.EMPTY);
            clickAt(player, level, vortexPos, 12.1, 3.1, 4.5, Direction.UP); // POWER, the slider on top
            check("Vortex Drive powered on", console.powered());
            clickAt(player, level, vortexPos, 11.9, 1.1, 3, Direction.NORTH); // take port 1
            check("player holds the Vortex Drive's port 1", playerId.equals(console.holder(0)));
        } catch (RuntimeException e) {
            fail("vortex: " + e);
            Cartridge.LOGGER.error("self-test Vortex Drive setup failed", e);
        }
    }

    /** The middle pixel of a console's picture, {r, g, b}. */
    private static int[] centre(BlockPos console) {
        return centre(ClientConsoles.view(console));
    }

    /** A pixel of a view's picture, {r, g, b}. */
    private static int[] pixel(ConsoleView view, int x, int y) {
        java.nio.ByteBuffer frame = java.nio.ByteBuffer.allocateDirect(640 * 480 * 4);
        SharedMemory shm = view == null || view.instance() == null ? null : view.instance().sharedMemory();
        var f = shm == null ? null : shm.readFrame(frame);
        if (f == null || x >= f.width() || y >= f.height()) return new int[] {-1, -1, -1};
        int i = (y * f.width() + x) * 4;
        return new int[] {frame.get(i) & 0xFF, frame.get(i + 1) & 0xFF, frame.get(i + 2) & 0xFF};
    }

    /**
     * Server thread: a Pocketron with a copy of the Game Boy cart in the off hand, linked to the Pocketron Color in the
     * main hand (D177; one player's two hands stand in for two players), then played on port 1.
     */
    private static void linkHands(MinecraftServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            CartridgeContents game = CartridgeComponents.CARTRIDGE.get(gameBoyCart);
            ItemStack brick = new ItemStack(CartridgeItems.POCKETRON.get());
            CartridgeComponents.HANDHELD.set(brick, new HandheldState(UUID.randomUUID(), false, 0));
            CartridgeComponents.setContents(brick, List.of(CartridgeItem.create(
                    new CartridgeContents(game.system(), game.romSha1(), game.title(), UUID.randomUUID()))));
            HandheldItem.offhandAllowed = true; // only here (D187)
            player.setItemSlot(EquipmentSlot.OFFHAND, brick);
            Handhelds.link(player, player.getMainHandItem(), player, player.getOffhandItem());
            HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(player.getMainHandItem());
            check("linked: the main hand's is side 1", link != null && link.side() == 0);
            check("linked: the off hand's is side 2", CartridgeComponents.HANDHELD_LINK.has(player.getOffhandItem()));
            player.gameMode.useItem(player, server.overworld(), player.getMainHandItem(), InteractionHand.MAIN_HAND);
        } catch (RuntimeException e) {
            fail("link: " + e);
            Cartridge.LOGGER.error("self-test link setup failed", e);
        }
    }

    /** The picture's width and height, or zeros. */
    private static int[] frameSize(ConsoleView view) {
        java.nio.ByteBuffer frame = java.nio.ByteBuffer.allocateDirect(640 * 480 * 4);
        SharedMemory shm = view == null || view.instance() == null ? null : view.instance().sharedMemory();
        var f = shm == null ? null : shm.readFrame(frame);
        return f == null ? new int[2] : new int[] {f.width(), f.height()};
    }

    /** Switches the hotbar slot, as the number keys do. */
    private static void select(Minecraft mc, int slot) {
        Compat.setSelectedSlot(mc.player.getInventory(), slot);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    /**
     * Server thread: a Pocketron Color with the burned Game Boy cart in it, in the main hand, and a right-click: it
     * switches on and the player plays it (D164). Color-only games are refused by the Pocketron, the adapter too.
     */
    private static void pocketron(MinecraftServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ItemStack cart = writer(server).removeItemNoUpdate(CartridgeWriterBlockEntity.MAIN);
            gameBoyCart = cart.copy();
            HandheldItem brick = CartridgeItems.POCKETRON.get();
            check("the Pocketron refuses a Color-only game", !brick.takes(CartridgeItem.create(
                    new CartridgeContents(CartridgeContents.GAME_BOY_COLOR, romSha1, "c", UUID.randomUUID()))));
            check("the Pocketron takes a game that also plays in color", brick.takes(cart));
            check("the adapter refuses a Color-only game", !PocketronAdapterItem.fits(CartridgeItem.create(
                    new CartridgeContents(CartridgeContents.GAME_BOY_COLOR, romSha1, "c", UUID.randomUUID()))));
            ItemStack handheld = new ItemStack(CartridgeItems.POCKETRON_COLOR.get());
            CartridgeComponents.setContents(handheld, List.of(cart, ItemStack.EMPTY));
            Compat.setSelectedSlot(player.getInventory(), 0);
            hold(player, handheld);
            player.gameMode.useItem(player, server.overworld(), handheld, InteractionHand.MAIN_HAND);
            HandheldState state = HandheldItem.state(player.getMainHandItem());
            check("the Pocketron switched on", state != null && state.powered());
        } catch (RuntimeException e) {
            fail("pocketron: " + e);
            Cartridge.LOGGER.error("self-test Pocketron setup failed", e);
        }
    }

    /**
     * Server thread: a Pocketron Advance (or SP) with a copy of the burned Game Boy Advance cart in it, in the main
     * hand, and a right-click to play (D183). Only Advance carts fit, and neither takes a Link Cable (D184).
     */
    private static void advance(MinecraftServer server, UUID playerId, HandheldItem item) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            if (advanceCart == null) advanceCart = writer(server).removeItemNoUpdate(CartridgeWriterBlockEntity.MAIN).copy();
            check(item + " refuses a Game Boy cart", !item.takes(gameBoyCart));
            check(item + " takes the Advance cart", item.takes(advanceCart));
            check("the Pocketron Color refuses an Advance cart", !CartridgeItems.POCKETRON_COLOR.get().takes(advanceCart));
            check(item + " takes no Link Cable", !item.linkable());
            CartridgeContents game = CartridgeComponents.CARTRIDGE.get(advanceCart);
            ItemStack handheld = new ItemStack(item);
            CartridgeComponents.setContents(handheld, List.of(CartridgeItem.create(
                    new CartridgeContents(game.system(), game.romSha1(), game.title(), UUID.randomUUID()))));
            Compat.setSelectedSlot(player.getInventory(), 0);
            hold(player, handheld);
            player.gameMode.useItem(player, server.overworld(), handheld, InteractionHand.MAIN_HAND);
            HandheldState state = HandheldItem.state(player.getMainHandItem());
            check(item + " switched on", state != null && state.powered());
        } catch (RuntimeException e) {
            fail("advance: " + e);
            Cartridge.LOGGER.error("self-test Pocketron Advance setup failed", e);
        }
    }

    /**
     * Server thread: a Super Pixelon with the Pocketron Adapter in it, a copy of the Game Boy cart in the adapter
     * (D166), a TV and a Super Pixelon Pad; powered on and played.
     */
    private static void adapter(MinecraftServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            player.getInventory().setItem(0, ItemStack.EMPTY); // the Pocketron, put away for good
            adapterPos = base.offset(-5, 0, 2);
            BlockPos tv = base.offset(-5, 0, 3);
            level.removeBlock(adapterPos, false); // a fresh one: the world is kept between runs
            level.setBlockAndUpdate(adapterPos, CartridgeBlocks.CONSOLE_16BIT.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(tv, CartridgeBlocks.CRT_TV.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            ConsoleBlockEntity console = (ConsoleBlockEntity) level.getBlockEntity(adapterPos);
            hold(player, new ItemStack(CartridgeItems.AV_CABLE.get()));
            clickAt(player, level, adapterPos, 8, 2, 8, Direction.UP);
            clickAt(player, level, tv, 9, 7, 0, Direction.NORTH);
            hold(player, new ItemStack(CartridgeItems.CONTROLLER_16BIT.get()));
            clickAt(player, level, adapterPos, 12.25, 1.1, 3, Direction.NORTH); // port 1

            hold(player, gameBoyCart.copy());
            clickAt(player, level, adapterPos, 8, 2.5, 3, Direction.NORTH);
            check("a Game Boy cart doesn't go straight into the Super Pixelon", console.cartridge().isEmpty());
            ItemStack adapter = new ItemStack(CartridgeItems.POCKETRON_ADAPTER.get());
            CartridgeContents game = CartridgeComponents.CARTRIDGE.get(gameBoyCart);
            CartridgeComponents.setContents(adapter, List.of(CartridgeItem.create(
                    new CartridgeContents(game.system(), game.romSha1(), game.title(), UUID.randomUUID()))));
            hold(player, adapter);
            clickAt(player, level, adapterPos, 8, 2.5, 3, Direction.NORTH);
            check("the loaded adapter went in", PocketronAdapterItem.isAdapter(console.cartridge()));
            check("with the adapter in, the Super Pixelon is a Super Game Boy", console.kind() == ConsoleKind.POCKETRON_ADAPTER);

            hold(player, ItemStack.EMPTY);
            clickAt(player, level, adapterPos, 10.75, 4, 5.25, Direction.UP); // POWER
            clickAt(player, level, adapterPos, 12.25, 1.1, 3, Direction.NORTH); // take port 1
            check("player holds the Super Game Boy's port 1", playerId.equals(console.holder(0)));
        } catch (RuntimeException e) {
            fail("adapter: " + e);
            Cartridge.LOGGER.error("self-test adapter setup failed", e);
        }
    }

    /** The middle pixel of a view's picture (a console's or a handheld's), {r, g, b}. */
    private static int[] centre(ConsoleView view) {
        java.nio.ByteBuffer frame = java.nio.ByteBuffer.allocateDirect(640 * 480 * 4);
        SharedMemory shm = view == null || view.instance() == null ? null : view.instance().sharedMemory();
        var f = shm == null ? null : shm.readFrame(frame);
        if (f == null || f.width() <= 0) return new int[] {-1, -1, -1};
        int i = (f.height() / 2 * f.width() + f.width() / 2) * 4;
        return new int[] {frame.get(i) & 0xFF, frame.get(i + 1) & 0xFF, frame.get(i + 2) & 0xFF};
    }

    /**
     * Server thread: a Flatscreen TV on its stand, a 1x1 and a Large Flatscreen hung on a wall, and a 3 x 2 video wall
     * of Display Panels (D129, D131), all linked to the console (the wall through one middle panel).
     */
    private static void displays(MinecraftServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-6, 0, 4), base.offset(6, 4, 7))) level.removeBlock(p, false);
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-6, 0, 8), base.offset(6, 4, 8))) {
                level.setBlockAndUpdate(p, net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
            }
            BlockPos stand = base.offset(-1, 0, 4), small = base.offset(0, 2, 7);
            largePos = base.offset(3, 1, 7);
            wallPos = base.offset(-3, 2, 7); // top middle panel
            level.setBlockAndUpdate(stand, CartridgeBlocks.FLATSCREEN_TV.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            level.setBlockAndUpdate(small, CartridgeBlocks.FLATSCREEN_TV.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(FlatscreenTvBlock.MOUNT, DisplayMount.WALL));
            var large = CartridgeBlocks.LARGE_FLATSCREEN_TV.get().defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH).setValue(FlatscreenTvBlock.MOUNT, DisplayMount.WALL);
            level.setBlockAndUpdate(largePos, large);
            large.getBlock().setPlacedBy(level, largePos, large, player, ItemStack.EMPTY);
            for (int dx = -4; dx <= -2; dx++) {
                for (int dy = 1; dy <= 2; dy++) {
                    BlockPos p = base.offset(dx, dy, 7);
                    var panel = CartridgeBlocks.DISPLAY_PANEL.get().defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
                    level.setBlockAndUpdate(p, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(panel, level, p));
                }
            }
            check("Large Flatscreen fills six spaces", level.getBlockState(largePos.relative(Direction.EAST).above())
                    .is(CartridgeBlocks.LARGE_FLATSCREEN_TV.get()));
            ConsoleBlockEntity console = (ConsoleBlockEntity) level.getBlockEntity(consolePos);
            hold(player, new ItemStack(CartridgeItems.AV_CABLE.get(), 8));
            for (BlockPos display : List.of(stand, small, largePos.above().relative(Direction.WEST), wallPos)) {
                click(player, level, consolePos, 8, 4, Direction.UP);
                click(player, level, display, 8, 8, Direction.NORTH);
            }
            check("all four displays linked", console.tvs().containsAll(List.of(stand, small, largePos, wallPos)));
            check("Large Flatscreen lit in every space", level.getBlockState(largePos.relative(Direction.WEST).above()).getValue(DisplayBlock.LIT));
            check("whole wall lit", level.getBlockState(base.offset(-4, 1, 7)).getValue(DisplayBlock.LIT));
            hold(player, ItemStack.EMPTY);
        } catch (RuntimeException e) {
            fail("displays: " + e);
            Cartridge.LOGGER.error("self-test displays setup failed", e);
        }
    }

    /** Server thread: link the TV, plug a pad, insert the burned cart, power on and take the pad. */
    private static void play(MinecraftServer server, UUID playerId) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(playerId);
            ServerLevel level = server.overworld();
            ConsoleBlockEntity console = (ConsoleBlockEntity) level.getBlockEntity(consolePos);
            TvBlockEntity tv = (TvBlockEntity) level.getBlockEntity(tvPos);

            hold(player, new ItemStack(CartridgeItems.AV_CABLE.get(), 2));
            click(player, level, consolePos, 8, 4, Direction.UP);
            click(player, level, tvPos, 9, 7, Direction.NORTH);
            check("TV linked", consolePos.equals(tv.console()) && console.tvs().contains(tvPos));

            hold(player, new ItemStack(CartridgeItems.CONTROLLER_8BIT.get()));
            click(player, level, consolePos, 4.25, 0.75, Direction.NORTH); // port 1
            check("pad plugged into port 1", console.hasPad(0) && !console.hasPad(1));

            hold(player, writer(server).removeItemNoUpdate(CartridgeWriterBlockEntity.SOURCE)); // the first burned cart
            click(player, level, consolePos, 8, 3, Direction.NORTH); // body
            check("cartridge inserted", !console.cartridge().isEmpty());

            hold(player, ItemStack.EMPTY);
            click(player, level, consolePos, 12.6, 0.7, Direction.NORTH); // POWER
            check("powered on", console.powered());
            click(player, level, consolePos, 4.25, 0.75, Direction.NORTH); // take port 1
            check("player holds port 1", playerId.equals(console.holder(0)));
        } catch (RuntimeException e) {
            fail("play: " + e);
            Cartridge.LOGGER.error("self-test play setup failed", e);
        }
    }

    private static void hold(ServerPlayer player, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
    }

    /** A click on a north-facing block at model pixel (x, y, z) on the given face. */
    private static void clickAt(ServerPlayer player, ServerLevel level, BlockPos pos, double x, double y, double z, Direction face) {
        Vec3 at = new Vec3(pos.getX() + x / 16, pos.getY() + y / 16, pos.getZ() + z / 16);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(at, face, pos, false));
    }

    /** A click on a north-facing block at model pixel (x, y) of the given face. */
    private static void click(ServerPlayer player, ServerLevel level, BlockPos pos, double x, double y, Direction face) {
        double z = face == Direction.UP ? 8 : pos.equals(consolePos) ? 3 : pos.equals(writerPos) ? 2 : 0;
        double py = face == Direction.UP ? 4 : y;
        Vec3 at = new Vec3(pos.getX() + x / 16, pos.getY() + py / 16, pos.getZ() + z / 16);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(at, face, pos, false));
    }

    private static void audioLog(Minecraft mc, String when) {
        ConsoleView view = ClientConsoles.view(consolePos);
        ConsoleInstance instance = view != null ? view.instance() : null;
        log("audio " + when + ": " + (instance == null ? "no instance" : instance.audio().describe()));
    }

    private static void lookAt(Minecraft mc, Vec3 target) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 d = target.subtract(eye);
        mc.player.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
    }

    private static void shot(Minecraft mc, String name) {
        DevHooks.screenshot("selftest-" + name + ".png");
        log("screenshot " + name);
    }

    private static void check(String what, boolean ok) {
        log((ok ? "ok   " : "FAIL ") + what);
    }

    private static void fail(String why) {
        log("FAIL " + why);
        log("RESULT: FAILED");
        write();
        step = -2;
        Minecraft.getInstance().stop();
    }

    private static synchronized void log(String line) {
        Cartridge.LOGGER.info("[self-test] {}", line);
        LOG.add(line);
    }

    private static synchronized void write() {
        try {
            Files.write(dir().resolve("selftest-result.txt"), LOG);
        } catch (IOException e) {
            Cartridge.LOGGER.error("self-test: couldn't write the result", e);
        }
    }
}
