package com.nanobuntv.cartridge.mixin.client;

// Up to 1.21.3 only: from 1.21.4 the item model definitions pick the models, and HandheldScreenRenderer draws the screen.
//? if <1.21.4 {
import com.mojang.blaze3d.vertex.PoseStack;
import com.nanobuntv.cartridge.client.Draw;
import com.nanobuntv.cartridge.client.HandheldRenderer;
import com.nanobuntv.cartridge.client.ItemIcons;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the flat icon instead of the 3D model in inventories and the hotbar (D43), the same way vanilla swaps the
 * trident and spyglass models per display context, and a cartridge in its console's shape (D143). A handheld's game
 * goes on its screen (D158), in the model's own space, before the pose is popped.
 */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow
    public abstract void render(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
            MultiBufferSource buffers, int light, int overlay, BakedModel model);

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cartridge$flatIcon(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
            MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        BakedModel swap = ItemIcons.modelFor(stack, context == ItemDisplayContext.GUI);
        if (swap == null || swap == model) {
            return;
        }
        ci.cancel();
        render(stack, context, leftHand, poseStack, buffers, light, overlay, swap);
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V"))
    private void cartridge$handheldScreen(ItemStack stack, ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
            MultiBufferSource buffers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        HandheldRenderer.renderScreen(stack, context, poseStack, Draw.of(buffers));
    }
}
//?}
