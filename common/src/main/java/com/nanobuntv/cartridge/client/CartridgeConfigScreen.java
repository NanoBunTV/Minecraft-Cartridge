package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.config.ClientSettings;
import com.nanobuntv.cartridge.config.ControlProfile;
import com.nanobuntv.cartridge.config.ControlSet;
import com.nanobuntv.cartridge.config.PocketronPalette;
import com.nanobuntv.cartridge.config.ServerSettings;
import com.nanobuntv.cartridge.net.CartridgeNetwork;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.EnumControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerFieldControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/**
 * Cartridge's config screen (D122), built with YetAnotherConfigLib: a Client category (audio, handhelds), a Controls
 * category (D191: the keys and gamepad inputs every console shares, then a fold-out group per console family, each row
 * a button's key and gamepad input, {@link BindingController}) and a Server category, which can be edited only where
 * this game runs the server (singleplayer or a LAN host); a dedicated server's settings are its
 * {@code config/cartridge_mod-server.json5}. Saving the server settings sends them to every player (D123). Opened from
 * the mod list (NeoForge) or Mod Menu (Fabric), and from a console's GUI on its own controls.
 */
public final class CartridgeConfigScreen {
    private static final String KEY = "cartridge_mod.configuration.";

    private CartridgeConfigScreen() {}

    public static Screen create(Screen parent) {
        return create(parent, null);
    }

    /** {@code open}: opens on the Controls tab with this family's group unfolded (from a console's GUI), or null. */
    public static Screen create(Screen parent, @Nullable ControlSet open) {
        Minecraft mc = Minecraft.getInstance();
        boolean serverEditable = mc.getConnection() == null || mc.hasSingleplayerServer();
        boolean[] tabChosen = {open == null};
        return YetAnotherConfigLib.createBuilder()
                .title(Component.translatable(KEY + "title"))
                .category(clientCategory())
                .category(controlsCategory(open))
                .category(serverCategory(serverEditable))
                .screenInit(screen -> {
                    if (tabChosen[0]) return; // once: a resize keeps the player's tab
                    tabChosen[0] = true;
                    screen.tabNavigationBar.selectTab(1, false);
                })
                .save(() -> {
                    CartridgeConfig.saveClient();
                    if (serverEditable) {
                        CartridgeConfig.saveServer();
                        MinecraftServer server = mc.getSingleplayerServer();
                        if (server != null) server.execute(() -> CartridgeNetwork.broadcastSettings(server));
                    }
                })
                .build()
                .generateScreen(parent);
    }

    // --- Client -----------------------------------------------------------------------------------------------------

    private static ConfigCategory clientCategory() {
        ClientSettings c = CartridgeConfig.client();
        ClientSettings d = CartridgeConfig.clientDefaults();
        return ConfigCategory.createBuilder()
                .name(Component.translatable(KEY + "client"))
                .tooltip(Component.translatable(KEY + "client.tooltip"))
                .group(OptionGroup.createBuilder()
                        .name(Component.translatable(KEY + "audio"))
                        .description(describe("audio"))
                        .option(bool("playModeUnattenuatedAudio", d.playModeUnattenuatedAudio,
                                () -> c.playModeUnattenuatedAudio, v -> c.playModeUnattenuatedAudio = v))
                        .build())
                .group(OptionGroup.createBuilder()
                        .name(Component.translatable(KEY + "handhelds"))
                        .description(describe("handhelds"))
                        .option(Option.<PocketronPalette>createBuilder()
                                .name(Component.translatable(KEY + "pocketronPalette"))
                                .description(describe("pocketronPalette"))
                                .binding(d.pocketronPalette, () -> c.pocketronPalette, v -> c.pocketronPalette = v)
                                .controller(o -> EnumControllerBuilder.create(o).enumClass(PocketronPalette.class)
                                        .formatValue(v -> Component.translatable(KEY + "pocketronPalette." + v.coreValue)))
                                .build())
                        .build())
                .build();
    }

    // --- Controls (D191) ---------------------------------------------------------------------------------------------

    private static final String CONTROLS = "cartridge_mod.controls.";

