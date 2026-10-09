package com.nanobuntv.cartridge.item;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.net.EnterPlayModePayload;
import com.nanobuntv.cartridge.netplay.GameHost;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.netplay.NetSessions;
import com.nanobuntv.cartridge.platform.Services;
import com.nanobuntv.cartridge.util.Compat;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The handhelds in players' hands on this server (D158), who plays which (D164), and the link cables between them
 * (D176-D181). A handheld's game runs while it's in a hand and switched on; its holder plays it after right-clicking
 * it, until Esc or putting it away, which pauses it. Two linked handhelds run one game, keyed by the link's id, while
 * both are in hand. Server thread only; rebuilt every tick.
 */
public final class Handhelds {
    private record Carried(ServerPlayer player, InteractionHand hand) {
        ItemStack stack() {
            return player.getItemInHand(hand);
        }
    }

    /** A link request waiting for the other player (D176), for a minute. */
    private record Request(UUID from, UUID fromHandheld, UUID to, UUID toHandheld, long until) {}

    private static final long REQUEST_NANOS = 60_000_000_000L;
    private static final Map<UUID, Carried> CARRIED = new HashMap<>();
    /** Handheld id to the player playing it. */
    private static final Map<UUID, UUID> PLAYING = new HashMap<>();
    /** Link id to its two handhelds, by side, while both are in hand (D181). */
    private static final Map<UUID, Carried[]> LINKS = new HashMap<>();
    private static final Map<Integer, Request> REQUESTS = new HashMap<>();
    private static int nextRequest;
    @Nullable
    private static MinecraftServer server;

    private Handhelds() {}

    public static void init() {
        Services.PLATFORM.onServerTick(Handhelds::tick);
        Services.PLATFORM.onServerStopped(Handhelds::clear);
        // The chat's Accept / Decline on a link request (D176).
        Services.PLATFORM.onRegisterCommands(dispatcher -> dispatcher.register(Commands.literal("cartridge_link")
                .then(Commands.literal("accept").then(Commands.argument("request", IntegerArgumentType.integer())
                        .executes(c -> answer(c.getSource(), IntegerArgumentType.getInteger(c, "request"), true))))
                .then(Commands.literal("decline").then(Commands.argument("request", IntegerArgumentType.integer())
                        .executes(c -> answer(c.getSource(), IntegerArgumentType.getInteger(c, "request"), false))))));
    }

    private static int answer(CommandSourceStack source, int request, boolean accept) throws CommandSyntaxException {
        answer(source.getPlayerOrException(), request, accept);
        return 1;
    }

    private static void clear() {
        CARRIED.clear();
        PLAYING.clear();
        LINKS.clear();
        REQUESTS.clear();
        server = null;
    }

    private static void tick(MinecraftServer s) {
        if (s != server) {
            clear();
            server = s;
        }
        refresh(s);
        // Put away, or in someone else's hands now: nobody plays it (D164).
        PLAYING.entrySet().removeIf(e -> {
            Carried c = CARRIED.get(e.getKey());
            return c == null || !c.player().getUUID().equals(e.getValue());
        });
        long now = System.nanoTime();
        REQUESTS.values().removeIf(r -> now > r.until());
        NetSessions.tickHandhelds(s);
    }

