package com.nanobuntv.cartridge.menu;

import com.nanobuntv.cartridge.platform.Services;
import java.util.function.Supplier;
import net.minecraft.world.inventory.MenuType;

public final class CartridgeMenus {
    public static final Supplier<MenuType<ConsoleMenu>> CONSOLE_8BIT = Services.REGISTRY.registerMenu("console_8bit", ConsoleMenu::new);
    public static final Supplier<MenuType<ConsoleMenu>> HANDHELD = Services.REGISTRY.registerMenu("handheld", ConsoleMenu::handheld);
    public static final Supplier<MenuType<AdapterMenu>> POCKETRON_ADAPTER = Services.REGISTRY.registerMenu("pocketron_adapter", AdapterMenu::new);
    public static final Supplier<MenuType<WriterMenu>> CARTRIDGE_WRITER = Services.REGISTRY.registerMenu("cartridge_writer", WriterMenu::new);

    private CartridgeMenus() {}

    public static void init() {}
}
