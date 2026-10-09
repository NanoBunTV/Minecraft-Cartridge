package com.nanobuntv.cartridge.mixin.compat;

import com.nanobuntv.cartridge.client.PlayMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cartridge reads gamepads itself (D118); if Controlify is installed too, its own game actions (attack, use, inventory,
 * pause, hotbar...) wait until the player stops playing. Looking around with the stick still works (D66), and
 * movement is stopped by mixin/client/LocalPlayerMixin. Targeted by name, so nothing compiles against Controlify and
 * every version of it with this class and method is covered.
 */
@Mixin(targets = "dev.isxander.controlify.ingame.InGameInputHandler", remap = false)
public abstract class InGameInputHandlerMixin {
    @Inject(method = "handleKeybinds", at = @At("HEAD"), cancellable = true, remap = false)
    private void cartridge$playing(CallbackInfo ci) {
        if (PlayMode.active()) ci.cancel();
    }
}
