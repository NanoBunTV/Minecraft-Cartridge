package com.nanobuntv.cartridge.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The mod's plain screens (no slots), drawn the same way on every Minecraft version (Porting.md): vanilla's widgets,
 * then {@link #drawOver}. Only this class has each version's overrides (see {@link CartridgeContainerScreen}).
 */
public abstract class CartridgeScreen extends Screen {
    protected CartridgeScreen(Component title) {
        super(title);
    }

    /** Over the widgets. */
    protected void drawOver(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    //? if >=26.1 {
    /*@Override
    public void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        drawOver(g, mouseX, mouseY, partialTick);
    }
    *///?} else if >=1.20 {
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        //? if <1.20.2 {
        /*renderBackground(g);
        *///?}
        super.render(g, mouseX, mouseY, partialTick);
        drawOver(g, mouseX, mouseY, partialTick);
    }
    //?} else {
    /*@Override
    public void render(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float partialTick) {
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        drawOver(new GuiGraphics(pose), mouseX, mouseY, partialTick);
    }
    *///?}
}
