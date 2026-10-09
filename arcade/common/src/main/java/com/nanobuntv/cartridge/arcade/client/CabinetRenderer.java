package com.nanobuntv.cartridge.arcade.client;

import com.nanobuntv.cartridge.client.CartridgeRenderTypes;
import com.nanobuntv.cartridge.client.Draw;
import com.nanobuntv.cartridge.client.CartridgeBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nanobuntv.cartridge.arcade.block.ArcadeCabinetBlock;
import com.nanobuntv.cartridge.arcade.block.ArcadeCabinetBlockEntity;
import com.nanobuntv.cartridge.block.DisplayBlock;
import com.nanobuntv.cartridge.block.Screen;
import com.nanobuntv.cartridge.client.ClientConsoles;
import com.nanobuntv.cartridge.client.ConsoleRenderer;
import com.nanobuntv.cartridge.client.PlayMode;
import com.nanobuntv.cartridge.client.TvRenderer;
import com.nanobuntv.cartridge.item.CartridgeContents;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.jetbrains.annotations.Nullable;

/**
 * Everything drawn on an Arcade Cabinet that isn't its block model (D198, D205):
 * <ul>
 *   <li>its own square screen, as a CRT TV shows the picture (Cartridge's TvRenderer), upright games standing;</li>
 *   <li>the marquee, lit while it's on, with the board's title across it;</li>
 *   <li>the AV Cable to each TV it feeds, from the jack at its back (D18, D103);</li>
 *   <li>a glow on the half of the control panel under the crosshair: the player it would take (D202).</li>
 * </ul>
 */
public class CabinetRenderer extends CartridgeBlockEntityRenderer<ArcadeCabinetBlockEntity> {
    private static final float[] AV_OFFSET = {1, 0, -1};
    private static final int MARQUEE_LIGHT = 0xFFF2D0, MARQUEE_TEXT = 0xFF1A1A1A;
    private static final float GROW = 0.2F;

    private final TvRenderer screen;
    private final Font font;

    public CabinetRenderer(BlockEntityRendererProvider.Context context) {
        this.screen = new TvRenderer(context);
        this.font = context.getFont();
    }

