package com.nanobuntv.cartridge.net;

import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.item.Handhelds;
import com.nanobuntv.cartridge.netplay.NetSessions;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.rom.RomServer;
import com.nanobuntv.cartridge.save.SaveServer;
import java.util.function.Consumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Payload registration. Client handlers go through static hooks that the client init fills in, so this class (loaded
 * on dedicated servers too) never references client classes.
 */
public final class CartridgeNetwork {
    public static Consumer<EnterPlayModePayload> onEnterPlayMode = payload -> {};
    public static Consumer<RomUploadStatusPayload> onUploadStatus = payload -> {};
    public static Consumer<RomDataPayload> onRomData = payload -> {};
    public static Consumer<SaveFilePayload> onSaveFile = payload -> {};
    public static Consumer<RomListPayload> onRomList = payload -> {};
    /** Every netplay payload for a console's game (netplay/NetSessions on the server side). */
    public static Consumer<CustomPacketPayload> onSession = payload -> {};

    private CartridgeNetwork() {}

    public static void init() {
        // Server settings (D123)
        Services.NETWORK.registerClientbound(ServerSettingsPayload.TYPE, ServerSettingsPayload.STREAM_CODEC,
                payload -> CartridgeConfig.setSynced(payload.settings()));
        Services.PLATFORM.onPlayerJoin(CartridgeNetwork::sendSettings);

        Services.NETWORK.registerClientbound(EnterPlayModePayload.TYPE, EnterPlayModePayload.STREAM_CODEC,
                payload -> onEnterPlayMode.accept(payload));
        Services.NETWORK.registerServerbound(LinkRequestPayload.TYPE, LinkRequestPayload.STREAM_CODEC,
                (payload, player) -> Handhelds.request(player, payload.target())); // D176
        Services.NETWORK.registerServerbound(LeavePlayModePayload.TYPE, LeavePlayModePayload.STREAM_CODEC,
                CartridgeNetwork::leavePlayMode);

        // ROM transfers (D14, D16, D74; rom/RomServer)
        Services.NETWORK.registerServerbound(RomUploadStartPayload.TYPE, RomUploadStartPayload.STREAM_CODEC,
                RomServer::onUploadStart);
        Services.NETWORK.registerServerbound(RomUploadChunkPayload.TYPE, RomUploadChunkPayload.STREAM_CODEC,
                RomServer::onUploadChunk);
        Services.NETWORK.registerServerbound(RomUploadCancelPayload.TYPE, RomUploadCancelPayload.STREAM_CODEC,
                (payload, player) -> RomServer.cancelUpload(player));
        Services.NETWORK.registerClientbound(RomUploadStatusPayload.TYPE, RomUploadStatusPayload.STREAM_CODEC,
                payload -> onUploadStatus.accept(payload));
        Services.NETWORK.registerServerbound(RomRequestPayload.TYPE, RomRequestPayload.STREAM_CODEC, RomServer::onRequest);
        Services.NETWORK.registerClientbound(RomDataPayload.TYPE, RomDataPayload.STREAM_CODEC,
                payload -> onRomData.accept(payload));
        Services.NETWORK.registerServerbound(RomAckPayload.TYPE, RomAckPayload.STREAM_CODEC, RomServer::onAck);
        // Which games the server has (D124-D126)
        Services.NETWORK.registerClientbound(RomListPayload.TYPE, RomListPayload.STREAM_CODEC,
                payload -> onRomList.accept(payload));
        Services.PLATFORM.onServerStarted(RomServer::onServerStarted);
        Services.PLATFORM.onPlayerJoin(RomServer::onPlayerJoin);

        // Netplay (D12, D13, D83-D90; netplay/)
        Services.NETWORK.registerServerbound(SessionJoinPayload.TYPE, SessionJoinPayload.STREAM_CODEC, NetSessions::onJoin);
        Services.NETWORK.registerServerbound(SessionLeavePayload.TYPE, SessionLeavePayload.STREAM_CODEC, NetSessions::onLeave);
        Services.NETWORK.registerServerbound(InputFramesPayload.TYPE, InputFramesPayload.STREAM_CODEC, NetSessions::onInputs);
        Services.NETWORK.registerServerbound(ChecksumPayload.TYPE, ChecksumPayload.STREAM_CODEC, NetSessions::onChecksum);
        Services.NETWORK.registerServerbound(StateUploadPayload.TYPE, StateUploadPayload.STREAM_CODEC, NetSessions::onStateUpload);
        Services.NETWORK.registerServerbound(SessionPingPayload.TYPE, SessionPingPayload.STREAM_CODEC, NetSessions::onPing);
        Services.NETWORK.registerClientbound(SessionInputsPayload.TYPE, SessionInputsPayload.STREAM_CODEC, CartridgeNetwork::session);
        Services.NETWORK.registerClientbound(SessionSyncPayload.TYPE, SessionSyncPayload.STREAM_CODEC, CartridgeNetwork::session);
        Services.NETWORK.registerClientbound(SessionStatePayload.TYPE, SessionStatePayload.STREAM_CODEC, CartridgeNetwork::session);
        Services.NETWORK.registerClientbound(StateRequestPayload.TYPE, StateRequestPayload.STREAM_CODEC, CartridgeNetwork::session);
        Services.NETWORK.registerClientbound(SessionPongPayload.TYPE, SessionPongPayload.STREAM_CODEC, CartridgeNetwork::session);
        Services.NETWORK.registerClientbound(CoreMismatchPayload.TYPE, CoreMismatchPayload.STREAM_CODEC, CartridgeNetwork::session);

        // Saves (D24, D91-D98; save/, netplay/)
        Services.NETWORK.registerServerbound(SramUploadPayload.TYPE, SramUploadPayload.STREAM_CODEC, NetSessions::onSramUpload);
        Services.NETWORK.registerClientbound(SessionLoadPayload.TYPE, SessionLoadPayload.STREAM_CODEC, CartridgeNetwork::session);
        Services.NETWORK.registerServerbound(SaveExportPayload.TYPE, SaveExportPayload.STREAM_CODEC, SaveServer::onExport);
        Services.NETWORK.registerServerbound(SaveImportPayload.TYPE, SaveImportPayload.STREAM_CODEC, SaveServer::onImport);
        Services.NETWORK.registerClientbound(SaveFilePayload.TYPE, SaveFilePayload.STREAM_CODEC,
                payload -> onSaveFile.accept(payload));
    }

    /** D123: this server's settings to one player (on join). */
    public static void sendSettings(ServerPlayer player) {
        Services.NETWORK.sendToPlayer(player, new ServerSettingsPayload(CartridgeConfig.server()));
    }

    /** D123: the settings changed (the config screen in singleplayer or on a LAN host): tell everyone. */
    public static void broadcastSettings(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(CartridgeNetwork::sendSettings);
    }

    private static void session(CustomPacketPayload payload) {
        onSession.accept(payload);
    }

    private static void leavePlayMode(LeavePlayModePayload payload, ServerPlayer player) {
        if (payload.game().isHandheld()) {
            Handhelds.release(player, payload.game().handheld()); // D164: pauses it
            return;
        }
        if (!player.level().isLoaded(payload.game().console())) return;
        if (player.level().getBlockEntity(payload.game().console()) instanceof ConsoleBlockEntity console) {
            console.release(player.getUUID());
        }
    }
}