    private static ConfigCategory controlsCategory(@Nullable ControlSet open) {
        ClientSettings c = CartridgeConfig.client();
        ClientSettings d = CartridgeConfig.clientDefaults();
        String pad = Gamepads.name();
        Component status = pad != null
                ? Component.translatable(CONTROLS + "connected", pad).withStyle(ChatFormatting.GREEN)
                : Component.translatable(CONTROLS + "no_gamepad").withStyle(ChatFormatting.GRAY);
        OptionGroup.Builder general = OptionGroup.createBuilder()
                .name(Component.translatable(CONTROLS + "general"))
                .description(OptionDescription.of(Component.translatable(CONTROLS + "general.tooltip")))
                .option(LabelOption.create(status))
                .option(bool("enabled", d.gamepadEnabled, () -> c.gamepadEnabled, v -> c.gamepadEnabled = v))
                .option(binding(Component.translatable(CONTROLS + "big_view"),
                        new BindingController.KeyAndPad(d.bigViewKey, d.padBigView),
                        () -> new BindingController.KeyAndPad(c.bigViewKey, c.padBigView),
                        v -> {
                            c.bigViewKey = v.key();
                            c.padBigView = v.pad();
                        }, null, null))
                .option(binding(Component.translatable(CONTROLS + "stop_playing"),
                        new BindingController.KeyAndPad(ControlSet.NO_KEY, d.padStopPlaying),
                        () -> new BindingController.KeyAndPad(ControlSet.NO_KEY, c.padStopPlaying),
                        v -> c.padStopPlaying = v.pad(),
                        Component.translatable(CONTROLS + "esc"), null));
        ConfigCategory.Builder category = ConfigCategory.createBuilder()
                .name(Component.translatable(CONTROLS + "title"))
                .tooltip(Component.translatable(CONTROLS + "title.tooltip"))
                .group(general.build());
        for (ControlSet set : ControlSet.values()) category.group(controlsGroup(set, set != open));
        return category.build();
    }

    /** One console family's buttons (D191), folded away unless it's the one asked for. */
    private static OptionGroup controlsGroup(ControlSet set, boolean collapsed) {
        ControlProfile profile = CartridgeConfig.client().controls(set);
        ControlProfile defaults = CartridgeConfig.clientDefaults().controls(set);
        OptionGroup.Builder group = OptionGroup.createBuilder()
                .name(Component.translatable(CONTROLS + set.id()))
                .description(OptionDescription.of(Component.translatable(CONTROLS + set.id() + ".tooltip")))
                .collapsed(collapsed);
        for (ControlSet.Button button : set.buttons()) {
            String name = button.name();
            boolean hasPad = button.pad() != null;
            group.option(binding(Component.translatable(CONTROLS + "button." + name),
                    new BindingController.KeyAndPad(defaults.key(name), defaults.pad(name)),
                    () -> new BindingController.KeyAndPad(profile.key(name), profile.pad(name)),
                    v -> {
                        profile.keys.put(name, v.key());
                        if (hasPad) profile.pad.put(name, v.pad());
                    },
                    null, hasPad ? null : Component.translatable(CONTROLS + "left_stick")));
        }
        if (set.stickAsDpad) {
            group.option(bool("leftStickAsDpad", defaults.leftStickAsDpad, () -> profile.leftStickAsDpad,
                    v -> profile.leftStickAsDpad = v));
        }
        return group.build();
    }

    private static Option<BindingController.KeyAndPad> binding(Component name, BindingController.KeyAndPad fallback,
            Supplier<BindingController.KeyAndPad> get, Consumer<BindingController.KeyAndPad> set,
            @Nullable Component fixedKey, @Nullable Component fixedPad) {
        return Option.<BindingController.KeyAndPad>createBuilder()
                .name(name)
                .description(OptionDescription.of(Component.translatable(CONTROLS + "binding.tooltip")))
                .binding(fallback, get, set)
                .customController(o -> new BindingController(o, fixedKey, fixedPad))
                .build();
    }

    // --- Server -----------------------------------------------------------------------------------------------------

