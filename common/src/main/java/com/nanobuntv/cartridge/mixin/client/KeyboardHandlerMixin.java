package com.nanobuntv.cartridge.mixin.client;

import com.nanobuntv.cartridge.client.PlayMode;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 {
/*import net.minecraft.client.input.KeyEvent;
*///?}

/** In play mode, keys go to the console instead of the game (D20, D22). */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    //? if >=1.21.9 {
    /*@Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void cartridge$playMode(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && PlayMode.onKey(event.key(), event.scancode(), action)) {
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void cartridge$playMode(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && PlayMode.onKey(key, scancode, action)) {
            ci.cancel();
        }
    }
    //?}
}
