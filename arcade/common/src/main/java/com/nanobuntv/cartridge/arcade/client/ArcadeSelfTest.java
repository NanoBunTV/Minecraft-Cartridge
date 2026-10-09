package com.nanobuntv.cartridge.arcade.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nanobuntv.cartridge.util.Compat;
import com.nanobuntv.cartridge.arcade.ArcadeContent;
import com.nanobuntv.cartridge.arcade.CartridgeArcade;
import com.nanobuntv.cartridge.arcade.block.ArcadeCabinetBlock;
import com.nanobuntv.cartridge.arcade.block.ArcadeCabinetBlockEntity;
import com.nanobuntv.cartridge.block.CartridgeBlocks;
import com.nanobuntv.cartridge.blockentity.CartridgeWriterBlockEntity;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.blockentity.TvBlockEntity;
import com.nanobuntv.cartridge.client.DevHooks;
import com.nanobuntv.cartridge.emu.RomCache;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.menu.ConsoleMenu;
import com.nanobuntv.cartridge.platform.Services;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Dev runs only: Cartridge: Arcade end to end, without anyone at the keyboard, when
 * {@code <run dir>/cartridge_dev/arcade-selftest} exists. Romsets come from the folder in {@code CARTRIDGE_ARCADE_ROMS}
 * (else {@code cartridge_dev/}): pacman.zip and sf2.zip, the tester's own. It burns Arcade Boards through the Writer's
 * real check (D206) and upload, slides them into a 1- and a 2-player cabinet, switches them on in the GUI (D203),
 * takes the controls at the panel (D202), coins up and starts, feeds a TV with the AV Cable, checks the dye (D204)
 * and breaking. Screenshots go to {@code screenshots/arcade-*.png}, the result to
 * {@code cartridge_dev/arcade-selftest-result.txt}; then it quits.
 */
public final class ArcadeSelfTest {
    private static final String WORLD = "cartridge-arcade-selftest";
    private static final int BLUE = 0x2050C0;
    private static int step = -1, wait, waited;
    private static final List<String> LOG = new ArrayList<>();
    private static BlockPos base, writerPos, cabinetPos, cabinet2Pos, tvPos;
    private static Path pacman, sf2;
    private static ItemStack pacmanBoard = ItemStack.EMPTY, sf2Board = ItemStack.EMPTY;

    private ArcadeSelfTest() {}

    private static Path dir() {
        return Services.PLATFORM.gameDir().resolve("cartridge_dev");
    }

    private static Path roms() {
        String env = System.getenv("CARTRIDGE_ARCADE_ROMS");
        return env != null && Files.isDirectory(Path.of(env)) ? Path.of(env) : dir();
    }

    public static void tick() {
        if (step == -2) return;
        Minecraft mc = Minecraft.getInstance();
        if (step == -1) {
            if (!Services.PLATFORM.isDevelopmentEnvironment() || !Files.exists(dir().resolve("arcade-selftest"))) {
                step = -2;
                return;
            }
            if (!(mc.screen instanceof TitleScreen)) return;
            pacman = roms().resolve("pacman.zip");
            sf2 = roms().resolve("sf2.zip");
            if (!Files.exists(pacman) || !Files.exists(sf2)) {
                step = 0;
                fail("needs pacman.zip and sf2.zip in " + roms());
                return;
            }
            step = 0;
            log("arcade self-test starting on " + Services.PLATFORM.loaderName() + ", romsets from " + roms());
            mc.options.pauseOnLostFocus = false;
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
            CartridgeArcade.LOGGER.error("arcade self-test failed", e);
            fail(e.toString());
        }
    }

    private static boolean timeout(int ticks, String what) {
        if (++waited > ticks) {
            fail("timed out: " + what);
            return true;
        }
        return false;
    }

    private static void next(int delay) {
        waited = 0;
        wait = delay;
        step++;
    }

