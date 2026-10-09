package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
//? if <26.2 {
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
//?}
//? if >=1.21.11 {
/*import net.minecraft.client.renderer.rendertype.RenderType;
*///?} else {
import net.minecraft.client.renderer.RenderType;
//?}

/**
 * Where the mod's renderers draw in the world, on every Minecraft version (Porting.md): geometry by render type,
 * text and items. Up to 26.1 a {@code MultiBufferSource} ({@link #of}); from 1.21.9 block entity renderers record and
 * submit instead ({@link RecordingBuffers}), and 26.2 dropped buffer sources and drawing text into them altogether.
 */
public interface Draw {
    /** A consumer for custom geometry of this render type (positions as transformed by the caller's matrices). */
    VertexConsumer getBuffer(RenderType type);

    /** Text at x, y in {@code pose}'s space, as a world label: {@code color} with its alpha byte. */
    void text(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow, Matrix4f pose,
            Font.DisplayMode mode, int light);

    default void text(Font font, Component text, float x, float y, int color, boolean shadow, Matrix4f pose,
            Font.DisplayMode mode, int light) {
        text(font, text.getVisualOrderText(), x, y, color, shadow, pose, mode, light);
    }

    /** An item, as the item renderer draws it in the world. */
    void item(ItemStack stack, ItemDisplayContext context, int light, int overlay, PoseStack pose, Level level, int seed);

    //? if <26.2 {
    /** Drawing straight into a buffer source (until 26.2 removed them). */
    static Draw of(MultiBufferSource buffers) {
        return new Draw() {
            @Override
            public VertexConsumer getBuffer(RenderType type) {
                return buffers.getBuffer(type);
            }

            @Override
            public void text(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow, Matrix4f pose,
                    Font.DisplayMode mode, int light) {
                font.drawInBatch(text, x, y, color, shadow, pose, buffers, mode, 0, light);
            }

            @Override
            public void item(ItemStack stack, ItemDisplayContext context, int light, int overlay, PoseStack pose, Level level,
                    int seed) {
                //? if <1.21.9 {
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, context, light, overlay, pose, buffers, level, seed);
                //?}
            }
        };
    }
    //?}
}
