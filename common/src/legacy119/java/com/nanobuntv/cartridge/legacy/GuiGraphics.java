package com.nanobuntv.cartridge.legacy;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/**
 * Minecraft's {@code GuiGraphics} (1.20), for 1.19.4: the part the mod draws with, over a PoseStack and GuiComponent's
 * statics (Porting.md). The build points the mod's GuiGraphics imports here; the screens' 1.19.4 overrides wrap the
 * PoseStack they get.
 */
public final class GuiGraphics {
    private final PoseStack pose;

    public GuiGraphics(PoseStack pose) {
        this.pose = pose;
    }

    public PoseStack pose() {
        return pose;
    }

    public int guiWidth() {
        return Minecraft.getInstance().getWindow().getGuiScaledWidth();
    }

    public int guiHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        GuiComponent.fill(pose, x1, y1, x2, y2, color);
    }

    public void blit(ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(pose, x, y, u, v, width, height);
    }

    public void blit(ResourceLocation texture, int x, int y, int width, int height, float u, float v, int uWidth,
            int vHeight, int textureWidth, int textureHeight) {
        RenderSystem.setShaderTexture(0, texture);
        GuiComponent.blit(pose, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
    }

    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public int drawString(Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return shadow ? font.drawShadow(pose, text, x, y, color) : font.draw(pose, text, x, y, color);
    }

    public void drawCenteredString(Font font, Component text, int x, int y, int color) {
        GuiComponent.drawCenteredString(pose, font, text, x, y, color);
    }

    public void drawWordWrap(Font font, FormattedText text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : font.split(text, width)) {
            font.draw(pose, line, x, y, color);
            y += font.lineHeight;
        }
    }

    public void renderTooltip(Font font, Component text, int x, int y) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) screen.renderTooltip(pose, text, x, y);
    }

    public void renderComponentTooltip(Font font, List<Component> lines, int x, int y) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen != null) screen.renderComponentTooltip(pose, lines, x, y);
    }
}