    /** Who carries which handheld, and the links with both in hand. */
    private static void refresh(MinecraftServer s) {
        CARRIED.clear();
        for (ServerPlayer player : s.getPlayerList().getPlayers()) {
            if (player.isSpectator()) continue;
            for (InteractionHand hand : InteractionHand.values()) {
                HandheldState state = CartridgeComponents.HANDHELD.get(player.getItemInHand(hand));
                if (state != null) CARRIED.putIfAbsent(state.id(), new Carried(player, hand));
            }
        }
        // Links whose two handhelds are both in hand; too far apart, the cable comes out (D178).
        LINKS.clear();
        double range = CartridgeConfig.server().linkCableRange;
        for (Map.Entry<UUID, Carried> e : List.copyOf(CARRIED.entrySet())) {
            HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(e.getValue().stack());
            if (link == null || link.side() != 0) continue;
            Carried other = CARRIED.get(link.partner());
            HandheldLink back = other == null ? null : CartridgeComponents.HANDHELD_LINK.get(other.stack());
            if (back == null || !back.linkId().equals(link.linkId())) continue;
            ServerPlayer a = e.getValue().player(), b = other.player();
            if (a != b && (a.level() != b.level() || a.distanceTo(b) > range)) {
                unlink(s, link, Component.translatable("cartridge_mod.link.too_far"));
                continue;
            }
            LINKS.put(link.linkId(), new Carried[] {e.getValue(), other});
        }
    }

    /** The handheld with this id, or the link with this id while both its handhelds are in hand. */
    @Nullable
    public static GameHost host(MinecraftServer s, UUID id) {
        Carried[] link = LINKS.get(id);
        if (link != null) return new LinkHost(id, link[0], link[1]);
        Carried c = CARRIED.get(id);
        if (c == null || c.player().isRemoved()) return null;
        HandheldState state = CartridgeComponents.HANDHELD.get(c.stack());
        return state != null && state.id().equals(id) ? new Host(c.player(), c.hand(), id) : null;
    }

    /** The holder starts (or goes on) playing it: play mode on their client (D164); a linked one on its side (D177). */
    static void play(ServerPlayer player, InteractionHand hand, UUID id) {
        CARRIED.put(id, new Carried(player, hand));
        HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(player.getItemInHand(hand));
        if (link != null) {
            if (!LINKS.containsKey(link.linkId())) {
                Compat.overlay(player, Component.translatable("cartridge_mod.link.partner_away", link.partnerName()));
                return;
            }
            PLAYING.put(id, player.getUUID());
            NetSessions.changed(host(Compat.server(player), link.linkId()));
            Services.NETWORK.sendToPlayer(player, new EnterPlayModePayload(GameKey.handheld(link.linkId()), link.side()));
            return;
        }
        PLAYING.put(id, player.getUUID());
        NetSessions.changed(new Host(player, hand, id));
        Services.NETWORK.sendToPlayer(player, new EnterPlayModePayload(GameKey.handheld(id), 0));
    }

    /** The player left play mode (Esc): the game pauses (D164). {@code id}: the handheld's, or its link's. */
    public static void release(ServerPlayer player, UUID id) {
        boolean any = PLAYING.entrySet().removeIf(e -> e.getValue().equals(player.getUUID()) && (e.getKey().equals(id)
                || linkOf(e.getKey()) != null && linkOf(e.getKey()).linkId().equals(id)));
        GameHost host = host(Compat.server(player), id);
        if (any && host != null) NetSessions.changed(host);
    }

    @Nullable
    private static HandheldLink linkOf(UUID handheld) {
        Carried c = CARRIED.get(handheld);
        return c == null ? null : CartridgeComponents.HANDHELD_LINK.get(c.stack());
    }

    // --- Link cables (D176-D181) -----------------------------------------------------------------------------------------

