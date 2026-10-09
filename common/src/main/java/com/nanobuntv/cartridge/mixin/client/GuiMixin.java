package com.nanobuntv.cartridge.mixin.client;

import com.nanobuntv.cartridge.client.PlayMode;
import net.minecraft.client.gui.Gui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No crosshair over the big view (D69). From 26.2 the HUD is its own class. */
//? if >=26.2 {
/*@Mixin(net.minecraft.client.gui.Hud.class)
*///?} else {
@Mixin(Gui.class)
//?}
public abstract class GuiMixin {
    //? if >=26.1 {
    /*@Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    //?}
    private void cartridge$hideCrosshair(CallbackInfo ci) { // the target's arguments left out: they differ by version
        if (PlayMode.bigViewShown()) ci.cancel();
    }
}