    private static void run(Minecraft mc) throws Exception {
        MinecraftServer server = mc.getSingleplayerServer();
        UUID id = mc.player == null ? null : mc.player.getUUID();
        switch (step) {
            case 0 -> {
                if (mc.player == null || server == null || mc.screen != null) return;
                next(40);
            }
            case 1 -> {
                server.execute(() -> build(server, id));
                next(20);
            }
            case 2 -> { // the Writer is open with a blank board: a romset missing a file first (D206)
                if (!(mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) {
                    timeout(100, "the Writer screen");
                    return;
                }
                DevHooks.writerPick(withoutFirstFile(sf2));
                next(5);
            }
            case 3 -> {
                if (DevHooks.writerBusy() && !timeout(1200, "checking a broken set")) return;
                String status = DevHooks.writerStatus();
                log("Writer says: " + status);
                check("a set with a file missing is from another MAME version", status.contains("another MAME version"));
                DevHooks.writerPick(pacman);
                next(5);
            }
            case 4 -> {
                if (DevHooks.writerBusy() && !timeout(1200, "checking pacman.zip")) return;
                String status = DevHooks.writerStatus();
                log("Writer says: " + status);
                check("MAME runs pacman.zip", status.contains("Ready to burn"));
                shot(mc, "1-writer-checked");
                DevHooks.writerBurn();
                next(5);
            }
            case 5 -> {
                ItemStack main = clientWriter(mc).getItem(CartridgeWriterBlockEntity.MAIN);
                if (!main.is(ArcadeContent.BOARD.get()) && !timeout(600, "burning pacman: " + DevHooks.writerStatus())) return;
                CartridgeContents game = CartridgeComponents.CARTRIDGE.get(main);
                check("burned onto an Arcade Board", main.is(ArcadeContent.BOARD.get()) && game != null);
                check("the board's system is mame/pacman", game != null && game.system().equals("mame/pacman"));
                check("titled as MAME names it", game != null && game.title().startsWith("Pac-Man"));
                log("board title: " + (game == null ? "" : game.title()));
                pacmanBoard = main.copy();
                server.execute(() -> writer(server).setItem(CartridgeWriterBlockEntity.MAIN, new ItemStack(ArcadeContent.BLANK_BOARD.get())));
                next(10);
            }
            case 6 -> {
                DevHooks.writerPick(sf2);
                next(5);
            }
            case 7 -> {
                if (DevHooks.writerBusy() && !timeout(1200, "checking sf2.zip")) return;
                check("MAME runs sf2.zip", DevHooks.writerStatus().contains("Ready to burn"));
                DevHooks.writerBurn();
                next(5);
            }
            case 8 -> {
                ItemStack main = clientWriter(mc).getItem(CartridgeWriterBlockEntity.MAIN);
                if (!main.is(ArcadeContent.BOARD.get()) && !timeout(600, "burning sf2")) return;
                sf2Board = main.copy();
                // Closing reaches the server as a packet: let it arrive before the cabinet's GUI opens, or it closes that.
                mc.player.closeContainer();
                step = 80;
                wait = 5;
            }
            case 80 -> {
                server.execute(() -> insertAndOpen(server, id, cabinetPos, pacmanBoard));
                step = 9;
                wait = 20;
            }
            case 9 -> { // the cabinet's GUI: POWER is here (D203)
                if (!(mc.player.containerMenu instanceof ConsoleMenu menu)) {
                    timeout(100, "the cabinet's GUI");
                    return;
                }
                check("the GUI has the POWER button", menu.powerInGui());
                check("the board is in", ConsoleBlockEntity.isCartridge(menu.getSlot(0).getItem()));
                shot(mc, "2-cabinet-gui");
                mc.gameMode.handleInventoryButtonClick(menu.containerId, ConsoleMenu.BUTTON_POWER);
                next(10);
            }
            case 10 -> {
                check("powered on", clientConsole(mc, cabinetPos).powered());
                mc.player.closeContainer();
                next(5);
            }
            case 11 -> { // MAME may be unpacking (372 MB) the first time
                if (!DevHooks.consoleState(cabinetPos).equals("PLAYING") && !timeout(2400, "Pac-Man to run: "
                        + DevHooks.consoleState(cabinetPos))) {
                    return;
                }
                int[] size = DevHooks.frameSize(cabinetPos);
                log("picture " + size[0] + "x" + size[1]);
                check("Pac-Man stands upright", size[0] == 224 && size[1] == 288);
                look(mc, cabinetPos, 0.5, 1.3, -1.6);
                next(40);
            }
            case 12 -> {
                shot(mc, "3-cabinet-on");
                server.execute(() -> click(server, id, cabinetPos, 8, 13.5, 3, Direction.UP)); // the panel (D202)
                next(440); // Pac-Man's own boot test takes about 20 s; a coin before then doesn't count
            }
            case 13 -> {
                if (!DevHooks.playing(cabinetPos) && !timeout(100, "play mode")) return;
                check("the panel took Player 1", id.equals(clientConsole(mc, cabinetPos).holder(0)));
                DevHooks.key(InputConstants.KEY_5, true); // Coin, then Start (D199)
                next(6);
            }
            case 14 -> {
                DevHooks.key(InputConstants.KEY_5, false);
                next(60);
            }
            case 15 -> {
                DevHooks.key(InputConstants.KEY_RETURN, true);
                next(6);
            }
            case 16 -> {
                DevHooks.key(InputConstants.KEY_RETURN, false);
                next(200);
            }
            case 17 -> {
                shot(mc, "4-playing");
                DevHooks.key(InputConstants.KEY_ESCAPE, true);
                DevHooks.key(InputConstants.KEY_ESCAPE, false);
                next(10);
            }
            case 18 -> {
                check("left play mode", !DevHooks.playing(cabinetPos));
                server.execute(() -> insertAndOpen(server, id, cabinet2Pos, sf2Board));
                next(20);
            }
            case 19 -> {
                if (!(mc.player.containerMenu instanceof ConsoleMenu menu)) {
                    timeout(100, "the 2-player cabinet's GUI");
                    return;
                }
                mc.gameMode.handleInventoryButtonClick(menu.containerId, ConsoleMenu.BUTTON_POWER);
                next(10);
            }
            case 20 -> {
                mc.player.closeContainer();
                if (!DevHooks.consoleState(cabinet2Pos).equals("PLAYING") && !timeout(2400, "Street Fighter II to run")) return;
                int[] size = DevHooks.frameSize(cabinet2Pos);
                check("Street Fighter II lies wide", size[0] == 384 && size[1] == 224);
                server.execute(() -> click(server, id, cabinet2Pos, -8, 13.5, 3, Direction.UP)); // the right half
                next(20);
            }
            case 21 -> {
                check("the right half is Player 2", id.equals(clientConsole(mc, cabinet2Pos).holder(1)));
                look(mc, cabinet2Pos, 0, 1.3, -2.2);
                next(60);
            }
            case 22 -> {
                shot(mc, "5-two-player");
                DevHooks.key(InputConstants.KEY_ESCAPE, true);
                DevHooks.key(InputConstants.KEY_ESCAPE, false);
                check("the dye stayed on the cabinet", ((ArcadeCabinetBlockEntity) clientConsole(mc, cabinet2Pos)).color() == BLUE);
                server.execute(() -> link(server, id));
                next(60);
            }
            case 23 -> {
                TvBlockEntity tv = (TvBlockEntity) mc.level.getBlockEntity(tvPos);
                check("the AV Cable links the cabinet to a TV", tv != null && cabinetPos.equals(tv.console()));
                look(mc, tvPos, 0.5, 0.45, -0.6);
                next(40);
            }
            case 24 -> {
                shot(mc, "6-tv");
                server.execute(() -> breakUpper(server, id));
                next(20);
            }
            case 25 -> {
                var level = mc.level;
                check("breaking the top breaks the cabinet", level.getBlockState(cabinetPos).isAir()
                        && level.getBlockState(cabinetPos.above()).isAir());
                List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(cabinetPos).inflate(3));
                check("it drops the cabinet", drops.stream().anyMatch(e -> e.getItem().is(ArcadeContent.CABINET_ITEM.get())));
                check("and the board", drops.stream().anyMatch(e -> e.getItem().is(ArcadeContent.BOARD.get())));
                log("RESULT: " + (LOG.stream().anyMatch(l -> l.startsWith("FAIL")) ? "FAILED" : "PASSED"));
                write();
                step = -2;
                mc.stop();
            }
            default -> {}
        }
    }