    private static ConfigCategory serverCategory(boolean editable) {
        ServerSettings s = CartridgeConfig.server();
        ServerSettings d = CartridgeConfig.serverDefaults();
        Component tooltip = editable ? Component.translatable(KEY + "server.tooltip")
                : Component.translatable(KEY + "server.remote").withStyle(ChatFormatting.RED);
        return ConfigCategory.createBuilder()
                .name(Component.translatable(KEY + "server"))
                .tooltip(tooltip)
                .group(group("general", editable,
                        slider("spectatorRadius", editable, d.spectatorRadius, ServerSettings.SPECTATOR_MIN, ServerSettings.SPECTATOR_MAX,
                                () -> s.spectatorRadius, v -> s.spectatorRadius = v, "blocks")))
                .group(group("roms", editable,
                        slider("maxRomSizeMegabytes", editable, d.maxRomSizeMegabytes, ServerSettings.ROM_SIZE_MIN, ServerSettings.ROM_SIZE_MAX,
                                () -> s.maxRomSizeMegabytes, v -> s.maxRomSizeMegabytes = v, "megabytes"),
                        field("romStorageQuotaMegabytes", editable, d.romStorageQuotaMegabytes, ServerSettings.QUOTA_MIN, ServerSettings.QUOTA_MAX,
                                () -> s.romStorageQuotaMegabytes, v -> s.romStorageQuotaMegabytes = v),
                        slider("uploadPermissionLevel", editable, d.uploadPermissionLevel, ServerSettings.PERMISSION_MIN, ServerSettings.PERMISSION_MAX,
                                () -> s.uploadPermissionLevel, v -> s.uploadPermissionLevel = v, null),
                        boolOption("validateRomHeaders", editable, d.validateRomHeaders, () -> s.validateRomHeaders, v -> s.validateRomHeaders = v)))
                .group(group("cables", editable,
                        slider("cableRange", editable, d.cableRange, ServerSettings.CABLE_MIN, ServerSettings.CABLE_MAX,
                                () -> s.cableRange, v -> s.cableRange = v, "blocks"),
                        slider("maxTvsPerConsole", editable, d.maxTvsPerConsole, ServerSettings.TVS_MIN, ServerSettings.TVS_MAX,
                                () -> s.maxTvsPerConsole, v -> s.maxTvsPerConsole = v, null)))
                .group(group("netplay", editable,
                        slider("rollbackFrames", editable, d.rollbackFrames, ServerSettings.ROLLBACK_MIN, ServerSettings.ROLLBACK_MAX,
                                () -> s.rollbackFrames, v -> s.rollbackFrames = v, "frames"),
                        slider("maxInputDelayFrames", editable, d.maxInputDelayFrames, ServerSettings.DELAY_MIN, ServerSettings.DELAY_MAX,
                                () -> s.maxInputDelayFrames, v -> s.maxInputDelayFrames = v, "frames"),
                        field("resumeSnapshotSeconds", editable, d.resumeSnapshotSeconds, ServerSettings.SNAPSHOT_MIN, ServerSettings.SNAPSHOT_MAX,
                                () -> s.resumeSnapshotSeconds, v -> s.resumeSnapshotSeconds = v)))
                .group(group("peripherals", editable,
                        slider("lightGunRange", editable, d.lightGunRange, ServerSettings.GUN_MIN, ServerSettings.GUN_MAX,
                                () -> s.lightGunRange, v -> s.lightGunRange = v, "blocks"),
                        slider("handheldRange", editable, d.handheldRange, ServerSettings.HANDHELD_MIN, ServerSettings.HANDHELD_MAX,
                                () -> s.handheldRange, v -> s.handheldRange = v, "blocks"),
                        slider("linkCableRange", editable, d.linkCableRange, ServerSettings.LINK_MIN, ServerSettings.LINK_MAX,
                                () -> s.linkCableRange, v -> s.linkCableRange = v, "blocks")))
                .build();
    }

    private static OptionGroup group(String key, boolean editable, Option<?>... options) {
        OptionGroup.Builder group = OptionGroup.createBuilder()
                .name(Component.translatable(KEY + key))
                .description(describe(key));
        for (Option<?> option : options) group.option(option);
        return group.build();
    }

    // --- Options ----------------------------------------------------------------------------------------------------

    private static OptionDescription describe(String key) {
        return OptionDescription.of(Component.translatable(KEY + key + ".tooltip"));
    }

    private static Option<Boolean> bool(String key, boolean fallback, Supplier<Boolean> get, Consumer<Boolean> set) {
        return boolOption(key, true, fallback, get, set);
    }

    private static Option<Boolean> boolOption(String key, boolean editable, boolean fallback, Supplier<Boolean> get,
            Consumer<Boolean> set) {
        return Option.<Boolean>createBuilder()
                .name(Component.translatable(KEY + key))
                .description(describe(key))
                .binding(fallback, get, set)
                .controller(TickBoxControllerBuilder::create)
                .available(editable)
                .build();
    }

    /** A slider; {@code unit} (a lang key under {@code cartridge_mod.configuration.unit.}) labels the value, or null. */
    private static Option<Integer> slider(String key, boolean editable, int fallback, int min, int max, Supplier<Integer> get,
            Consumer<Integer> set, String unit) {
        Function<Integer, Component> format = unit == null ? v -> Component.literal(String.valueOf(v))
                : v -> Component.translatable(KEY + "unit." + unit, v);
        return Option.<Integer>createBuilder()
                .name(Component.translatable(KEY + key))
                .description(describe(key))
                .binding(fallback, get, set)
                .controller(o -> IntegerSliderControllerBuilder.create(o).range(min, max).step(1).formatValue(format::apply))
                .available(editable)
                .build();
    }

    /** A number field, for ranges too wide for a slider. */
    private static Option<Integer> field(String key, boolean editable, int fallback, int min, int max, Supplier<Integer> get,
            Consumer<Integer> set) {
        return Option.<Integer>createBuilder()
                .name(Component.translatable(KEY + key))
                .description(describe(key))
                .binding(fallback, get, set)
                .controller(o -> IntegerFieldControllerBuilder.create(o).range(min, max))
                .available(editable)
                .build();
    }
}
