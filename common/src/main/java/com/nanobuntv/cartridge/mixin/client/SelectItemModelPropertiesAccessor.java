package com.nanobuntv.cartridge.mixin.client;

// From 1.21.4 only: registers the mod's item model select property (client/CartridgeClient); Fabric has no API for it.
//? if >=1.21.4 {
/*import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperties;
import net.minecraft.client.renderer.item.properties.select.SelectItemModelProperty;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SelectItemModelProperties.class)
public interface SelectItemModelPropertiesAccessor {
    @Accessor("ID_MAPPER")
    static ExtraCodecs.LateBoundIdMapper<ResourceLocation, SelectItemModelProperty.Type<?, ?>> cartridge$ids() {
        throw new AssertionError();
    }
}
*///?}
