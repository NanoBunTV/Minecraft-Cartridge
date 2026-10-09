package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.blockentity.CartridgeWriterBlockEntity;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.emu.RomCache;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.GameMedia;
import com.nanobuntv.cartridge.menu.WriterMenu;
import com.nanobuntv.cartridge.net.RomUploadCancelPayload;
import com.nanobuntv.cartridge.net.RomUploadChunkPayload;
import com.nanobuntv.cartridge.net.RomUploadStartPayload;
import com.nanobuntv.cartridge.net.RomUploadStatusPayload;
import com.nanobuntv.cartridge.net.SaveExportPayload;
import com.nanobuntv.cartridge.net.SaveFilePayload;
import com.nanobuntv.cartridge.net.SaveImportPayload;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.rom.RomFormats;
import com.nanobuntv.cartridge.save.SaveStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
//? if >=1.21.9 {
/*import net.minecraft.client.input.KeyEvent;
*///?}

/**
 * The Cartridge Writer's GUI (D15, D77, D78, D81). What it offers depends on the slots: a blank alone gets a title
 * field, "Choose file…" (the OS dialog, or drop a file on the window) and "Burn"; a blank plus a burned cart in the
 * source slot gets "Copy"; a burned cart in the main slot gets "Erase" (click twice), and "Import" / "Export" for its
 * battery save as a {@code .sav} file (D97, save/SaveServer). Burning uploads the file in acknowledged pieces
 * (rom/RomServer); a disk game on a server without the BIOS asks for {@code disksys.rom} first (D74). A blank of an
 * add-on's media burns games through its {@link GamePreparer} (an Arcade Board's romset is test-booted in MAME, which
 * may ask for its parent's or BIOS's zip, D206).
 */
public class WriterScreen extends CartridgeContainerScreen<WriterMenu> {
    private static final ResourceLocation BACKGROUND = Cartridge.id("textures/gui/container/cartridge_writer.png");
    private static final int UPLOAD_WINDOW = 512 * 1024;
    private static final long ERASE_CONFIRM_NANOS = 3_000_000_000L;
    private static final int STATUS_Y = 60, STATUS_WIDTH = 160;
    private static final int COLOR_TEXT = 0xFF404040, COLOR_ERROR = 0xFFA02020, COLOR_NOTICE = 0xFF2050A0;

    /** RESTORE (D124): a burned cart whose game file the server no longer has. */
    enum Mode { EMPTY, BURN, COPY, ERASE, RESTORE }

    /** A file the player picked: its bytes are read once, off the render thread. */
    private record Picked(Path path, byte[] data, String sha1, String system) {}

    @Nullable
    private Picked file;
    @Nullable
    private Picked upload;
    private int uploadKind, sent, acked;
    private boolean needBios;
    /** A game the media's {@link GamePreparer} is readying: the picked file, the extra files given so far, what it wants. */
    @Nullable
    private Path pending;
    private final List<Path> extras = new ArrayList<>();
    private List<String> wants = List.of();
    private boolean preparing;
    private Component status = Component.empty();
    private int statusColor = COLOR_TEXT;
    private long eraseArmedUntil;

    /** A battery save import or export is waiting for the server (D97). */
    private boolean saveBusy;

    private EditBox titleBox;
    private Button chooseButton, burnButton, actionButton, restoreButton, importButton, exportButton;

