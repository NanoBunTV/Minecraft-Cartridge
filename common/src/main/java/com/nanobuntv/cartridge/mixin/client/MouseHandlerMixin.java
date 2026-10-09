package com.nanobuntv.cartridge.mixin.client;

import com.nanobuntv.cartridge.client.PlayMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//? if >=1.21.9 {
/*import net.minecraft.client.input.MouseButtonInfo;
*///?}

/** In play mode, mouse buttons can be bound to the console and scrolling is ignored; looking around still works (D66). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    //? if >=1.21.9 {
    /*@Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void cartridge$playModeButton(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && PlayMode.onMouseButton(info.button(), action)) {
            ci.cancel();
        }
    }
    *///?} else {
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void cartridge$playModeButton(long window, int button, int action, int modifiers, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && PlayMode.onMouseButton(button, action)) {
            ci.cancel();
        }
    }
    //?}

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void cartridge$playModeScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (window == Minecraft.getInstance().getWindow().getWindow() && PlayMode.onScroll()) ci.cancel();
    }
}