    /** The handheld in a player's hands, the main hand first. */
    @Nullable
    private static ItemStack held(ServerPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            if (HandheldItem.isHandheld(player.getItemInHand(hand))) return player.getItemInHand(hand);
        }
        return null;
    }

    /** "Link with..." in the GUI: asks the other player (D176). Needs a Link Cable in the asker's GUI (D179). */
    public static void request(ServerPlayer player, UUID target) {
        ItemStack mine = held(player);
        ServerPlayer other = Compat.server(player).getPlayerList().getPlayer(target);
        ItemStack theirs = other == null ? null : held(other);
        Component problem = null;
        if (mine == null || HandheldItem.state(mine) == null) problem = Component.translatable("cartridge_mod.link.hold_yours");
        else if (!HandheldItem.slot(mine, HandheldItem.LINK_SLOT).is(CartridgeItems.LINK_CABLE.get())) {
            problem = Component.translatable("cartridge_mod.link.no_cable");
        } else if (CartridgeComponents.HANDHELD_LINK.has(mine)) problem = Component.translatable("cartridge_mod.link.already");
        else if (other == null || other == player || theirs == null || HandheldItem.state(theirs) == null
                || !HandheldItem.isLinkable(theirs)) { // the Advances don't link (D184)
            problem = Component.translatable("cartridge_mod.link.not_holding");
        } else if (CartridgeComponents.HANDHELD_LINK.has(theirs)) {
            problem = Component.translatable("cartridge_mod.link.they_are_linked", other.getName());
        } else if (other.level() != player.level() || other.distanceTo(player) > CartridgeConfig.server().linkCableRange) {
            problem = Component.translatable("cartridge_mod.link.too_far_to_ask", other.getName());
        }
        if (problem != null) {
            Compat.overlay(player, problem);
            return;
        }
        int id = ++nextRequest;
        REQUESTS.put(id, new Request(player.getUUID(), HandheldItem.state(mine).id(), other.getUUID(),
                HandheldItem.state(theirs).id(), System.nanoTime() + REQUEST_NANOS));
        Component accept = Component.translatable("cartridge_mod.link.accept").withStyle(Style.EMPTY
                .withColor(ChatFormatting.GREEN).withBold(true)
                .withClickEvent(Compat.runCommand("/cartridge_link accept " + id))
                .withHoverEvent(Compat.showText(Component.translatable("cartridge_mod.link.accept.hover"))));
        Component decline = Component.translatable("cartridge_mod.link.decline").withStyle(Style.EMPTY
                .withColor(ChatFormatting.RED).withBold(true)
                .withClickEvent(Compat.runCommand("/cartridge_link decline " + id)));
        other.sendSystemMessage(Component.translatable("cartridge_mod.link.asked", player.getName(), accept, decline));
        Compat.overlay(player, Component.translatable("cartridge_mod.link.asking", other.getName()));
    }

    /** The asked player's answer (the chat's Accept / Decline, through the {@code cartridge_link} command). */
    public static void answer(ServerPlayer player, int requestId, boolean accept) {
        Request r = REQUESTS.remove(requestId);
        if (r == null || !r.to().equals(player.getUUID())) {
            player.sendSystemMessage(Component.translatable("cartridge_mod.link.expired"));
            return;
        }
        ServerPlayer asker = Compat.server(player).getPlayerList().getPlayer(r.from());
        if (!accept) {
            if (asker != null) asker.sendSystemMessage(Component.translatable("cartridge_mod.link.declined", player.getName()));
            return;
        }
        ItemStack mine = held(player), theirs = asker == null ? null : held(asker);
        if (mine == null || theirs == null || !idOf(mine).equals(r.toHandheld()) || !idOf(theirs).equals(r.fromHandheld())
                || CartridgeComponents.HANDHELD_LINK.has(mine) || CartridgeComponents.HANDHELD_LINK.has(theirs)
                || asker.level() != player.level() || asker.distanceTo(player) > CartridgeConfig.server().linkCableRange) {
            player.sendSystemMessage(Component.translatable("cartridge_mod.link.expired"));
            return;
        }
        link(asker, theirs, player, mine);
    }

    private static UUID idOf(ItemStack handheld) {
        HandheldState state = HandheldItem.state(handheld);
        return state != null ? state.id() : new UUID(0, 0);
    }

    /**
     * Plugs the cable in (D177): both restart from power-on as one linked game; their own moments are forgotten. Also
     * the dev self-test's way in, with one player's two hands.
     */
    public static void link(ServerPlayer a, ItemStack first, ServerPlayer b, ItemStack second) {
        UUID linkId = UUID.randomUUID();
        int boot = newBoot(a);
        CartridgeComponents.HANDHELD_LINK.set(first, new HandheldLink(linkId, 0, idOf(second),
                b.getGameProfile().getName(), boot));
        CartridgeComponents.HANDHELD_LINK.set(second, new HandheldLink(linkId, 1, idOf(first),
                a.getGameProfile().getName(), boot));
        for (ItemStack stack : List.of(first, second)) {
            HandheldState state = HandheldItem.state(stack);
            CartridgeComponents.HANDHELD.set(stack, state.withPower(true, state.bootId()));
            PLAYING.remove(state.id());
            NetSessions.endGame(Compat.server(a), GameKey.handheld(state.id()));
        }
        refresh(Compat.server(a));
        Component done = Component.translatable("cartridge_mod.link.linked");
        Compat.overlay(a, done);
        Compat.overlay(b, done);
    }

    /**
     * Pulls the cable out (D177, D181): the GUI's Unlink, switching one off, a pulled cart, or walking too far apart.
     * Each handheld restarts on its own. The other handheld is found in its player's inventory if they're online;
     * otherwise its link is cleared when it next turns up ({@link HandheldItem#inventoryTick}).
     */
    public static void unlink(MinecraftServer s, HandheldLink link, Component why) {
        NetSessions.endGame(s, GameKey.handheld(link.linkId()));
        for (ServerPlayer player : s.getPlayerList().getPlayers()) {
            for (ItemStack stack : Compat.mainItems(player.getInventory())) clearLink(player, stack, link.linkId(), why);
            for (ItemStack stack : List.of(player.getOffhandItem())) clearLink(player, stack, link.linkId(), why);
        }
        DEAD_LINKS.add(link.linkId());
    }

    /** Links pulled out while one handheld was away: cleared when that one turns up. */
    static final java.util.Set<UUID> DEAD_LINKS = new java.util.HashSet<>();

    static void clearLink(ServerPlayer player, ItemStack stack, UUID linkId, @Nullable Component why) {
        HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(stack);
        if (link == null || !link.linkId().equals(linkId)) return;
        CartridgeComponents.HANDHELD_LINK.remove(stack);
        HandheldState state = HandheldItem.state(stack);
        if (state != null) {
            CartridgeComponents.HANDHELD.set(stack, state.withPower(state.powered(), newBoot(player))); // on its own
            PLAYING.remove(state.id());
        }
        if (why != null) Compat.overlay(player, why);
    }

    private static int newBoot(ServerPlayer player) {
        int boot;
        do {
            boot = player.getRandom().nextInt();
        } while (boot == 0);
        return boot;
    }

    /** A handheld in a player's hand, as netplay sees it (D158). */
    record Host(ServerPlayer player, InteractionHand hand, UUID id) implements GameHost {
        ItemStack stack() {
            return player.getItemInHand(hand);
        }

        @Nullable
        private HandheldState state() {
            HandheldState state = CartridgeComponents.HANDHELD.get(stack());
            return state != null && state.id().equals(id) ? state : null;
        }

        @Override
        public GameKey gameKey() {
            return GameKey.handheld(id);
        }

        @Override
        public boolean powered() {
            HandheldState state = state();
            return state != null && state.powered();
        }

        @Override
        public int bootId() {
            HandheldState state = state();
            return state != null ? state.bootId() : 0;
        }

        @Override
        public void reboot() {
            HandheldItem.setPower(player, stack(), true);
        }

        @Override
        public ServerLevel serverLevel() {
            return Compat.level(player);
        }

        @Override
        public Vec3 where() {
            return player.position();
        }

        /** Watchers leave at the handheld range (D160); past this they've gone without saying. */
        @Override
        public double reach() {
            return CartridgeConfig.server().handheldRange + 32.0;
        }

        @Override
        @Nullable
        public UUID holder(int port) {
            return port == 0 && player.getUUID().equals(PLAYING.get(id)) ? player.getUUID() : null;
        }

        @Override
        public int deviceWord() {
            return 0;
        }

        @Override
        @Nullable
        public CartridgeContents contents() {
            return HandheldItem.game(stack());
        }

        @Override
        public ItemStack memoryCard() {
            return HandheldItem.slot(stack(), HandheldItem.CARD_SLOT);
        }

        @Override
        public void setMemoryCard(ItemStack card) {
            HandheldItem.setSlot(stack(), HandheldItem.CARD_SLOT, card);
        }
    }

    /** Two linked handhelds' game (D177): side 0 is port 1, side 1 port 2. No Memory Cards while linked. */
    record LinkHost(UUID linkId, Carried first, Carried second) implements GameHost {
        private Carried side(int port) {
            return port == 0 ? first : second;
        }

        @Nullable
        private HandheldLink link() {
            return CartridgeComponents.HANDHELD_LINK.get(first.stack());
        }

        @Override
        public GameKey gameKey() {
            return GameKey.handheld(linkId);
        }

        @Override
        public boolean powered() {
            for (Carried c : new Carried[] {first, second}) {
                HandheldState state = HandheldItem.state(c.stack());
                if (state == null || !state.powered()) return false;
            }
            return true;
        }

        @Override
        public int bootId() {
            HandheldLink link = link();
            return link != null ? link.bootId() : 0;
        }

        @Override
        public void reboot() {
            int boot = newBoot(first.player());
            for (Carried c : new Carried[] {first, second}) {
                HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(c.stack());
                if (link != null) CartridgeComponents.HANDHELD_LINK.set(c.stack(), link.withBoot(boot));
            }
            NetSessions.forget(Compat.level(first.player()), gameKey());
            NetSessions.changed(this);
        }

        @Override
        public ServerLevel serverLevel() {
            return Compat.level(first.player());
        }

        @Override
        public Vec3 where() {
            return first.player().position();
        }

        @Override
        public double reach() {
            return CartridgeConfig.server().linkCableRange + CartridgeConfig.server().handheldRange + 32.0;
        }

        @Override
        @Nullable
        public UUID holder(int port) {
            if (port > 1) return null;
            Carried c = side(port);
            HandheldState state = HandheldItem.state(c.stack());
            return state != null && c.player().getUUID().equals(PLAYING.get(state.id())) ? c.player().getUUID() : null;
        }

        @Override
        public int deviceWord() {
            return 0;
        }

        @Override
        @Nullable
        public CartridgeContents contents() {
            return linkedGame(HandheldItem.game(first.stack()), HandheldItem.game(second.stack()));
        }

        @Override
        public List<UUID> saveCarts() {
            CartridgeContents a = HandheldItem.game(first.stack()), b = HandheldItem.game(second.stack());
            return a == null || b == null ? List.of() : List.of(a.cartId(), b.cartId());
        }

        @Override
        public ItemStack memoryCard() {
            return ItemStack.EMPTY;
        }

        @Override
        public void setMemoryCard(ItemStack card) {}
    }

    /**
     * A linked game's own description (D177), the same on the server and every client: the first game's ROM and both
     * titles, and an id made from both carts (a cart swapped on either side is another game). Null without both.
     */
    @Nullable
    public static CartridgeContents linkedGame(@Nullable CartridgeContents a, @Nullable CartridgeContents b) {
        if (a == null || b == null) return null;
        ByteBuffer ids = ByteBuffer.allocate(32);
        ids.putLong(a.cartId().getMostSignificantBits()).putLong(a.cartId().getLeastSignificantBits());
        ids.putLong(b.cartId().getMostSignificantBits()).putLong(b.cartId().getLeastSignificantBits());
        return new CartridgeContents(CartridgeContents.GAME_BOY_LINK, a.romSha1(), a.title() + " / " + b.title(),
                UUID.nameUUIDFromBytes(ids.array()));
    }
}
