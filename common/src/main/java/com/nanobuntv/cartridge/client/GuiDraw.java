package com.nanobuntv.cartridge.client;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
//? if >=1.21.6 {
/*import net.minecraft.client.renderer.RenderPipelines;
*///?}

/**
 * The GUI drawing the mod's screens do, for every Minecraft version (Porting.md): from 1.21.6 a texture is drawn with
 * a render pipeline and tooltips are queued for the end of the frame.
 */
public final class GuiDraw {
    private GuiDraw() {}

    /** Part of a 256 x 256 texture, at its own size (a screen's background). */
    public static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        //? if >=1.21.6 {
        /*g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, 256, 256);
        *///?} else {
        g.blit(texture, x, y, u, v, width, height);
        //?}
    }

    /** The region {@code u, v, uWidth, vHeight} of a {@code textureWidth x textureHeight} texture, stretched to a size. */
    public static void blit(GuiGraphics g, ResourceLocation texture, int x, int y, int width, int height, float u, float v,
            int uWidth, int vHeight, int textureWidth, int textureHeight) {
        //? if >=1.21.6 {
        /*g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, uWidth, vHeight, textureWidth, textureHeight);
        *///?} else {
        g.blit(texture, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
        //?}
    }

    /** Text at x, y ({@code color} with its alpha byte). */
    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, color, shadow);
        *///?} else {
        g.drawString(font, text, x, y, color, shadow);
        //?}
    }

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, color, shadow);
        *///?} else {
        g.drawString(font, text, x, y, color, shadow);
        //?}
    }

    public static void text(GuiGraphics g, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        //? if >=26.1 {
        /*g.text(font, text, x, y, color, shadow);
        *///?} else {
        g.drawString(font, text, x, y, color, shadow);
        //?}
    }

    /** Text centred on x, with a shadow. */
    public static void centered(GuiGraphics g, Font font, Component text, int x, int y, int color) {
        //? if >=26.1 {
        /*g.centeredText(font, text, x, y, color);
        *///?} else {
        g.drawCenteredString(font, text, x, y, color);
        //?}
    }

    /** Text wrapped to {@code width}, without a shadow. */
    public static void wordWrap(GuiGraphics g, Font font, Component text, int x, int y, int width, int color) {
        //? if >=26.1 {
        /*g.textWithWordWrap(font, text, x, y, width, color, false);
        *///?} else {
        g.drawWordWrap(font, text, x, y, width, color);
        //?}
    }

    public static void tooltip(GuiGraphics g, Font font, Component text, int x, int y) {
        //? if >=1.21.6 {
        /*g.setTooltipForNextFrame(font, text, x, y);
        *///?} else {
        g.renderTooltip(font, text, x, y);
        //?}
    }

    public static void tooltip(GuiGraphics g, Font font, List<Component> lines, int x, int y) {
        //? if >=1.21.6 {
        /*g.setComponentTooltipForNextFrame(font, lines, x, y);
        *///?} else {
        g.renderComponentTooltip(font, lines, x, y);
        //?}
    }
}
