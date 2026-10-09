package com.nanobuntv.cartridge.mixin.client;

import com.nanobuntv.cartridge.client.ClientConsoles;
import net.minecraft.client.model.HumanoidModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
//? if >=1.21.9 {
/*import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
*///?} else {
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
//?}

/**
 * A player playing at a console holds the pad out in both hands (D104); client/ConsoleRenderer draws the pad. The
 * player renderer became the avatar renderer in 1.21.9, choosing the pose per arm.
 */
//? if >=1.21.9 {
/*@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "getArmPose(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/world/entity/HumanoidArm;)Lnet/minecraft/client/model/HumanoidModel$ArmPose;",
            at = @At("HEAD"), cancellable = true)
    private static void cartridge$holdingPad(Avatar avatar, HumanoidArm arm, CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (ClientConsoles.holdsPad(avatar.getUUID())) cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
    }
}
*///?} else {
@Mixin(PlayerRenderer.class)
public abstract class PlayerRendererMixin {
    @Inject(method = "getArmPose", at = @At("HEAD"), cancellable = true)
    private static void cartridge$holdingPad(AbstractClientPlayer player, InteractionHand hand,
            CallbackInfoReturnable<HumanoidModel.ArmPose> cir) {
        if (ClientConsoles.holdsPad(player.getUUID())) cir.setReturnValue(HumanoidModel.ArmPose.CROSSBOW_HOLD);
    }
}
//?}
