package com.nanobuntv.cartridge.forge.platform;

import com.nanobuntv.cartridge.platform.NetworkHelper;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Each payload type is its own SimpleChannel, named after it, with one message: the payload, written and read by its
 * codec. Forge runs the handlers on the main thread ({@code consumerMainThread}).
 */
public final class ForgeNetworkHelper implements NetworkHelper {
    private static final String VERSION = "1";
    private static final Map<ResourceLocation, SimpleChannel> CHANNELS = new HashMap<>();

    /** The channel's message: any payload (each channel carries one type). */
    private record Message(CustomPacketPayload payload) {}

    @Override
    public <T extends CustomPacketPayload> void registerServerbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<T, ServerPlayer> handler) {
        channel(type, codec, NetworkDirection.PLAY_TO_SERVER, (payload, context) -> {
            ServerPlayer player = context.getSender();
            if (player != null) handler.accept(payload, player);
        });
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientbound(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {
        channel(type, codec, NetworkDirection.PLAY_TO_CLIENT, (payload, context) -> handler.accept(payload));
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> void channel(CustomPacketPayload.Type<T> type,
            StreamCodec<? super FriendlyByteBuf, T> codec, NetworkDirection direction,
            BiConsumer<T, net.minecraftforge.network.NetworkEvent.Context> handler) {
        SimpleChannel channel = NetworkRegistry.newSimpleChannel(type.id(), () -> VERSION, VERSION::equals, VERSION::equals);
        channel.messageBuilder(Message.class, 0, direction)
                .encoder((message, buf) -> codec.encode(buf, (T) message.payload()))
                .decoder(buf -> new Message(codec.decode(buf)))
                .consumerMainThread((message, context) -> handler.accept((T) message.payload(), context.get()))
                .add();
        CHANNELS.put(type.id(), channel);
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        CHANNELS.get(payload.type().id()).sendToServer(new Message(payload));
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        CHANNELS.get(payload.type().id()).send(PacketDistributor.PLAYER.with(() -> player), new Message(payload));
    }
}