    public WriterScreen(WriterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 186);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        String title = titleBox != null ? titleBox.getValue() : "";
        titleBox = new EditBox(font, leftPos + 66, topPos + 18, 102, 14, Component.translatable("cartridge_mod.writer.title_field"));
        titleBox.setMaxLength(RomUploadStartPayload.MAX_TITLE);
        titleBox.setValue(title);
        titleBox.setHint(Component.translatable("cartridge_mod.writer.title_hint").withStyle(ChatFormatting.DARK_GRAY));
        chooseButton = Button.builder(Component.empty(), b -> choose()).bounds(leftPos + 66, topPos + 36, 72, 20).build();
        burnButton = Button.builder(Component.translatable("cartridge_mod.writer.burn"), b -> burn())
                .bounds(leftPos + 140, topPos + 36, 28, 20).build();
        actionButton = Button.builder(Component.empty(), b -> action()).bounds(leftPos + 66, topPos + 36, 102, 20).build();
        restoreButton = Button.builder(Component.translatable("cartridge_mod.writer.restore"), b -> chooseRestore())
                .bounds(leftPos + 66, topPos + 36, 66, 20)
                .tooltip(Tooltip.create(Component.translatable("cartridge_mod.writer.restore.tooltip"))).build();
        importButton = Button.builder(Component.translatable("cartridge_mod.writer.save.import"), b -> importSave())
                .bounds(leftPos + 66, topPos + 17, 50, 16)
                .tooltip(Tooltip.create(Component.translatable("cartridge_mod.writer.save.import_tooltip"))).build();
        exportButton = Button.builder(Component.translatable("cartridge_mod.writer.save.export"), b -> exportSave())
                .bounds(leftPos + 118, topPos + 17, 50, 16)
                .tooltip(Tooltip.create(Component.translatable("cartridge_mod.writer.save.export_tooltip"))).build();
        addRenderableWidget(titleBox);
        addRenderableWidget(chooseButton);
        addRenderableWidget(burnButton);
        addRenderableWidget(actionButton);
        addRenderableWidget(restoreButton);
        addRenderableWidget(importButton);
        addRenderableWidget(exportButton);
        updateWidgets();
    }

    Mode mode() {
        ItemStack main = menu.main();
        if (CartridgeWriterBlockEntity.isBurned(main)) {
            CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(main);
            return contents != null && RomAvailability.missing(contents.romSha1()) ? Mode.RESTORE : Mode.ERASE;
        }
        if (!CartridgeWriterBlockEntity.isBlank(main)) return Mode.EMPTY;
        return CartridgeWriterBlockEntity.isBurned(menu.source()) ? Mode.COPY : Mode.BURN;
    }

    boolean uploading() {
        return upload != null;
    }

    boolean needsBios() {
        return needBios;
    }

    Component status() {
        return status;
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.main().isEmpty() && !uploading()) { // the cart was taken out
            setStatus(Component.empty(), COLOR_TEXT);
            saveBusy = false;
            forgetPending();
        }
        updateWidgets();
    }

    private void updateWidgets() {
        Mode mode = mode();
        boolean busy = uploading();
        titleBox.visible = mode == Mode.BURN;
        titleBox.setEditable(!busy);
        chooseButton.visible = mode == Mode.BURN;
        chooseButton.active = !busy && !FilePicker.isOpen() && !preparing;
        chooseButton.setMessage(needBios ? Component.translatable("cartridge_mod.writer.choose_bios")
                : !wants.isEmpty() ? Component.translatable("cartridge_mod.writer.choose_extra", wants.get(0))
                : Component.translatable("cartridge_mod.writer.choose"));
        burnButton.visible = mode == Mode.BURN;
        burnButton.active = !busy && file != null && !needBios && !preparing;
        actionButton.visible = mode == Mode.COPY || mode == Mode.ERASE || mode == Mode.RESTORE;
        actionButton.active = !busy;
        // Restoring: Restore beside a narrower Erase (the whole row while it asks to confirm); else the action alone.
        boolean beside = mode == Mode.RESTORE && System.nanoTime() >= eraseArmedUntil;
        actionButton.setX(leftPos + (beside ? 134 : 66));
        actionButton.setWidth(beside ? 34 : 102);
        restoreButton.visible = beside;
        restoreButton.active = !busy && !FilePicker.isOpen();
        importButton.visible = exportButton.visible = mode == Mode.ERASE || mode == Mode.RESTORE;
        importButton.active = exportButton.active = !saveBusy && !FilePicker.isOpen();
        boolean armed = System.nanoTime() < eraseArmedUntil;
        actionButton.setMessage(Component.translatable(mode == Mode.COPY ? "cartridge_mod.writer.copy"
                : armed ? "cartridge_mod.writer.erase_confirm" : "cartridge_mod.writer.erase"));
    }

    // --- Actions ---------------------------------------------------------------------------------------------------

    private void choose() {
        if (needBios) {
            FilePicker.open(Component.translatable("cartridge_mod.writer.pick_bios").getString(),
                    new String[] {"*.rom", "*.bin"}, "disksys.rom", this::pickBios);
        } else if (!wants.isEmpty()) {
            FilePicker.open(Component.translatable("cartridge_mod.writer.pick_extra", wants.get(0)).getString(),
                    new String[] {wants.get(0)}, wants.get(0), this::pickExtra);
        } else {
            String[] patterns = media().map(GameMedia::filePatterns).orElse(RomFormats.FILE_PATTERNS);
            FilePicker.open(Component.translatable("cartridge_mod.writer.pick_game").getString(),
                    patterns, filterName(patterns), this::pickGame);
        }
    }

    /** The media of the blank in the main slot, if any. */
    private Optional<GameMedia> media() {
        return GameMedia.ofBlank(menu.main());
    }

    @Nullable
    private GamePreparer preparer() {
        return media().map(m -> GamePreparer.BY_MEDIA.get(m.id())).orElse(null);
    }

    private void forgetPending() {
        pending = null;
        extras.clear();
        wants = List.of();
    }

    /** A game file was picked or dropped. Also the self-test's way in (client/DevSelfTest). */
    void pickGame(Path path) {
        if (preparer() != null) {
            forgetPending();
            pending = path;
            file = null;
            prepare();
            return;
        }
        read(path, picked -> {
            file = picked;
            needBios = false;
            String name = path.getFileName().toString();
            String title = RomFormats.titleFromFileName(name); // D116
            titleBox.setValue(title.length() > RomUploadStartPayload.MAX_TITLE ? title.substring(0, RomUploadStartPayload.MAX_TITLE) : title);
            setStatus(Component.translatable("cartridge_mod.writer.file", name, kilobytes(picked.data().length),
                    Component.translatable("cartridge_mod.cartridge.system." + picked.system())), COLOR_TEXT);
        });
    }

    /** A further file the media's preparer asked for (an arcade romset's parent or BIOS, D206). */
    void pickExtra(Path path) {
        if (pending == null) return;
        extras.add(path);
        prepare();
    }

    /** Hands the picked file (and any extras) to the media's {@link GamePreparer}, off the render thread. */
    private void prepare() {
        GamePreparer preparer = preparer();
        Path game = pending;
        if (preparer == null || game == null) return;
        List<Path> given = List.copyOf(extras);
        preparing = true;
        setStatus(Component.translatable("cartridge_mod.writer.checking", game.getFileName().toString()), COLOR_TEXT);
        CompletableFuture.supplyAsync(() -> {
            try {
                long max = maxRomSize();
                if (Files.size(game) > max) {
                    throw new IOException(Component.translatable("cartridge_mod.writer.error.too_big",
                            String.valueOf(max / (1024 * 1024))).getString());
                }
                return preparer.prepare(game, Files.readAllBytes(game), given);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((result, error) -> minecraft.execute(() -> {
            preparing = false;
            if (minecraft.screen != this || pending != game) return;
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                setStatus(Component.translatable("cartridge_mod.writer.error.read", cause.getMessage()), COLOR_ERROR);
                forgetPending();
                return;
            }
            wants = result.wants();
            if (result.ready()) {
                file = new Picked(game, result.data(), RomCache.sha1(result.data()), result.system());
                String title = result.title() == null ? "" : result.title();
                titleBox.setValue(title.length() > RomUploadStartPayload.MAX_TITLE ? title.substring(0, RomUploadStartPayload.MAX_TITLE) : title);
                setStatus(result.message(), COLOR_TEXT);
            } else {
                file = null;
                setStatus(result.message(), wants.isEmpty() ? COLOR_ERROR : COLOR_NOTICE);
                if (wants.isEmpty()) forgetPending();
            }
            updateWidgets();
        }));
    }

    /** Whether the media's preparer is at work (the self-test waits for it). */
    boolean preparing() {
        return preparing;
    }

    /** The BIOS file was picked or dropped (D74): checked here first, then uploaded right away. */
    void pickBios(Path path) {
        read(path, picked -> {
            if (!picked.sha1().equals(RomFormats.FDS_BIOS.sha1())) {
                setStatus(Component.translatable("cartridge_mod.writer.error.wrong_bios"), COLOR_ERROR);
                return;
            }
            startUpload(RomUploadStartPayload.KIND_BIOS, picked, "");
        });
    }

    private void read(Path path, Consumer<Picked> then) {
        CompletableFuture.supplyAsync(() -> {
            try {
                long max = maxRomSize();
                if (Files.size(path) > max) {
                    throw new IOException(Component.translatable("cartridge_mod.writer.error.too_big",
                            String.valueOf(max / (1024 * 1024))).getString());
                }
                byte[] data = RomFormats.normalize(Files.readAllBytes(path)); // an N64 game as .z64 (D189)
                String system = RomFormats.detect(data).orElse(RomFormats.fromFileName(path.getFileName().toString()));
                return new Picked(path, data, RomCache.sha1(data), system);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((picked, error) -> minecraft.execute(() -> {
            if (minecraft.screen != this) return;
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                setStatus(Component.translatable("cartridge_mod.writer.error.read", cause.getMessage()), COLOR_ERROR);
            } else {
                then.accept(picked);
            }
        }));
    }

    void burn() {
        if (file != null && !uploading()) startUpload(RomUploadStartPayload.KIND_ROM, file, titleBox.getValue());
    }

    private void action() {
        Mode mode = mode();
        if (mode == Mode.COPY) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WriterMenu.BUTTON_COPY);
            CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(menu.source());
            setStatus(Component.translatable("cartridge_mod.writer.copied", contents == null ? "" : contents.title()), COLOR_NOTICE);
        } else if (mode == Mode.ERASE || mode == Mode.RESTORE) {
            if (System.nanoTime() < eraseArmedUntil) {
                eraseArmedUntil = 0;
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, WriterMenu.BUTTON_ERASE);
                setStatus(Component.translatable("cartridge_mod.writer.erased"), COLOR_NOTICE);
            } else {
                eraseArmedUntil = System.nanoTime() + ERASE_CONFIRM_NANOS;
            }
        }
        updateWidgets();
    }

    // --- Restoring a missing game file (D124) -----------------------------------------------------------------------

    private void chooseRestore() {
        FilePicker.open(Component.translatable("cartridge_mod.writer.pick_restore").getString(),
                RomFormats.FILE_PATTERNS, filterName(RomFormats.FILE_PATTERNS), this::pickRestore);
    }

    /** The file picker's name for its filter: "Games (*.nes, *.fds, ...)", from the patterns it offers. */
    private static String filterName(String[] patterns) {
        return Component.translatable("cartridge_mod.writer.pick_filter", String.join(", ", patterns)).getString();
    }

    /** Only the very file the cart was burned from will do: checked here by hash, and again on the server. */
    void pickRestore(Path path) {
        read(path, picked -> {
            CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(menu.main());
            if (mode() != Mode.RESTORE || contents == null) return;
            if (!picked.sha1().equals(contents.romSha1())) {
                setStatus(Component.translatable("cartridge_mod.writer.error.not_same_game"), COLOR_ERROR);
                return;
            }
            startUpload(RomUploadStartPayload.KIND_RESTORE, picked, "");
        });
    }

    // --- Battery saves (D97) -----------------------------------------------------------------------------------------

    private void importSave() {
        FilePicker.open(Component.translatable("cartridge_mod.writer.save.pick").getString(), new String[] {"*.sav", "*.srm"},
                Component.translatable("cartridge_mod.writer.save.filter").getString(), this::readSave);
    }

    private void readSave(Path path) {
        CompletableFuture.supplyAsync(() -> {
            try {
                if (Files.size(path) > SaveStore.MAX_SRAM) {
                    throw new IOException(Component.translatable("cartridge_mod.writer.save.too_big", SaveStore.MAX_SRAM / 1024).getString());
                }
                byte[] data = Files.readAllBytes(path);
                if (data.length == 0) throw new IOException(Component.translatable("cartridge_mod.writer.save.empty_file").getString());
                return data;
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((data, error) -> minecraft.execute(() -> {
            if (minecraft.screen != this || mode() != Mode.ERASE) return;
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                setStatus(Component.translatable("cartridge_mod.writer.error.read", cause.getMessage()), COLOR_ERROR);
                return;
            }
            saveBusy = true;
            for (int off = 0; off < data.length; off += SaveImportPayload.MAX_CHUNK) {
                int n = Math.min(SaveImportPayload.MAX_CHUNK, data.length - off);
                Services.NETWORK.sendToServer(new SaveImportPayload(data.length, off, Arrays.copyOfRange(data, off, off + n)));
            }
            updateWidgets();
        }));
    }

    private void exportSave() {
        saveBusy = true;
        Services.NETWORK.sendToServer(SaveExportPayload.INSTANCE);
        updateWidgets();
    }

    /** Render thread: the server's answer (CartridgeNetwork.onSaveFile). */
    void onSaveFile(SaveFilePayload payload) {
        saveBusy = false;
        switch (payload.state()) {
            case SaveFilePayload.EXPORT -> {
                byte[] data = payload.data();
                String name = payload.fileName().replaceAll("[\\\\/:*?\"<>|]", "_");
                FilePicker.save(Component.translatable("cartridge_mod.writer.save.pick_export").getString(), name,
                        new String[] {"*.sav"}, Component.translatable("cartridge_mod.writer.save.filter").getString(),
                        path -> writeSave(path, data));
            }
            case SaveFilePayload.IMPORTED -> setStatus(payload.message(), COLOR_NOTICE);
            default -> setStatus(payload.message(), COLOR_ERROR);
        }
        updateWidgets();
    }

    private void writeSave(Path path, byte[] data) {
        CompletableFuture.runAsync(() -> {
            try {
                Files.write(path, data);
            } catch (IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }).whenComplete((ignored, error) -> minecraft.execute(() -> {
            if (minecraft.screen != this) return;
            if (error != null) {
                Throwable cause = error.getCause() != null ? error.getCause() : error;
                setStatus(Component.translatable("cartridge_mod.writer.save.write_failed", cause.getMessage()), COLOR_ERROR);
            } else {
                setStatus(Component.translatable("cartridge_mod.writer.save.exported", path.getFileName().toString()), COLOR_NOTICE);
            }
        }));
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths.isEmpty() || uploading()) return;
        if (mode() == Mode.RESTORE) {
            pickRestore(paths.get(0));
            return;
        }
        if (mode() != Mode.BURN) return;
        if (needBios) pickBios(paths.get(0));
        else if (!wants.isEmpty()) pickExtra(paths.get(0));
        else pickGame(paths.get(0));
    }

    // --- Uploading -------------------------------------------------------------------------------------------------

    private void startUpload(int kind, Picked picked, String title) {
        upload = picked;
        uploadKind = kind;
        sent = acked = 0;
        setStatus(Component.empty(), COLOR_TEXT);
        Services.NETWORK.sendToServer(new RomUploadStartPayload(kind, picked.system(),
                picked.path().getFileName().toString(), title, picked.data().length, picked.sha1()));
        updateWidgets();
    }

    /** Render thread: the server's answer (CartridgeNetwork.onUploadStatus). */
    void onStatus(RomUploadStatusPayload payload) {
        Picked done = upload;
        switch (payload.state()) {
            case RomUploadStatusPayload.SEND -> {
                if (done == null) return;
                acked = payload.received();
                sent = Math.max(sent, acked);
                pump();
                return;
            }
            case RomUploadStatusPayload.DONE -> {
                // Burned: keep a copy in this client's cache so its own consoles needn't download it (D16).
                Picked burned = done != null && uploadKind != RomUploadStartPayload.KIND_BIOS ? done : file;
                if (burned != null) cacheAsync(burned.data());
                file = null;
                forgetPending();
                titleBox.setValue("");
                setStatus(payload.message(), COLOR_NOTICE);
            }
            case RomUploadStatusPayload.NEED_BIOS -> {
                needBios = true;
                setStatus(payload.message(), COLOR_NOTICE);
            }
            case RomUploadStatusPayload.BIOS_DONE -> {
                if (done != null && uploadKind == RomUploadStartPayload.KIND_BIOS) {
                    byte[] bios = done.data();
                    CompletableFuture.runAsync(() -> {
                        try {
                            RomDownloads.installBios(Services.PLATFORM.gameDir(), RomFormats.FDS_BIOS, bios);
                        } catch (IOException e) {
                            Cartridge.LOGGER.warn("Couldn't keep a copy of the BIOS", e);
                        }
                    });
                }
                needBios = false;
                setStatus(payload.message(), COLOR_NOTICE);
            }
            default -> setStatus(payload.message(), COLOR_ERROR);
        }
        upload = null;
        updateWidgets();
    }

    private void pump() {
        byte[] data = upload.data();
        while (sent < data.length && sent - acked < UPLOAD_WINDOW) {
            int n = Math.min(RomUploadChunkPayload.MAX_CHUNK, data.length - sent);
            Services.NETWORK.sendToServer(new RomUploadChunkPayload(sent, Arrays.copyOfRange(data, sent, sent + n)));
            sent += n;
        }
    }

    private static void cacheAsync(byte[] data) {
        CompletableFuture.runAsync(() -> {
            try {
                RomCache.put(Services.PLATFORM.gameDir(), data);
            } catch (IOException e) {
                Cartridge.LOGGER.warn("Couldn't cache a burned ROM", e);
            }
        });
    }

    @Override
    public void removed() {
        super.removed();
        if (uploading()) Services.NETWORK.sendToServer(RomUploadCancelPayload.INSTANCE);
        upload = null;
    }

    // --- Drawing and keys ------------------------------------------------------------------------------------------

    private void setStatus(Component message, int color) {
        status = message;
        statusColor = color;
    }

    @Override
    protected void beforeDraw() {
        updateWidgets(); // slot changes can land between ticks
    }

    @Override
    protected void drawOver(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index < 2) {
            GuiDraw.tooltip(g, font, Component.translatable(hoveredSlot.index == CartridgeWriterBlockEntity.MAIN
                    ? "cartridge_mod.writer.slot_main" : "cartridge_mod.writer.slot_source"), mouseX, mouseY);
        }
    }

    @Override
    protected void drawBackground(GuiGraphics g, int mouseX, int mouseY) {
        GuiDraw.blit(g, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void drawLabels(GuiGraphics g, int mouseX, int mouseY) {
        if (uploading()) {
            int length = upload.data().length;
            float progress = length == 0 ? 1 : acked / (float) length;
            GuiDraw.text(g, font, Component.translatable("cartridge_mod.writer.uploading", Math.round(progress * 100)),
                    8, STATUS_Y, COLOR_TEXT, false);
            g.fill(8, STATUS_Y + 11, 8 + STATUS_WIDTH, STATUS_Y + 15, 0xFF555555);
            g.fill(8, STATUS_Y + 11, 8 + Math.round(STATUS_WIDTH * progress), STATUS_Y + 15, 0xFF55C055);
            return;
        }
        Component text = status.getString().isEmpty() ? hint() : status;
        int color = status.getString().isEmpty() ? COLOR_TEXT : statusColor;
        List<FormattedCharSequence> lines = font.split(text, STATUS_WIDTH);
        for (int i = 0; i < Math.min(3, lines.size()); i++) {
            GuiDraw.text(g, font, lines.get(i), 8, STATUS_Y + i * 10, color, false);
        }
    }

    /** What to do next, when there's no message to show. */
    private Component hint() {
        return switch (mode()) {
            case EMPTY -> Component.translatable("cartridge_mod.writer.hint.empty");
            case BURN -> Component.translatable(needBios ? "cartridge_mod.writer.need_bios" : "cartridge_mod.writer.hint.burn");
            case COPY -> Component.translatable("cartridge_mod.writer.hint.copy", title(menu.source()));
            case ERASE -> Component.translatable("cartridge_mod.writer.hint.erase", title(menu.main()));
            case RESTORE -> Component.translatable("cartridge_mod.writer.hint.restore", title(menu.main()));
        };
    }

    private static String title(ItemStack cart) {
        CartridgeContents contents = CartridgeComponents.CARTRIDGE.get(cart);
        return contents == null ? "" : contents.title();
    }

    //? if >=1.21.9 {
    /*@Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            minecraft.player.closeContainer();
            return true;
        }
        // Typing a title mustn't close the screen (the inventory key) or drop items.
        return titleBox.keyPressed(event) || titleBox.canConsumeInput() || super.keyPressed(event);
    }
    *///?} else {
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            minecraft.player.closeContainer();
            return true;
        }
        // Typing a title mustn't close the screen (the inventory key) or drop items.
        return titleBox.keyPressed(keyCode, scanCode, modifiers) || titleBox.canConsumeInput()
                || super.keyPressed(keyCode, scanCode, modifiers);
    }
    //?}

    private static long maxRomSize() {
        return CartridgeConfig.synced().maxRomSizeBytes(); // the connected server's (D123)
    }

    private static String kilobytes(int bytes) {
        return String.valueOf((bytes + 1023) / 1024);
    }
}
