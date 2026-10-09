package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nanobuntv.cartridge.block.CrtTvBlock;
import com.nanobuntv.cartridge.block.DisplayBlock;
import com.nanobuntv.cartridge.block.FlatscreenTvBlock;
import com.nanobuntv.cartridge.block.Screen;
import com.nanobuntv.cartridge.blockentity.TvBlockEntity;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * Draws a display's picture (D17, D31) on its glass ({@link Screen}, from the block: the CRT, the flatscreens, a
 * video wall's linked panel across the whole wall, D129, D131): the game as a 4:3 picture (D65, D130) on black,
 * static while the console boots, black when it runs without a cartridge, the game's title and a progress bar while
 * its ROM downloads (D70), messages (errors, "unsupported OS", D5; a wall that isn't a rectangle, D131) as text,
 * "Waiting for <name>..." over the picture (D84) and a small "PAUSED" in its corner (D88). Everything scales with the
 * glass; the CRT's 10 x 8 px is the unit. Full-bright while on; nothing when the console is off, out of range or
 * unlinked: the model's dark glass shows.
 * <p>
 * Switching on and off: the CRT's picture opens out from a bright line and collapses to a dot (D31); flatscreens
 * fade in and out (D133). The picture itself is flat (the CRT effect was removed, D117).
 */
public class TvRenderer extends CartridgeBlockEntityRenderer<TvBlockEntity> {
    private static final float UNIT_WIDTH = 10, UNIT_HEIGHT = 8; // the CRT's glass, px: text sizes are set for it
    private static final float LIFT = 0.05F; // px in front of the glass
    private static final long WARM_UP = 500_000_000L, COLLAPSE = 600_000_000L, FADE = 250_000_000L;

    private final Font font;

    public TvRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    protected boolean drawsOffScreen(@Nullable TvBlockEntity tv) {
        // A Large Flatscreen and a video wall reach well past their anchor's block.
        return tv == null
                || !(tv.getBlockState().getBlock() instanceof CrtTvBlock || tv.getBlockState().getBlock() instanceof FlatscreenTvBlock);
    }

    /** NeoForge's culling box (an override there, a plain method on Fabric): the whole glass. */
    public AABB getRenderBoundingBox(TvBlockEntity tv) {
        BlockPos pos = tv.getBlockPos();
        if (tv.getLevel() == null) return new AABB(pos);
        Screen screen = DisplayBlock.screenAt(tv.getLevel(), pos);
        if (screen == null) return new AABB(pos);
        Vec3 far = screen.origin().add(screen.right()).add(screen.down());
        return new AABB(pos).minmax(new AABB(screen.origin(), far).inflate(0.1));
    }

    @Override
    public void draw(TvBlockEntity tv, float partialTick, PoseStack pose, Draw buffers, int light, int overlay) {
        BlockPos consolePos = tv.console();
        if (consolePos == null || tv.getLevel() == null) return;
        Screen screen = DisplayBlock.screenAt(tv.getLevel(), tv.getBlockPos());
        if (screen == null) return;
        drawScreen(consolePos, screen, tv.getBlockPos(), pose, buffers);
    }

    /**
     * Draws a console's picture on a glass, for any block entity renderer at {@code pos} (an arcade cabinet's own
     * screen, D205): what a TV linked to the console at {@code consolePos} would show there.
     */
    public void drawScreen(BlockPos consolePos, Screen screen, BlockPos pos, PoseStack pose, Draw buffers) {
        ConsoleView view = ClientConsoles.view(consolePos);
        if (view == null) return;
        boolean crt = screen.style() == Screen.Style.CRT;
        ConsoleView.State state = view.state();
        long now = System.nanoTime();
        if (state == ConsoleView.State.OFF && view.sinceOff(now) >= (crt ? COLLAPSE : FADE)) return;

        float w = (float) screen.width() * 16, h = (float) screen.height() * 16; // px
        float unit = Math.min(w / UNIT_WIDTH, h / UNIT_HEIGHT);
        pose.pushPose();
        // A frame on the glass: x along its top edge to the right, y down, z out towards the viewer, in px.
        Vec3 o = screen.origin(), r = screen.right().normalize(), d = screen.down().normalize(), n = screen.normal();
        pose.translate(o.x - pos.getX(), o.y - pos.getY(), o.z - pos.getZ());
        pose.last().pose().mul(new Matrix4f(
                (float) r.x, (float) r.y, (float) r.z, 0,
                (float) d.x, (float) d.y, (float) d.z, 0,
                (float) n.x, (float) n.y, (float) n.z, 0,
                0, 0, 0, 1));
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        pose.translate(0, 0, LIFT);
        int full = LightTexture.FULL_BRIGHT;
        double[] fit = screen.picture(view.pictureAspect());
        float px = (float) fit[0] * w, py = (float) fit[1] * h, pw = (float) fit[2] * w, ph = (float) fit[3] * h;

        if (state == ConsoleView.State.OFF) {
            float t = view.sinceOff(now) / (float) (crt ? COLLAPSE : FADE);
            if (crt) collapse(pose, buffers, view.lastPicture(), t, w, h, px, py, pw, ph, full);
            else fadeOut(pose, buffers, view.lastPicture(), t, w, h, px, py, pw, ph, full);
            pose.popPose();
            return;
        }
        float warm = Math.min(1, view.sinceOn(now) / (float) (crt ? WARM_UP : FADE));
        if (crt && warm < 1) { // the picture opens out from a line in the middle
            float open = Math.max(0.02F, Math.min(1, warm * 2));
            pose.translate(0, h / 2, 0);
            pose.scale(1, open, 1);
            pose.translate(0, -h / 2, 0);
        }
        Matrix4f matrix = pose.last().pose();
        ResourceLocation white = ClientConsoles.whiteTexture();

        Component message = screen.problem() != null ? screen.problem() : view.message();
        boolean error = screen.problem() != null || state == ConsoleView.State.ERROR;
        if (screen.problem() != null) {
            quad(buffers, white, matrix, 0, 0, w, h, 0, 0x000000, 255, full);
        } else {
            ResourceLocation picture = view.picture();
            switch (state) {
                case PLAYING, GLITCH -> {
                    quad(buffers, white, matrix, 0, 0, w, h, 0, 0x000000, 255, full);
                    if (picture != null) quad(buffers, picture, matrix, px, py, pw, ph, 0.02F, 0xFFFFFF, 255, full);
                }
                case STARTING -> quad(buffers, ClientConsoles.staticTexture(), matrix, 0, 0, w, h, 0, 0xB0B0B0, 255, full);
                case BLACK, ERROR, LOADING -> quad(buffers, white, matrix, 0, 0, w, h, 0, 0x000000, 255, full);
                default -> {}
            }
        }
        float progress = screen.problem() == null && state == ConsoleView.State.LOADING ? view.progress() : -1;
        if (message != null && screen.problem() == null && state == ConsoleView.State.PLAYING) {
            // Over the game: a dark band behind the text so it reads on any picture.
            quad(buffers, white, matrix, 0, h / 2 - 1.2F * unit, w, 2.4F * unit, 0.3F, 0x000000, 255, full);
        }
        if (view.paused() && screen.problem() == null) {
            corner(pose, buffers, Component.translatable("cartridge_mod.tv.paused"), px, py, unit, full);
        }
        if (message != null) {
            text(pose, buffers, message, error ? 0xFFFF6060 : 0xFFFFFFFF, full, w, h, unit, progress >= 0 ? -unit : 0);
        }
        if (progress >= 0) {
            float bw = 7 * unit, bh = 0.6F * unit, x = (w - bw) / 2, y = h / 2 + unit;
            quad(buffers, white, matrix, x, y, bw, bh, 0.3F, 0x404040, 255, full);
            quad(buffers, white, matrix, x, y, bw * progress, bh, 0.35F, 0xFFFFFF, 255, full);
        }
        if (warm < 1) {
            if (crt) { // a bright line at first, then the glow settles
                quad(buffers, white, matrix, 0, 0, w, h, 0.4F, 0xFFFFFF, Math.round(255 * (1 - warm)), full);
            } else { // the backlight comes up (D133)
                quad(buffers, white, matrix, 0, 0, w, h, 0.4F, 0x000000, Math.round(255 * (1 - warm)), full);
            }
        }
        pose.popPose();
    }

    /** CRT switched off (D31): the last picture squashes into a bright line, the line into a dot, and the dot fades. */
    private static void collapse(PoseStack pose, Draw buffers, ResourceLocation picture, float t, float w,
            float h, float px, float py, float pw, float ph, int light) {
        float sy, sx, white, fade;
        if (t < 0.25F) {
            sy = 1 - 0.98F * (t / 0.25F);
            sx = 1;
            white = t / 0.25F;
            fade = 1;
        } else if (t < 0.5F) {
            sy = 0.02F;
            sx = 1 - 0.97F * ((t - 0.25F) / 0.25F);
            white = 1;
            fade = 1;
        } else {
            sy = 0.02F;
            sx = 0.03F;
            white = 1;
            fade = 1 - (t - 0.5F) / 0.5F;
        }
        ResourceLocation solid = ClientConsoles.whiteTexture();
        Matrix4f m = pose.last().pose();
        quad(buffers, solid, m, 0, 0, w, h, 0, 0x000000, Math.round(255 * Math.min(1, fade * 2)), light);
        pose.pushPose();
        pose.translate(w / 2, h / 2, 0);
        pose.scale(sx, sy, 1);
        pose.translate(-w / 2, -h / 2, 0);
        m = pose.last().pose();
        int alpha = Math.round(255 * fade);
        if (picture != null && white < 1) quad(buffers, picture, m, px, py, pw, ph, 0.02F, 0xFFFFFF, alpha, light);
        quad(buffers, solid, m, px, py, pw, ph, 0.03F, 0xFFFFFF, Math.round(alpha * white), light);
        pose.popPose();
    }

    /** Flatscreen switched off (D133): the last picture fades to black, then the dark glass shows. */
    private static void fadeOut(PoseStack pose, Draw buffers, ResourceLocation picture, float t, float w,
            float h, float px, float py, float pw, float ph, int light) {
        Matrix4f m = pose.last().pose();
        int alpha = Math.round(255 * (1 - t));
        quad(buffers, ClientConsoles.whiteTexture(), m, 0, 0, w, h, 0, 0x000000, alpha, light);
        if (picture != null) quad(buffers, picture, m, px, py, pw, ph, 0.02F, 0xFFFFFF, alpha, light);
    }

    /** A textured rectangle in the glass frame, tinted by {@code rgb} (black for a plain black fill). */
    private static void quad(Draw buffers, ResourceLocation texture, Matrix4f m, float x, float y, float w,
            float h, float z, int rgb, int alpha, int light) {
        VertexConsumer v = buffers.getBuffer(CartridgeRenderTypes.text(texture));
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        // Same winding as Font's glyph quads, so it faces the viewer.
        v.addVertex(m, x, y, z).setColor(r, g, b, alpha).setUv(0, 0).setLight(light);
        v.addVertex(m, x, y + h, z).setColor(r, g, b, alpha).setUv(0, 1).setLight(light);
        v.addVertex(m, x + w, y + h, z).setColor(r, g, b, alpha).setUv(1, 1).setLight(light);
        v.addVertex(m, x + w, y, z).setColor(r, g, b, alpha).setUv(1, 0).setLight(light);
    }

    /** Tiny text in the picture's top-left corner. */
    private void corner(PoseStack pose, Draw buffers, Component text, float px, float py, float unit, int light) {
        float scale = unit / 20F;
        pose.pushPose();
        pose.translate(px + 0.6F * unit, py + 0.6F * unit, 0.34F);
        pose.scale(scale, scale, scale);
        buffers.text(font, text, 0, 0, 0xFFFFFFFF, true, pose.last().pose(), Font.DisplayMode.POLYGON_OFFSET, light);
        pose.popPose();
    }

    /** Small wrapped text, centred on the glass ({@code dy} px lower). */
    private void text(PoseStack pose, Draw buffers, Component message, int color, int light, float w, float h,
            float unit, float dy) {
        float scale = unit / 12F; // font px per glass px
        int maxWidth = (int) ((w - unit) / scale);
        List<FormattedCharSequence> lines = font.split(message, maxWidth);
        pose.pushPose();
        pose.translate(w / 2, h / 2 + dy, 0.34F);
        pose.scale(scale, scale, scale);
        float y = -lines.size() * font.lineHeight / 2F;
        for (FormattedCharSequence line : lines) {
            buffers.text(font, line, -font.width(line) / 2F, y, color, false, pose.last().pose(),
                    Font.DisplayMode.POLYGON_OFFSET, light);
            y += font.lineHeight;
        }
        pose.popPose();
    }
}
