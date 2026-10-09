package com.nanobuntv.cartridge.mixin.client;

import com.nanobuntv.cartridge.client.PlayMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.2 {
/*import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
*///?} else {
import net.minecraft.client.player.Input;
//?}

/**
 * In play mode the player stands still (D20) whatever moves them: the keyboard's keys go to the console anyway, but a
 * gamepad mod (such as Controlify, D118) has its own movement input, which is zeroed here after it's read. From
 * 1.21.2 the input is the pressed keys (a record) and a move vector.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    //? if >=1.21.2 {
    /*@Shadow
    public ClientInput input;

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/ClientInput;tick()V", shift = At.Shift.AFTER))
    private void cartridge$standStill(CallbackInfo ci) {
        if (!PlayMode.active() || PlayMode.walking()) return; // D168: a handheld played with a gamepad walks
        input.keyPresses = Input.EMPTY;
        ((ClientInputAccessor) input).cartridge$setMoveVector(Vec2.ZERO);
    }
    *///?} else {
    @Shadow
    public Input input;

    @Inject(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/Input;tick(ZF)V", shift = At.Shift.AFTER))
    private void cartridge$standStill(CallbackInfo ci) {
        if (!PlayMode.active() || PlayMode.walking()) return; // D168: a handheld played with a gamepad walks
        input.leftImpulse = 0;
        input.forwardImpulse = 0;
        input.up = input.down = input.left = input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
    }
    //?}
}
