package com.nanobuntv.cartridge.neoforge.platform;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import com.nanobuntv.cartridge.platform.RegistryHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * NeoForge registers through one DeferredRegister per registry and namespace. A mod declares its entries, then
 * {@link #attach(IEventBus)} hands its new registers to its mod bus (Cartridge's own, then an add-on's, D195); the
 * values exist once RegisterEvent has run.
 */
public final class NeoForgeRegistryHelper implements RegistryHelper {
    private static final Map<String, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();
    private static final java.util.Set<DeferredRegister<?>> ATTACHED = new java.util.HashSet<>();

    @Override
    @SuppressWarnings("unchecked")
    public <R, T extends R> Supplier<T> register(Registry<R> registry, ResourceLocation id, Supplier<T> factory) {
        var register = (DeferredRegister<R>) REGISTERS.computeIfAbsent(registry.key().location() + "|" + id.getNamespace(),
                key -> DeferredRegister.create(registry.key(), id.getNamespace()));
        return register.register(id.getPath(), factory);
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        return CreativeModeTab.builder();
    }

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
        //? if >=1.21.2 {
        /*return new BlockEntityType<>(factory::create, java.util.Set.of(blocks));
        *///?} else {
        return BlockEntityType.Builder.of(factory::create, blocks).build(null);
        //?}
    }

    @Override
    public <T extends AbstractContainerMenu> Supplier<MenuType<T>> registerMenu(String name, MenuFactory<T> factory) {
        return register(BuiltInRegistries.MENU, name, () -> new MenuType<>(factory::create, FeatureFlags.VANILLA_SET));
    }

    /** Called from a mod's constructor after its common init has declared every entry: its registers join its bus. */
    public static void attach(IEventBus modBus) {
        for (DeferredRegister<?> register : REGISTERS.values()) {
            if (ATTACHED.add(register)) register.register(modBus);
        }
    }
}
