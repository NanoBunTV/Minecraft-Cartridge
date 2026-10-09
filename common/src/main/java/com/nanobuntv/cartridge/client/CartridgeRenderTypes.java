package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
*///?} else {
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
//?}

/**
 * The render types the mod draws with, and its highlight boxes, for every Minecraft version (Porting.md): 1.21.11
 * moved the render types into {@code RenderTypes} and dropped {@code LevelRenderer}'s box helpers.
 */
public final class CartridgeRenderTypes {
    private CartridgeRenderTypes() {}

    /** Full-bright, unlit, textured quads (text's own type): the pictures, the glass, the marquee. */
    public static RenderType text(ResourceLocation texture) {
        //? if >=1.21.11 {
        /*return RenderTypes.text(texture);
        *///?} else {
        return RenderType.text(texture);
        //?}
    }

    /** Lit, textured, both faces: the cables. */
    public static RenderType entityCutoutNoCull(ResourceLocation texture) {
        //? if >=26.1 {
        /*return RenderTypes.entityCutout(texture); // 26.1: no cull by default (entityCutoutCull culls)
        *///?} else if >=1.21.11 {
        /*return RenderTypes.entityCutoutNoCull(texture);
        *///?} else {
        return RenderType.entityCutoutNoCull(texture);
        //?}
    }

    /** A translucent filled box, the glow on a clickable part (D62). */
    public static void filledBox(PoseStack pose, Draw buffers, AABB box, float r, float g, float b, float a) {
        //? if >=1.21.11 {
        /*VertexConsumer v = buffers.getBuffer(RenderTypes.debugFilledBox());
        Matrix4f m = pose.last().pose();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        float[][] faces = {
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1}, // down
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0}, // up
                {x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0}, // north
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1}, // south
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0}, // west
                {x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1}, // east
        };
        for (float[] f : faces) {
            for (int i = 0; i < 12; i += 3) v.addVertex(m, f[i], f[i + 1], f[i + 2]).setColor(r, g, b, a);
        }
        *///?} else {
        LevelRenderer.addChainedFilledBoxVertices(pose, buffers.getBuffer(RenderType.debugFilledBox()),
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, a);
        //?}
    }

    /** A box's outline, as a block's. */
    public static void outlineBox(PoseStack pose, Draw buffers, AABB box, float r, float g, float b, float a) {
        //? if >=1.21.11 {
        /*int color = (Math.round(a * 255) << 24) | (Math.round(r * 255) << 16) | (Math.round(g * 255) << 8) | Math.round(b * 255);
        VertexConsumer v = buffers.getBuffer(RenderTypes.lines());
        PoseStack.Pose last = pose.last();
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ;
        float x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        float[][] edges = {
                {x0, y0, z0, x1, y0, z0}, {x0, y1, z0, x1, y1, z0}, {x0, y0, z1, x1, y0, z1}, {x0, y1, z1, x1, y1, z1},
                {x0, y0, z0, x0, y1, z0}, {x1, y0, z0, x1, y1, z0}, {x0, y0, z1, x0, y1, z1}, {x1, y0, z1, x1, y1, z1},
                {x0, y0, z0, x0, y0, z1}, {x1, y0, z0, x1, y0, z1}, {x0, y1, z0, x0, y1, z1}, {x1, y1, z0, x1, y1, z1},
        };
        for (float[] e : edges) {
            float nx = e[3] - e[0], ny = e[4] - e[1], nz = e[5] - e[2]; // along the edge, as vanilla's outlines do
            v.addVertex(last, e[0], e[1], e[2]).setColor(color).setNormal(last, nx, ny, nz).setLineWidth(2);
            v.addVertex(last, e[3], e[4], e[5]).setColor(color).setNormal(last, nx, ny, nz).setLineWidth(2);
        }
        *///?} else {
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, r, g, b, a);
        //?}
    }
}
