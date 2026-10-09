package com.nanobuntv.cartridge.client;

// From 1.21.4 only: item models are data, and this lets the cartridge's pick its shape (up to 1.21.3, ItemIcons).
//? if >=1.21.4 {
/*import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.nanobuntv.cartridge.item.CartridgeItem;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/^*
 * The item model property {@code cartridge_mod:cartridge_shape}: the shape a cartridge takes for its game's console
 * ({@link CartridgeItem#SHAPES}, D143), for the cartridge's model definition to {@code select} on.
 ^/
public record CartridgeShapeProperty() implements SelectItemModelProperty<String> {
    public static final SelectItemModelProperty.Type<CartridgeShapeProperty, String> TYPE =
            SelectItemModelProperty.Type.create(MapCodec.unit(new CartridgeShapeProperty()), Codec.STRING);

    @Override
    public String get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed,
            ItemDisplayContext context) {
        return CartridgeItem.shape(stack);
    }

    @Override
    public Codec<String> valueCodec() {
        return Codec.STRING;
    }

    @Override
    public SelectItemModelProperty.Type<CartridgeShapeProperty, String> type() {
        return TYPE;
    }
}
*///?}
