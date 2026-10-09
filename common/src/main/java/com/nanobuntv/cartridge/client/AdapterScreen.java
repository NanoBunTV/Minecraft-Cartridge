package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.menu.AdapterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The Pocketron Adapter's GUI (D166): its cartridge slot, with a word on which games fit (Color-only games don't run on
 * the Super Game Boy, D167).
 */
public class AdapterScreen extends CartridgeContainerScreen<AdapterMenu> {
    private static final ResourceLocation BACKGROUND = Cartridge.id("textures/gui/container/pocketron_adapter.png");
    private static final int TEXT_X = AdapterMenu.SLOT_X + 26, TEXT_WIDTH = 176 - TEXT_X - 8, COLOR_TEXT = 0xFF404040;

    public AdapterScreen(AdapterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, AdapterMenu.INVENTORY_Y + 82);
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void drawBackground(GuiGraphics g, int mouseX, int mouseY) {
        GuiDraw.blit(g, BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }

    @Override
    protected void drawLabels(GuiGraphics g, int mouseX, int mouseY) {
        GuiDraw.wordWrap(g, font, Component.translatable("cartridge_mod.pocketron_adapter.fits"), TEXT_X, 22, TEXT_WIDTH, COLOR_TEXT);
    }
}
