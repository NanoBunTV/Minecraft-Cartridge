package com.nanobuntv.cartridge.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * The mod's container screens, drawn the same way on every Minecraft version (Porting.md): {@link #drawBackground},
 * then vanilla's slots and labels with {@link #drawLabels} after them, then the hovered slot's tooltip and
 * {@link #drawOver}. Only this class has each version's overrides (1.20's GuiGraphics, 26.1's render state
 * extraction, where the type is GuiGraphicsExtractor: the build renames it).
 */
public abstract class CartridgeContainerScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {
    /** A screen {@code width x height} (26.1 made the size final, set by the constructor). */
    protected CartridgeContainerScreen(T menu, Inventory inventory, Component title, int width, int height) {
        //? if >=26.1 {
        /*super(menu, inventory, title, width, height);
        *///?} else {
        super(menu, inventory, title);
        imageWidth = width;
        imageHeight = height;
        //?}
    }

    /** The screen's texture, under everything. */
    protected abstract void drawBackground(GuiGraphics g, int mouseX, int mouseY);

    /** After vanilla's labels, in the screen's own coordinates (its top-left is 0, 0). */
    protected void drawLabels(GuiGraphics g, int mouseX, int mouseY) {}

    /** Over everything: extra tooltips. */
    protected void drawOver(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    /** Before anything is drawn this frame (slot changes can land between ticks). */
    protected void beforeDraw() {}

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        beforeDraw();
        super.extractRenderState(g, mouseX, mouseY, partialTick); // its tooltip included
        drawOver(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void extractBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        drawBackground(g, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        drawLabels(g, mouseX, mouseY);
    }
    *///?} else if >=1.20 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        beforeDraw();
        //? if <1.20.2 {
        /*renderBackground(g);
        *///?}
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        drawOver(g, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        drawBackground(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        super.renderLabels(g, mouseX, mouseY);
        drawLabels(g, mouseX, mouseY);
    }
    //?} else {
    /*// 1.19.4 draws on a PoseStack: the mod's GuiGraphics wraps it.
    @Override
    public void render(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float partialTick) {
        beforeDraw();
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        renderTooltip(pose, mouseX, mouseY);
        drawOver(new GuiGraphics(pose), mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(com.mojang.blaze3d.vertex.PoseStack pose, float partialTick, int mouseX, int mouseY) {
        drawBackground(new GuiGraphics(pose), mouseX, mouseY);
    }

    @Override
    protected void renderLabels(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY) {
        super.renderLabels(pose, mouseX, mouseY);
        drawLabels(new GuiGraphics(pose), mouseX, mouseY);
    }
    *///?}
}
