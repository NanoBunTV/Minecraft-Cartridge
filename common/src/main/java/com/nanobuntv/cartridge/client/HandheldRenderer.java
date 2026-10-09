package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.HandheldItem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * The game on a handheld's own screen (D158, D160), drawn over its item model wherever it's held (first and third
 * person), in the model's own space (0..1 across the block): up to 1.21.3 by {@code ItemRendererMixin}, from 1.21.4 by
 * {@link HandheldScreenRenderer}, a special model in the item's model definition. The screen sits on the model's
 * front (south) face; the glass rectangles match the models' {@code screen} elements.
 */
public final class HandheldRenderer {
    /** x0, y0, x1, y1 and the front's z, in model pixels: each handheld's glass. */
    private static final float[] BRICK = {5.75F, 9.2F, 10.25F, 13.25F, 9.17F};
    private static final float[] COLOR = {5.75F, 8.95F, 10.25F, 13.0F, 9.17F};
    /** The Advance's 3:2 screen, between the D-pad and the buttons (D185). */
    private static final float[] ADVANCE = {5.6F, 6.6F, 10.4F, 9.8F, 9.17F};
    /** The SP's, in its open lid. */
    private static final float[] ADVANCE_SP = {5.6F, 10.1F, 10.4F, 13.3F, 8.67F};

    /** What a handheld's screen shows: its game's picture on its glass ({@code rect} in model px), these columns of it. */
    public record Screen(ResourceLocation picture, float[] rect, float u0, float u1) {}

    private HandheldRenderer() {}

    /** The screen of a handheld stack with a game showing, or null. */
    @Nullable
    public static Screen screen(ItemStack stack) {
        if (!(stack.getItem() instanceof HandheldItem)) return null;
        ConsoleView view = ClientConsoles.handheldView(stack);
        if (view == null) return null;
        ResourceLocation picture = view.picture();
        if (picture == null) return null;
        float[] r = stack.is(CartridgeItems.POCKETRON_COLOR.get()) ? COLOR : stack.is(CartridgeItems.POCKETRON_ADVANCE.get())
                ? ADVANCE : stack.is(CartridgeItems.POCKETRON_ADVANCE_SP.get()) ? ADVANCE_SP : BRICK;
        int side = ClientConsoles.linkSide(stack); // linked (D177): its own Game Boy's half of the picture
        float u0 = side < 0 ? 0 : side * 0.5F, u1 = side < 0 ? 1 : u0 + 0.5F;
        return new Screen(picture, r, u0, u1);
    }

    public static void renderScreen(ItemStack stack, ItemDisplayContext context, PoseStack pose, Draw buffers) {
        if (context == ItemDisplayContext.GUI) return;
        Screen screen = screen(stack);
        if (screen != null) quad(pose.last().pose(), buffers.getBuffer(CartridgeRenderTypes.text(screen.picture())), screen);
    }

    /** The glass, full-bright, in the model's space (0..1). */
    public static void quad(Matrix4f m, VertexConsumer v, Screen screen) {
        float[] r = screen.rect();
        float x0 = r[0] / 16, y0 = r[1] / 16, x1 = r[2] / 16, y1 = r[3] / 16, z = r[4] / 16;
        float u0 = screen.u0(), u1 = screen.u1();
        int light = LightTexture.FULL_BRIGHT;
        // Both windings, so it shows whichever way the item's transform turned it.
        v.addVertex(m, x0, y1, z).setColor(255, 255, 255, 255).setUv(u0, 0).setLight(light);
        v.addVertex(m, x0, y0, z).setColor(255, 255, 255, 255).setUv(u0, 1).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(255, 255, 255, 255).setUv(u1, 1).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(255, 255, 255, 255).setUv(u1, 0).setLight(light);
        v.addVertex(m, x1, y1, z).setColor(255, 255, 255, 255).setUv(u1, 0).setLight(light);
        v.addVertex(m, x1, y0, z).setColor(255, 255, 255, 255).setUv(u1, 1).setLight(light);
        v.addVertex(m, x0, y0, z).setColor(255, 255, 255, 255).setUv(u0, 1).setLight(light);
        v.addVertex(m, x0, y1, z).setColor(255, 255, 255, 255).setUv(u0, 0).setLight(light);
    }
}
