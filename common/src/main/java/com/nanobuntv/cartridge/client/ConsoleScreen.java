package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.HandheldItem;
import com.nanobuntv.cartridge.item.HandheldLink;
import com.nanobuntv.cartridge.item.MemoryCardContents;
import com.nanobuntv.cartridge.menu.ConsoleMenu;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The console's GUI (D29): the cartridge slot, with the power state and both ports listed beside it, and the disk swap
 * button while a disk game runs (D74). Below, the Memory Card slot (D92) with the card's screenshot, game and time
 * (D98), and Save (anyone; overwriting asks twice) and Load (Player 1, D93; the same game, D94). A handheld's GUI
 * (D163) is the same, with a power switch where the ports would be (D164).
 */
public class ConsoleScreen extends CartridgeContainerScreen<ConsoleMenu> {
    private static final ResourceLocation BACKGROUND = Cartridge.id("textures/gui/container/console_8bit.png");
    private static final int THUMB_X = 48, THUMB_Y = 72, THUMB_W = 48, THUMB_H = 36;
    private static final int TEXT_X = 100, TEXT_WIDTH = 68;
    private static final long CONFIRM_NANOS = 3_000_000_000L, RESULT_NANOS = 4_000_000_000L;
    private static final int COLOR_TEXT = 0xFF404040, COLOR_DIM = 0xFF707070, COLOR_ERROR = 0xFFA02020, COLOR_NOTICE = 0xFF2050A0;
    private static final DateTimeFormatter SHORT_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.ROOT);
    private static final DateTimeFormatter FULL_TIME = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM);
    private static int nextThumbnailId;

    private Button swapDisk, saveButton, loadButton, powerButton, linkButton;
    private long saveArmedUntil;
    private int seenResult = -1;
    private int shownResult;
    private long resultUntil;

    @Nullable
    private ResourceLocation thumbnailId;
    @Nullable
    private MemoryCardContents thumbnailOf;

    public ConsoleScreen(ConsoleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, ConsoleMenu.INVENTORY_Y + 82);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        swapDisk = addRenderableWidget(Button.builder(Component.translatable("cartridge_mod.console.swap_disk"),
                b -> click(ConsoleMenu.BUTTON_SWAP_DISK))
                .bounds(leftPos + ConsoleMenu.SLOT_X + 26, topPos + 57, 70, 13).build());
        saveButton = addRenderableWidget(Button.builder(Component.empty(), b -> save())
                .bounds(leftPos + TEXT_X, topPos + 94, 33, 14).build());
        // D121, D191: this console's controls in the config screen, top right beside the power line.
        addRenderableWidget(Button.builder(Component.translatable("cartridge_mod.console.controls"),
                        b -> minecraft.setScreen(CartridgeConfigScreen.create(this, menu.controls())))
                .bounds(leftPos + 122, topPos + 18, 48, 13)
                .tooltip(Tooltip.create(Component.translatable("cartridge_mod.console.controls.tooltip"))).build());
        powerButton = addRenderableWidget(Button.builder(Component.empty(), b -> click(ConsoleMenu.BUTTON_POWER))
                .bounds(leftPos + ConsoleMenu.SLOT_X + 26, topPos + 36, 70, 14).build());
        linkButton = addRenderableWidget(Button.builder(Component.empty(), b -> link())
                .bounds(leftPos + ConsoleMenu.SLOT_X + 26, topPos + 53, 70, 14).build());
        loadButton = addRenderableWidget(Button.builder(Component.translatable("cartridge_mod.console.card.load"),
                b -> click(ConsoleMenu.BUTTON_LOAD_CARD)).bounds(leftPos + TEXT_X + 35, topPos + 94, 33, 14).build());
        updateWidgets();
    }

    /** "Link with..." opens the list of players to ask (D176); when linked, Unlink. */
    private void link() {
        if (menu.linkState() == 1 || menu.linkState() == 2) click(ConsoleMenu.BUTTON_UNLINK);
        else minecraft.setScreen(new LinkScreen(this));
    }

    /** The handheld this GUI is for: the one in the player's hands (D163). */
    @Nullable
    private HandheldLink heldLink() {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = minecraft.player.getItemInHand(hand);
            if (HandheldItem.isHandheld(stack)) return CartridgeComponents.HANDHELD_LINK.get(stack);
        }
        return null;
    }

    private void click(int button) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    /** Saving over a card's savestate needs a second click (D92). */
    private void save() {
        if (card() != null && System.nanoTime() >= saveArmedUntil) {
            saveArmedUntil = System.nanoTime() + CONFIRM_NANOS;
        } else {
            saveArmedUntil = 0;
            click(ConsoleMenu.BUTTON_SAVE_CARD);
        }
        updateWidgets();
    }

    @Nullable
    private MemoryCardContents card() {
        return CartridgeComponents.MEMORY_CARD.get(menu.memoryCard());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        updateWidgets();
    }

    private void updateWidgets() {
        swapDisk.visible = menu.canSwapDisk();
        powerButton.visible = menu.handheld() || menu.powerInGui(); // a cabinet's POWER is here (D203)
        powerButton.setMessage(Component.translatable(menu.powered() ? "cartridge_mod.handheld.switch_off" : "cartridge_mod.handheld.switch_on"));
        // A handheld switches on by right-clicking it in hand, which starts playing.
        powerButton.active = menu.powered() || menu.powerInGui();
        linkButton.visible = menu.linkable(); // not on the Advances (D184)
        boolean hasCable = menu.linkable() && menu.getSlot(2).getItem().is(CartridgeItems.LINK_CABLE.get());
        boolean linked = menu.linkable() && menu.linkState() != 0;
        linkButton.setMessage(Component.translatable(linked ? "cartridge_mod.link.unlink" : "cartridge_mod.link.link_with"));
        linkButton.active = linked || hasCable;
        linkButton.setTooltip(linkButton.active ? null : Tooltip.create(Component.translatable("cartridge_mod.link.no_cable")));
        ItemStack cardStack = menu.memoryCard();
        MemoryCardContents card = card();
        CartridgeContents cart = CartridgeComponents.CARTRIDGE.get(menu.getSlot(0).getItem());
        boolean hasCard = !cardStack.isEmpty();
        boolean running = menu.running() && menu.powered();

        saveButton.visible = loadButton.visible = hasCard;
        boolean armed = System.nanoTime() < saveArmedUntil;
        saveButton.setMessage(Component.translatable(armed ? "cartridge_mod.console.card.confirm" : "cartridge_mod.console.card.save"));
        saveButton.active = running;
        saveButton.setTooltip(running ? null : Tooltip.create(Component.translatable("cartridge_mod.console.why.no_game")));

        String why = null;
        if (card == null) why = "empty";
        else if (!running) why = "no_game";
        else if (cart == null || !card.romSha1().equals(cart.romSha1())) why = "other_game"; // D94
        else if (!menu.mayLoad()) why = "player_one"; // D93
        loadButton.active = why == null;
        loadButton.setTooltip(why == null ? null : Tooltip.create(Component.translatable("cartridge_mod.console.why." + why)));
    }

    @Override
    protected void beforeDraw() {
        updateWidgets(); // slot changes can land between ticks
    }

    @Override
    protected void drawOver(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index == 1) {
            GuiDraw.tooltip(g, font, Component.translatable("cartridge_mod.console.card_slot"), mouseX, mouseY);
        }
        if (hoveredSlot != null && !hoveredSlot.hasItem() && hoveredSlot.index == 2 && menu.linkable()) {
            GuiDraw.tooltip(g, font, Component.translatable("cartridge_mod.link.cable_slot"), mouseX, mouseY);
        }
        if (showingError() && isHovering(TEXT_X, 82, TEXT_WIDTH, 10, mouseX, mouseY)) {
            GuiDraw.tooltip(g, font, Component.translatable("cartridge_mod.console.why." + ConsoleMenu.RESULT_KEYS[shownResult]),
                    mouseX, mouseY);
        }
        MemoryCardContents card = card();
        if (card != null && isHovering(THUMB_X, THUMB_Y, THUMB_W, THUMB_H, mouseX, mouseY)) {
            GuiDraw.tooltip(g, font, List.of(Component.literal(card.title()),
                    Component.translatable("cartridge_mod.console.card.saved_at", FULL_TIME.format(local(card.savedAt())))
                            .withStyle(ChatFormatting.GRAY)), mouseX, mouseY);
        }
    }

    @Override
    protected void drawBackground(GuiGraphics g, int mouseX, int mouseY) {
        GuiDraw.blit(g, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (menu.linkable()) { // the Link Cable's slot (D179), drawn as vanilla's slots are
            int x = leftPos + ConsoleMenu.LINK_X - 1, y = topPos + ConsoleMenu.LINK_Y - 1;
            g.fill(x, y, x + 18, y + 18, 0xFF8B8B8B);
            g.fill(x, y, x + 17, y + 1, 0xFF373737);
            g.fill(x, y, x + 1, y + 17, 0xFF373737);
            g.fill(x + 1, y + 17, x + 18, y + 18, 0xFFFFFFFF);
            g.fill(x + 17, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        }
        ResourceLocation thumbnail = thumbnail(card());
        if (thumbnail != null) {
            GuiDraw.blit(g, thumbnail, leftPos + THUMB_X, topPos + THUMB_Y, THUMB_W, THUMB_H, 0, 0,
                    MemoryCardContents.THUMB_WIDTH, MemoryCardContents.THUMB_HEIGHT,
                    MemoryCardContents.THUMB_WIDTH, MemoryCardContents.THUMB_HEIGHT);
        }
    }

    @Override
    protected void drawLabels(GuiGraphics g, int mouseX, int mouseY) {
        int x = ConsoleMenu.SLOT_X + 26;
        HandheldLink link = menu.linkable() && menu.linkState() != 0 ? heldLink() : null;
        Component power = link != null
                ? Component.translatable(menu.linkState() == 1 ? "cartridge_mod.link.linked_with" : "cartridge_mod.link.linked_away",
                        link.partnerName())
                : Component.translatable(menu.powered() ? "cartridge_mod.console.power_on" : "cartridge_mod.console.power_off");
        GuiDraw.text(g, font, power, x, 22, link != null ? COLOR_NOTICE : COLOR_TEXT, false);
        int ports = menu.handheld() ? 0 : menu.ports(), top = ports > 2 ? 33 : 36, spacing = ports > 2 ? 9 : 12;
        if (menu.powerInGui()) { // below the POWER button (D203)
            top = 53;
            spacing = 9;
        }
        boolean players = menu.builtInControls(); // a cabinet's players, not ports (D202)
        for (int port = 0; port < ports; port++) {
            int state = menu.portState(port);
            if (players && state == 0) continue; // a 1-player cabinet has no Player 2
            String key = players ? (state == 2 ? "playing" : "free") : switch (state) {
                case 1 -> "plugged";
                case 2 -> "in_use";
                default -> "empty";
            };
            String prefix = players ? "cartridge_mod.console.player" : "cartridge_mod.console.port";
            GuiDraw.text(g, font, Component.translatable(prefix, port + 1, Component.translatable(prefix + "." + key)),
                    x, top + port * spacing, COLOR_TEXT, false);
        }
        renderCard(g);
    }

    /** The card's game and time (D98), or the latest Save / Load result for a few seconds. */
    private void renderCard(GuiGraphics g) {
        if (menu.memoryCard().isEmpty()) {
            drawClipped(g, Component.translatable("cartridge_mod.console.card.none"), 72, COLOR_DIM);
            return;
        }
        MemoryCardContents card = card();
        drawClipped(g, card == null ? Component.translatable("cartridge_mod.memory_card.empty") : Component.literal(card.title()),
                72, card == null ? COLOR_DIM : COLOR_TEXT);

        int result = menu.result();
        if (result != seenResult) {
            if (seenResult != -1 && (result & 0xF) != ConsoleMenu.RESULT_NONE) { // not the value the menu opened with
                shownResult = result & 0xF;
                resultUntil = System.nanoTime() + RESULT_NANOS;
            }
            seenResult = result;
        }
        if (showingResult()) {
            int color = switch (shownResult) {
                case ConsoleMenu.RESULT_SAVING -> COLOR_TEXT;
                case ConsoleMenu.RESULT_SAVED, ConsoleMenu.RESULT_LOADED -> COLOR_NOTICE;
                default -> COLOR_ERROR;
            };
            drawClipped(g, Component.translatable("cartridge_mod.console.result." + ConsoleMenu.RESULT_KEYS[shownResult]), 83, color);
        } else if (card != null) {
            drawClipped(g, Component.literal(SHORT_TIME.format(local(card.savedAt()))), 83, COLOR_DIM);
        }
    }

    private boolean showingResult() {
        return shownResult != ConsoleMenu.RESULT_NONE && !menu.memoryCard().isEmpty()
                && (shownResult == ConsoleMenu.RESULT_SAVING || System.nanoTime() < resultUntil);
    }

    /** A result that went wrong: hovering it explains. */
    private boolean showingError() {
        return showingResult() && shownResult != ConsoleMenu.RESULT_SAVING && shownResult != ConsoleMenu.RESULT_SAVED
                && shownResult != ConsoleMenu.RESULT_LOADED;
    }

    private void drawClipped(GuiGraphics g, Component text, int y, int color) {
        String s = text.getString();
        if (font.width(s) > TEXT_WIDTH) s = font.plainSubstrByWidth(s, TEXT_WIDTH - font.width("...")) + "...";
        GuiDraw.text(g, font, s, TEXT_X, y, color, false);
    }

    private static java.time.LocalDateTime local(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** The card's screenshot as a texture, made when the card (or its save) changes. */
    @Nullable
    private ResourceLocation thumbnail(@Nullable MemoryCardContents card) {
        if (card == null || !card.hasThumbnail()) {
            releaseThumbnail();
            return null;
        }
        if (card.equals(thumbnailOf) && thumbnailId != null) return thumbnailId;
        releaseThumbnail();
        int w = MemoryCardContents.THUMB_WIDTH, h = MemoryCardContents.THUMB_HEIGHT;
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        byte[] px = card.thumbnail();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = (px[(y * w + x) * 2] & 0xFF) << 8 | px[(y * w + x) * 2 + 1] & 0xFF;
                int r = (v >> 11 & 31) * 255 / 31, gr = (v >> 5 & 63) * 255 / 63, b = (v & 31) * 255 / 31;
                ClientCompat.setPixelAbgr(image, x, y, 0xFF000000 | b << 16 | gr << 8 | r); // ABGR
            }
        }
        thumbnailId = Cartridge.id("card_thumbnail/" + nextThumbnailId++);
        minecraft.getTextureManager().register(thumbnailId, ClientCompat.texture("Cartridge card thumbnail", image));
        thumbnailOf = card;
        return thumbnailId;
    }

    private void releaseThumbnail() {
        if (thumbnailId != null) minecraft.getTextureManager().release(thumbnailId);
        thumbnailId = null;
        thumbnailOf = null;
    }

    @Override
    public void removed() {
        super.removed();
        releaseThumbnail();
    }
}
