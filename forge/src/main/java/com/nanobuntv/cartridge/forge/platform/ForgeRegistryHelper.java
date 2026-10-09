package com.nanobuntv.cartridge.forge.platform;

import com.nanobuntv.cartridge.platform.RegistryHelper;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Forge registers through one DeferredRegister per registry and namespace, as NeoForge does. A mod declares its
 * entries, then {@link #attach(IEventBus)} hands its new registers to its mod bus (Cartridge's own, then an add-on's,
 * D195); the values exist once RegisterEvent has run.
 */
public final class ForgeRegistryHelper implements RegistryHelper {
    private static final Map<String, DeferredRegister<?>> REGISTERS = new LinkedHashMap<>();
    private static final Set<DeferredRegister<?>> ATTACHED = new HashSet<>();

    @Override
    @SuppressWarnings("unchecked")
    public <R, T extends R> Supplier<T> register(Registry<R> registry, ResourceLocation id, Supplier<T> factory) {
        var register = (DeferredRegister<R>) REGISTERS.computeIfAbsent(registry.key().location() + "|" + id.getNamespace(),
                key -> DeferredRegister.create(registry.key(), id.getNamespace()));
        return register.register(id.getPath(), factory);
    }

    @Override
    public CreativeModeTab.Builder creativeTabBuilder() {
        //? if >=1.20 {
        return CreativeModeTab.builder();
        //?} else {
        /*return CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0);
        *///?}
    }

    //? if <1.20 {
    /*private static final java.util.List<java.util.function.Consumer<net.minecraftforge.event.CreativeModeTabEvent.Register>> TABS =
            new java.util.ArrayList<>();

    @Override
    public void creativeTab(ResourceLocation id, net.minecraft.network.chat.Component title,
            Supplier<net.minecraft.world.item.ItemStack> icon, java.util.function.Consumer<CreativeModeTab.Output> items) {
        // Before 1.20 tabs aren't registry entries: Forge asks for them in an event.
        TABS.add(event -> event.registerCreativeModeTab(id, builder -> builder.title(title).icon(icon)
                .displayItems((parameters, output) -> items.accept(output))));
    }
    *///?}

    @Override
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BlockEntityFactory<T> factory, Block... blocks) {
        return BlockEntityType.Builder.of(factory::create, blocks).build(null);
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
        //? if <1.20 {
        /*var tabs = new java.util.ArrayList<>(TABS);
        TABS.clear();
        modBus.addListener((net.minecraftforge.event.CreativeModeTabEvent.Register event) -> tabs.forEach(t -> t.accept(event)));
        *///?}
    }
}
