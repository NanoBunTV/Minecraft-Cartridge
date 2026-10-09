package com.nanobuntv.cartridge.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.nanobuntv.cartridge.client.ClientConsoles;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 {
/*import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
*///?} else {
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
//?}

/**
 * A player playing at a console has the pad in their hands, not whatever they were holding (D104). From 1.21.9 the
 * layer gets a render state, whose entity id leads back to the player.
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    //? if >=1.21.9 {
    /*@Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/ArmedEntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void cartridge$holdingPad(PoseStack pose, SubmitNodeCollector collector, int light, ArmedEntityRenderState state,
            float yRot, float xRot, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (state instanceof AvatarRenderState avatar && mc.level != null && mc.level.getEntity(avatar.id) instanceof Player player
                && ClientConsoles.holdsPad(player.getUUID())) {
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void cartridge$holdingPad(PoseStack pose, MultiBufferSource buffers, int light, LivingEntity entity, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        if (entity instanceof Player player && ClientConsoles.holdsPad(player.getUUID())) ci.cancel();
    }
    //?}
}
