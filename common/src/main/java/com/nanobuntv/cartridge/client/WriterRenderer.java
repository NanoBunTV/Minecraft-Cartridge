package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.nanobuntv.cartridge.blockentity.CartridgeWriterBlockEntity;
import net.minecraft.Util;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;

/**
 * The Cartridge Writer's moving parts (D45): the cart in the main slot stands in the socket on top, label to the front,
 * and the status LED glows red (blinking) while an upload is on its way and green while a burned cart sits in it.
 * Positions are the north-facing model's (models/block/cartridge_writer.json).
 */
public class WriterRenderer extends CartridgeBlockEntityRenderer<CartridgeWriterBlockEntity> {
    private static final float CART_SCALE = 0.75F; // the 10 px cart fits the 8 px socket
    private static final float SOCKET_X = 7, SOCKET_Z = 7, SOCKET_TOP = 7, CART_SINK = 0.5F;
    private static final float CART_BOTTOM = 2.5F; // the cartridge model's lowest pixel

    public WriterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void draw(CartridgeWriterBlockEntity writer, float partialTick, PoseStack pose, Draw buffers,
            int light, int overlay) {
        pose.pushPose();
        float facing = writer.getBlockState().getValue(HorizontalDirectionalBlock.FACING).toYRot();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing));
        pose.translate(-0.5, 0, -0.5);

        ItemStack cart = writer.getItem(CartridgeWriterBlockEntity.MAIN);
        if (!cart.isEmpty()) {
            pose.pushPose();
            // Item models are drawn centred on the origin (px / 16 - 0.5): bring the cart's bottom edge to the socket.
            pose.translate(SOCKET_X / 16, (SOCKET_TOP - CART_SINK) / 16 + (0.5F - CART_BOTTOM / 16) * CART_SCALE, SOCKET_Z / 16);
            pose.mulPose(Axis.YP.rotationDegrees(180)); // the label (+z in the item model) faces the front
            pose.scale(CART_SCALE, CART_SCALE, CART_SCALE);
            buffers.item(cart, ItemDisplayContext.NONE, light, overlay, pose, writer.getLevel(), 0);
            pose.popPose();
        }

        boolean blink = (Util.getMillis() / 250) % 2 == 0;
        if (writer.busy() ? blink : CartridgeWriterBlockEntity.isBurned(cart)) {
            float r = writer.busy() ? 1.0F : 0.3F, g = writer.busy() ? 0.15F : 1.0F, b = 0.2F;
            float px = 1 / 16F;
            // Just in front of the model's status_led (x 3.125-3.875, y 3.125-3.875, front face at z 1.85).
            CartridgeRenderTypes.filledBox(pose, buffers, new AABB(3.1 * px, 3.1 * px, 1.8 * px, 3.9 * px, 3.9 * px, 1.84 * px),
                    r, g, b, 1.0F);
        }
        pose.popPose();
    }
}
