package com.nanobuntv.cartridge.fabric.platform;

import com.nanobuntv.cartridge.platform.NetworkHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
//? if >=1.20.5 {
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//?} else {
/*import java.util.HashMap;
import java.util.Map;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
*///?}

/**
 * Payload types are registered during common init. Client receivers can only be registered from the client
 * initializer, so they wait in a list until {@link #registerClientReceivers()}. Handlers run on the main thread.
 * Before 1.20.5 a payload is a channel id and a buffer: its codec writes and reads it.
 */
public final class FabricNetworkHelper implements NetworkHelper {
    private static final List<Runnable> CLIENT_RECEIVERS = new ArrayList<>();
    //? if <1.20.5 {
    /*private static final Map<ResourceLocation, StreamCodec<? super FriendlyByteBuf, ?>> CODECS = new HashMap<>();
    *///?}

    @Override
    public <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        //? if >=1.20.5 {
        PayloadTypeRegistry.playC2S().register(type, codec);
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload, context.player()));
        //?} else {
        /*CODECS.put(type.id(), codec);
        ServerPlayNetworking.registerGlobalReceiver(type.id(), (server, player, listener, buf, sender) -> {
            T payload = codec.decode(buf); // the buffer is released once this returns
            server.execute(() -> handler.accept(payload, player));
        });
        *///?}
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        //? if >=1.20.5 {
        PayloadTypeRegistry.playS2C().register(type, codec);
        CLIENT_RECEIVERS.add(() -> ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) -> handler.accept(payload)));
        //?} else {
        /*CODECS.put(type.id(), codec);
        CLIENT_RECEIVERS.add(() -> ClientPlayNetworking.registerGlobalReceiver(type.id(), (client, listener, buf, sender) -> {
            T payload = codec.decode(buf);
            client.execute(() -> handler.accept(payload));
        }));
        *///?}
    }

    /** Called from the client initializer. */
    public static void registerClientReceivers() {
        CLIENT_RECEIVERS.forEach(Runnable::run);
        CLIENT_RECEIVERS.clear();
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        //? if >=1.20.5 {
        ClientPlayNetworking.send(payload);
        //?} else {
        /*ClientPlayNetworking.send(payload.type().id(), encode(payload));
        *///?}
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        //? if >=1.20.5 {
        ServerPlayNetworking.send(player, payload);
        //?} else {
        /*ServerPlayNetworking.send(player, payload.type().id(), encode(payload));
        *///?}
    }

    //? if <1.20.5 {
    /*@SuppressWarnings("unchecked")
    private static FriendlyByteBuf encode(CustomPacketPayload payload) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ((StreamCodec<FriendlyByteBuf, CustomPacketPayload>) CODECS.get(payload.type().id())).encode(buf, payload);
        return buf;
    }
    *///?}
}