    @Override
    protected boolean drawsOffScreen(@Nullable ArcadeCabinetBlockEntity cabinet) {
        return true; // two blocks tall (and wide), and cables to TVs
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /** NeoForge's culling box (an override there, a plain method on Fabric): the cabinet and its TVs. */
    public AABB getRenderBoundingBox(ArcadeCabinetBlockEntity cabinet) {
        AABB box = new AABB(cabinet.getBlockPos()).inflate(2);
        for (BlockPos tv : cabinet.tvs()) box = box.minmax(new AABB(tv));
        return box;
    }

    @Override
    public void draw(ArcadeCabinetBlockEntity cabinet, float partialTick, PoseStack pose, Draw buffers,
            int light, int overlay) {
        Level level = cabinet.getLevel();
        BlockState state = cabinet.getBlockState();
        if (level == null || !(state.getBlock() instanceof ArcadeCabinetBlock block)) return;
        BlockPos pos = cabinet.getBlockPos();
        Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
        ArcadeCabinetBlock.Geometry g = block.geometry();

        screen.drawScreen(pos, block.screen(pos, facing), pos, pose, buffers); // D205
        if (cabinet.powered()) marquee(cabinet, g, facing, pose, buffers);

        double[] jack = g.jack();
        for (BlockPos tv : cabinet.tvs()) {
            BlockState tvState = level.getBlockState(tv);
            DisplayBlock display = DisplayBlock.of(tvState);
            if (display == null) continue;
            for (int i = 0; i < ConsoleRenderer.AV_COLORS.length; i++) {
                Vec3 from = DisplayBlock.point(pos, facing, jack[0] + AV_OFFSET[i], jack[1], jack[2]);
                Vec3 to = display.jack(level, tv, tvState, i, from);
                ConsoleRenderer.cable(buffers, pose, level, pos, from, to, ConsoleRenderer.AV_COLORS[i], ConsoleRenderer.CABLE_RADIUS);
            }
        }
        highlight(cabinet, block, facing, pose, buffers);
    }

    /** The lit marquee and the game's title across it (D198), full-bright. */
    private void marquee(ArcadeCabinetBlockEntity cabinet, ArcadeCabinetBlock.Geometry g, Direction facing, PoseStack pose,
            Draw buffers) {
        double[] m = g.marquee();
        BlockPos pos = cabinet.getBlockPos();
        // The face is at z = m[4]; drawn a hair in front of it, towards the viewer (-z in the north model).
        Vec3 origin = DisplayBlock.point(pos, facing, m[2], m[3], m[4] - 0.05);
        Vec3 right = DisplayBlock.point(pos, facing, m[0], m[3], m[4] - 0.05).subtract(origin);
        Vec3 down = DisplayBlock.point(pos, facing, m[2], m[1], m[4] - 0.05).subtract(origin);
        float w = (float) right.length() * 16, h = (float) down.length() * 16;
        pose.pushPose();
        frame(pose, pos, origin, right, down);
        int full = LightTexture.FULL_BRIGHT;
        quad(buffers, pose.last().pose(), 0, 0, w, h, 0, MARQUEE_LIGHT, full);
        CartridgeContents game = cabinet.contents();
        if (game != null) {
            Component title = Component.literal(MameLog.marqueeTitle(game.title())).withStyle(ChatFormatting.BOLD);
            float scale = Math.min((h * 0.7F) / font.lineHeight, (w * 0.92F) / Math.max(1, font.width(title)));
            pose.translate(w / 2, h / 2, 0.02F); // just in front of the light
            pose.scale(scale, scale, scale);
            buffers.text(font, title, -font.width(title) / 2F, -font.lineHeight / 2F + 0.5F, MARQUEE_TEXT, false,
                    pose.last().pose(), Font.DisplayMode.POLYGON_OFFSET, full);
        }
        pose.popPose();
    }

    /** A frame on a face: x along its top edge to the right, y down, z out of it (towards the viewer), in px. */
    private static void frame(PoseStack pose, BlockPos pos, Vec3 origin, Vec3 right, Vec3 down) {
        Vec3 r = right.normalize(), d = down.normalize(), n = d.cross(r).normalize();
        pose.translate(origin.x - pos.getX(), origin.y - pos.getY(), origin.z - pos.getZ());
        pose.last().pose().mul(new Matrix4f(
                (float) r.x, (float) r.y, (float) r.z, 0,
                (float) d.x, (float) d.y, (float) d.z, 0,
                (float) n.x, (float) n.y, (float) n.z, 0,
                0, 0, 0, 1));
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
    }

    private static void quad(Draw buffers, Matrix4f m, float x, float y, float w, float h, float z, int rgb, int light) {
        VertexConsumer v = buffers.getBuffer(CartridgeRenderTypes.text(ClientConsoles.whiteTexture()));
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        v.addVertex(m, x, y, z).setColor(r, g, b, 255).setUv(0, 0).setLight(light);
        v.addVertex(m, x, y + h, z).setColor(r, g, b, 255).setUv(0, 1).setLight(light);
        v.addVertex(m, x + w, y + h, z).setColor(r, g, b, 255).setUv(1, 1).setLight(light);
        v.addVertex(m, x + w, y, z).setColor(r, g, b, 255).setUv(1, 0).setLight(light);
    }

    /** The glow on the player's half of the panel under the crosshair (D202), as consoles' ports glow (D62). */
    private static void highlight(ArcadeCabinetBlockEntity cabinet, ArcadeCabinetBlock block, Direction facing, PoseStack pose,
            Draw buffers) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos pos = cabinet.getBlockPos();
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || mc.level == null
                || PlayMode.isPlaying(pos)) {
            return;
        }
        BlockState hitState = mc.level.getBlockState(hit.getBlockPos());
        if (!hitState.is(block) || !block.anchor(hitState, hit.getBlockPos()).equals(pos)) return;
        int player = block.player(hit, cabinet.getBlockState(), pos);
        if (player < 0) return;
        ArcadeCabinetBlock.Geometry g = block.geometry();
        double x1 = g.players() == 1 ? 0.5 : player == 0 ? 0.25 : -15.5, x2 = g.players() == 1 ? 15.5 : player == 0 ? 15.5 : -0.25;
        float px = 1 / 16F;
        AABB box = new AABB((x1 - GROW) * px, (g.panelY()[0] + 0.5 - GROW) * px, (0 - GROW) * px,
                (x2 + GROW) * px, (13.5 + GROW) * px, (g.panelZ() - 0.5 + GROW) * px);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 180.0);
        CartridgeRenderTypes.filledBox(pose, buffers, box, 1.0F, 0.95F, 0.6F, 0.2F + 0.15F * pulse);
        CartridgeRenderTypes.outlineBox(pose, buffers, box, 1.0F, 1.0F, 1.0F, 0.9F);
        pose.popPose();
    }
}
