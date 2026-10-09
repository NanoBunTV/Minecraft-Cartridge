package com.nanobuntv.cartridge.neoforge.platform;

import com.nanobuntv.cartridge.platform.NetworkHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payloads wait in a list until RegisterPayloadHandlersEvent; {@link #attach(IEventBus)} hooks that up. NeoForge runs
 * handlers on the main thread by default.
 */
public final class NeoForgeNetworkHelper implements NetworkHelper {
    private static final String VERSION = "1";
    private static final List<Consumer<PayloadRegistrar>> REGISTRATIONS = new ArrayList<>();

    @Override
    public <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        REGISTRATIONS.add(registrar -> registrar.playToServer(type, codec,
                (payload, context) -> handler.accept(payload, (ServerPlayer) context.player())));
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        REGISTRATIONS.add(registrar -> registrar.playToClient(type, codec, (payload, context) -> handler.accept(payload)));
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        //? if >=1.21.6 {
        /*net.neoforged.neoforge.client.network.ClientPacketDistributor.sendToServer(payload);
        *///?} else {
        PacketDistributor.sendToServer(payload);
        //?}
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    /** Called once from the mod constructor, after common init has declared every payload. */
    public static void attach(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar(VERSION);
            REGISTRATIONS.forEach(r -> r.accept(registrar));
        });
    }
}