    // --- Server side ------------------------------------------------------------------------------------------------

    private static CartridgeWriterBlockEntity clientWriter(Minecraft mc) {
        return (CartridgeWriterBlockEntity) mc.level.getBlockEntity(writerPos);
    }

    private static ConsoleBlockEntity clientConsole(Minecraft mc, BlockPos pos) {
        return (ConsoleBlockEntity) mc.level.getBlockEntity(pos);
    }

    private static CartridgeWriterBlockEntity writer(MinecraftServer server) {
        return (CartridgeWriterBlockEntity) server.overworld().getBlockEntity(writerPos);
    }

    private static ConsoleBlockEntity console(MinecraftServer server, BlockPos pos) {
        return (ConsoleBlockEntity) server.overworld().getBlockEntity(pos);
    }

    /** A Writer, a 1-player cabinet, a blue 2-player cabinet and a TV, all facing the player; the Writer opened. */
    private static void build(MinecraftServer server, UUID id) {
        try {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            ServerLevel level = server.overworld();
            base = player.blockPosition();
            writerPos = base.offset(-1, 0, 2);
            cabinetPos = base.offset(1, 0, 3);
            cabinet2Pos = base.offset(4, 0, 3);
            tvPos = base.offset(-3, 0, 3);
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-5, 0, -2), base.offset(6, 3, 6))) level.removeBlock(p, false);
            for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, new AABB(base).inflate(8))) e.discard();
            player.teleportTo(base.getX() + 0.5, base.getY(), base.getZ() + 0.5);
            level.setBlockAndUpdate(writerPos, facing(CartridgeBlocks.CARTRIDGE_WRITER.get().defaultBlockState()));
            level.setBlockAndUpdate(tvPos, facing(CartridgeBlocks.CRT_TV.get().defaultBlockState()));
            place(level, player, cabinetPos, ArcadeContent.CABINET.get(), new ItemStack(ArcadeContent.CABINET_ITEM.get()));
            ItemStack blue = new ItemStack(ArcadeContent.CABINET_2P_ITEM.get());
            Compat.setDye(blue, BLUE);
            place(level, player, cabinet2Pos, ArcadeContent.CABINET_2P.get(), blue);
            // Start from an empty store so the uploads really happen (the world is reused between runs).
            Path store = server.getWorldPath(LevelResource.ROOT).resolve("cartridge_mod").resolve("roms");
            for (Path p : List.of(pacman, sf2)) Files.deleteIfExists(store.resolve(RomCache.sha1(Files.readAllBytes(p))));
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            clickAt(player, level, writerPos, 8, 3, 2, Direction.NORTH); // opens the Writer
            writer(server).setItem(CartridgeWriterBlockEntity.MAIN, new ItemStack(ArcadeContent.BLANK_BOARD.get()));
        } catch (IOException | RuntimeException e) {
            CartridgeArcade.LOGGER.error("arcade self-test build failed", e);
            fail("build: " + e);
        }
    }

    private static BlockState facing(BlockState state) {
        return state.setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
    }

    /** As placing it by hand does: the anchor, then the other spaces and the colour (setPlacedBy). */
    private static void place(ServerLevel level, ServerPlayer player, BlockPos pos, ArcadeCabinetBlock block, ItemStack item) {
        BlockState state = facing(block.defaultBlockState());
        level.setBlockAndUpdate(pos, state);
        block.setPlacedBy(level, pos, state, player, item);
        check("placed " + block.getName().getString() + " in " + (block.columns() * 2) + " spaces",
                level.getBlockState(pos.above()).is(block)
                        && (block.columns() == 1 || level.getBlockState(pos.west().above()).is(block)));
    }

    /** The board slides in with a right-click (D203); then a click elsewhere on the body opens the GUI. */
    private static void insertAndOpen(MinecraftServer server, UUID id, BlockPos cabinet, ItemStack board) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        ServerLevel level = server.overworld();
        player.setItemInHand(InteractionHand.MAIN_HAND, board.copy());
        clickAt(player, level, cabinet.above(), 8, 4, 7, Direction.NORTH);
        check("the board went in", !console(server, cabinet).cartridge().isEmpty());
        clickAt(player, level, cabinet, 8, 5, 4, Direction.NORTH); // below the panel: the GUI
    }

    private static void click(MinecraftServer server, UUID id, BlockPos anchor, double x, double y, double z, Direction face) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        clickAt(player, server.overworld(), anchor, x, y, z, face);
    }

    /** A click at a north-facing block's model pixel (x may run into the next space, as the 2-player panel does). */
    private static void clickAt(ServerPlayer player, ServerLevel level, BlockPos pos, double x, double y, double z, Direction face) {
        Vec3 at = new Vec3(pos.getX() + x / 16, pos.getY() + y / 16, pos.getZ() + z / 16);
        BlockPos hit = BlockPos.containing(at.x, at.y - (face == Direction.UP ? 0.001 : 0), at.z);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(at, face, hit, false));
    }

    /** The AV Cable from the cabinet's top half (it links from the anchor) to the TV (D198). */
    private static void link(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        ServerLevel level = server.overworld();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(CartridgeItems.AV_CABLE.get()));
        clickAt(player, level, cabinetPos.above(), 8, 8, 7, Direction.NORTH);
        clickAt(player, level, tvPos, 8, 8, 1, Direction.NORTH);
    }

    /** Breaking the top half in survival breaks the whole cabinet, which drops itself and its board. */
    private static void breakUpper(MinecraftServer server, UUID id) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.gameMode.destroyBlock(cabinetPos.above());
        player.setGameMode(GameType.CREATIVE);
    }

    // --- Helpers ----------------------------------------------------------------------------------------------------

    /** The romset with its first file left out: MAME then names it missing (D206). */
    private static Path withoutFirstFile(Path set) throws IOException {
        Path out = dir().resolve("broken").resolve(set.getFileName());
        Files.createDirectories(out.getParent());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipFile zip = new ZipFile(set.toFile()); ZipOutputStream o = new ZipOutputStream(bytes)) {
            var entries = zip.stream().filter(e -> !e.isDirectory()).toList();
            for (ZipEntry e : entries.subList(1, entries.size())) {
                o.putNextEntry(new ZipEntry(e.getName()));
                zip.getInputStream(e).transferTo(o);
                o.closeEntry();
            }
        }
        Files.write(out, bytes.toByteArray());
        return out;
    }

    /** Stands the player before a point (offsets from a block's corner) and looks at it. */
    private static void look(Minecraft mc, BlockPos pos, double x, double y, double back) {
        Vec3 target = new Vec3(pos.getX() + x, pos.getY() + y, pos.getZ());
        Vec3 stand = new Vec3(target.x, pos.getY(), pos.getZ() + back);
        mc.player.setPos(stand.x, stand.y, stand.z);
        Vec3 d = target.subtract(mc.player.getEyePosition());
        mc.player.setYRot((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
    }

    private static void shot(Minecraft mc, String name) {
        DevHooks.screenshot("arcade-" + name + ".png");
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
        CartridgeArcade.LOGGER.info("[arcade self-test] {}", line);
        LOG.add(line);
    }

    private static synchronized void write() {
        try {
            Files.createDirectories(dir());
            Files.write(dir().resolve("arcade-selftest-result.txt"), LOG);
        } catch (IOException e) {
            CartridgeArcade.LOGGER.error("arcade self-test: couldn't write the result", e);
        }
    }
}
