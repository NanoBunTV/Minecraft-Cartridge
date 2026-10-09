package com.nanobuntv.cartridge.mixin.client;

// From 1.21.2 only: the player's movement input became ClientInput, its move vector protected (LocalPlayerMixin).
//? if >=1.21.2 {
/*import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientInput.class)
public interface ClientInputAccessor {
    @Accessor("moveVector")
    void cartridge$setMoveVector(Vec2 moveVector);
}
*///?}
