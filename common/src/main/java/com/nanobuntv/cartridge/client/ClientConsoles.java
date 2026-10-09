package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.block.DisplayBlock;
import com.nanobuntv.cartridge.block.Screen;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.item.HandheldItem;
import com.nanobuntv.cartridge.item.HandheldLink;
import com.nanobuntv.cartridge.item.HandheldState;
import com.nanobuntv.cartridge.item.Handhelds;
import com.nanobuntv.cartridge.net.CoreMismatchPayload;
import com.nanobuntv.cartridge.net.SessionInputsPayload;
import com.nanobuntv.cartridge.net.SessionLoadPayload;
import com.nanobuntv.cartridge.net.SessionPongPayload;
import com.nanobuntv.cartridge.net.SessionStatePayload;
import com.nanobuntv.cartridge.net.SessionSyncPayload;
import com.nanobuntv.cartridge.net.StateRequestPayload;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.platform.Services;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * Every console and handheld this client knows about (D158), keyed by their game. Each loaded console block entity
 * reports in every client tick ({@link #observe}); consoles within the spectator radius (D13) of the player, measured
 * to the console or any of its TVs, run their emulator here. Handhelds in the hands of players within the handheld
 * range (D160) run too ({@link #observeHandhelds}). Consoles that stop reporting (unloaded or broken) are shut down; a
 * handheld that's put away sends the server where its game got to (D164). Render thread only.
 */
public final class ClientConsoles {
    private static final Map<GameKey, ConsoleView> VIEWS = new HashMap<>();
    /** Who plays with a pad (or the Blaster) at a console this client knows, as of the last tick (D104). */
    private static final Set<UUID> HOLDERS = new HashSet<>();
    private static final int STATIC_WIDTH = 128, STATIC_HEIGHT = 96;
    private static final ResourceLocation STATIC_ID = Cartridge.id("screen/static");
    private static final ResourceLocation WHITE_ID = Cartridge.id("screen/white");
    /** Two texture rows per line of the game's picture: clear, then dark (the CRT effect, D102). */

    private static long tick;
    private static ClientLevel level;
    private static ScreenTexture staticTexture, whiteTexture;
    private static ByteBuffer staticPixels;
    private static boolean staticWanted;

    private ClientConsoles() {}

    @Nullable
    static ConsoleView view(GameKey game) {
        return VIEWS.get(game);
    }

    @Nullable
    static ConsoleView view(BlockPos console) {
        return VIEWS.get(GameKey.console(console));
    }

    /** Called for each loaded console every client tick (its block entity's client ticker). */
    public static void observe(ConsoleBlockEntity console) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || console.getLevel() != mc.level) return;
        if (level != mc.level) {
            clear();
            level = mc.level;
        }
        ConsoleView view = VIEWS.computeIfAbsent(GameKey.console(console.getBlockPos()), ConsoleView::new);
        if (view.entity != console) {
            // A new block entity at this spot (broken and replaced): its counters start over, so start over too.
            view.powerOff();
            view.entity = console;
        }
        view.lastObservedTick = tick;
        view.tvs = console.tvs();
        view.kind = console.kind();
        view.speaker = console.getBlockState().getBlock() instanceof com.nanobuntv.cartridge.block.ConsoleBlock block
                ? block.speaker(console.getBlockState(), console.getBlockPos()) : null;
        boolean inRange = inRange(mc.player.position(), view);
        view.observe(console.powered(), inRange, console.bootId(), console.contents(), Services.PLATFORM.gameDir());
    }

    /** Each client tick: the handhelds in the hands of players within the handheld range (D160), this one's too. */
    private static void observeHandhelds() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (level != mc.level) {
            clear();
            level = mc.level;
        }
        double range = CartridgeConfig.synced().handheldRange;
        for (Player player : mc.level.players()) {
            if (player.isSpectator() || player.distanceToSqr(mc.player) > range * range) continue;
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = player.getItemInHand(hand);
                HandheldState state = HandheldItem.state(stack);
                if (state == null || !(stack.getItem() instanceof HandheldItem item)) continue;
                HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(stack);
                if (link != null) {
                    if (link.side() == 0) observeLink(mc, player, stack, item, state, link, range);
                    continue;
                }
                ConsoleView view = VIEWS.computeIfAbsent(GameKey.handheld(state.id()), ConsoleView::new);
                if (view.lastObservedTick == tick && view.carrier != player) continue; // the same id twice: first wins
                view.lastObservedTick = tick;
                view.carrier = player;
                view.kind = item.kind();
                view.playerOptions = item.kind() == ConsoleKind.POCKETRON
                        ? Map.of("sameboy_mono_palette", CartridgeConfig.client().pocketronPalette.coreValue) : Map.of();
                view.observe(state.powered(), true, state.bootId(), HandheldItem.game(stack), Services.PLATFORM.gameDir());
            }
        }
    }

    /**
     * A linked pair (D177), seen from its first handheld: it runs only while both are in hand (D181), the second in
     * the hands of a player near enough to see.
     */
    private static void observeLink(Minecraft mc, Player first, ItemStack firstStack, HandheldItem firstItem,
            HandheldState firstState, HandheldLink link, double range) {
        Player second = null;
        ItemStack secondStack = ItemStack.EMPTY;
        for (Player p : mc.level.players()) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack s = p.getItemInHand(hand);
                HandheldState st = HandheldItem.state(s);
                HandheldLink back = CartridgeComponents.HANDHELD_LINK.get(s);
                if (st != null && st.id().equals(link.partner()) && back != null && back.linkId().equals(link.linkId())) {
                    second = p;
                    secondStack = s;
                }
            }
        }
        if (second == null || !(secondStack.getItem() instanceof HandheldItem secondItem)) return;
        HandheldState secondState = HandheldItem.state(secondStack);
        CartridgeContents a = HandheldItem.game(firstStack), b = HandheldItem.game(secondStack);
        ConsoleView view = VIEWS.computeIfAbsent(GameKey.handheld(link.linkId()), ConsoleView::new);
        view.lastObservedTick = tick;
        view.carrier = first;
        view.carrier2 = second;
        view.kind = ConsoleKind.POCKETRON_LINK;
        view.linkedCarts = a == null || b == null ? null : List.of(a, b);
        Map<String, String> options = new HashMap<>();
        String palette = CartridgeConfig.client().pocketronPalette.coreValue;
        HandheldItem[] items = {firstItem, secondItem};
        for (int i = 0; i < 2; i++) {
            boolean color = items[i].kind() == ConsoleKind.POCKETRON_COLOR;
            options.put("sameboy_model_" + (i + 1), color ? "Game Boy Color" : "Game Boy");
            options.put("sameboy_mono_palette_" + (i + 1), palette);
        }
        // Each player hears their own Game Boy; anyone else both (D160).
        options.put("sameboy_audio_output", first == mc.player ? "Game Boy #1" : second == mc.player ? "Game Boy #2" : "Mix Both");
        view.playerOptions = Map.copyOf(options);
        view.observe(firstState.powered() && secondState.powered(), true, link.bootId(),
                Handhelds.linkedGame(a, b), Services.PLATFORM.gameDir());
    }

    /** The linked games this client runs (D177), for their cords (D180). */
    static List<ConsoleView> linkViews() {
        return VIEWS.values().stream().filter(v -> v.linkedCarts != null || v.carrier2 != null).toList();
    }

    /** The view of a handheld's game (its own, or its link's, D177), if this client runs it. */
    @Nullable
    static ConsoleView handheldView(ItemStack stack) {
        HandheldState state = HandheldItem.state(stack);
        if (state == null) return null;
        HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(stack);
        return VIEWS.get(GameKey.handheld(link != null ? link.linkId() : state.id()));
    }

    /** Which half of a linked game's picture a handheld shows: 0 or 1, or -1 for a game of its own (D177). */
    static int linkSide(ItemStack stack) {
        HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(stack);
        return link == null ? -1 : link.side();
    }

    /** A netplay message from the server: to the console's running emulator, if it's for its game. */
    static void deliver(CustomPacketPayload payload) {
        GameKey game;
        int bootId;
        // instanceof rather than a pattern switch: the backports build with Java 17.
        if (payload instanceof SessionInputsPayload p) {
            game = p.game();
            bootId = p.bootId();
        } else if (payload instanceof SessionSyncPayload p) {
            game = p.game();
            bootId = p.bootId();
        } else if (payload instanceof SessionStatePayload p) {
            game = p.game();
            bootId = p.bootId();
        } else if (payload instanceof StateRequestPayload p) {
            game = p.game();
            bootId = p.bootId();
        } else if (payload instanceof SessionPongPayload p) {
            game = p.game();
            bootId = p.bootId();
        } else if (payload instanceof CoreMismatchPayload p) {
            game = p.game();
            bootId = p.bootId();
        } else if (payload instanceof SessionLoadPayload p) {
            game = p.game();
            bootId = p.bootId();
        } else {
            return;
        }
        ConsoleView view = VIEWS.get(game);
        ConsoleInstance instance = view == null ? null : view.instance();
        if (instance != null && instance.bootId() == bootId) instance.deliver(payload);
    }

    private static boolean inRange(Vec3 player, ConsoleView view) {
        double radius = spectatorRadius();
        double r2 = radius * radius;
        if (player.distanceToSqr(view.where()) <= r2) return true;
        for (BlockPos tv : view.tvs) {
            if (player.distanceToSqr(Vec3.atCenterOf(tv)) <= r2) return true;
        }
        return false;
    }

    static double spectatorRadius() {
        return CartridgeConfig.synced().spectatorRadius; // the connected server's (D123)
    }

    /** End of each client tick: retire consoles that stopped reporting, update sound placement and the static. */
    static void endTick() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isPaused()) observeHandhelds();
        HOLDERS.clear();
        for (ConsoleView view : VIEWS.values()) {
            for (int port = 0; port < ConsoleBlockEntity.PORTS; port++) {
                UUID holder = view.entity != null ? view.entity.holder(port) : null;
                if (holder != null) HOLDERS.add(holder);
            }
        }
        boolean paused = mc.isPaused();
        if (!paused) {
            tick++;
            for (Iterator<ConsoleView> it = VIEWS.values().iterator(); it.hasNext(); ) {
                ConsoleView view = it.next();
                if (tick - view.lastObservedTick > 2) {
                    if (view.key.isHandheld()) view.leave(); // put away, or out of range: where it got to (D164)
                    view.dispose();
                    it.remove();
                }
            }
        }
        Vec3 listener = ClientCompat.cameraPosition();
        float volume = mc.options.getSoundSourceVolume(SoundSource.RECORDS);
        boolean unattenuated = CartridgeConfig.client().playModeUnattenuatedAudio;
        staticWanted = false;
        for (ConsoleView view : VIEWS.values()) {
            if (view.state() == ConsoleView.State.STARTING) staticWanted = true;
            ConsoleInstance instance = view.instance();
            if (instance == null) continue;
            instance.setPaused(paused);
            if (view.key.isHandheld()) {
                // A handheld's little speaker, at its holder (D160): quieter, and heard within the handheld range.
                Vec3 at = view.where();
                boolean mine = view.carrier == mc.player;
                instance.audio().place(at.x, at.y, at.z, volume * 0.6F, (float) CartridgeConfig.synced().handheldRange,
                        mine && PlayMode.isPlaying(view.key));
                continue;
            }
            // From the middle of the nearest display's glass (a wall can be 8 blocks wide), or the console's own
            // speakers (an arcade cabinet's, D205).
            Vec3 nearest = view.speaker;
            for (BlockPos tv : view.tvs) {
                Screen screen = DisplayBlock.screenAt(mc.level, tv);
                Vec3 at = screen != null ? screen.center() : Vec3.atCenterOf(tv);
                if (nearest == null || listener.distanceToSqr(at) < listener.distanceToSqr(nearest)) nearest = at;
            }
            // No TV, no sound. Players in play mode may hear it at full volume (D63).
            Vec3 at = nearest != null ? nearest : view.where();
            instance.audio().place(at.x, at.y, at.z, nearest != null ? volume : 0, (float) spectatorRadius(),
                    unattenuated && PlayMode.isPlaying(view.key));
        }
    }

    /** The animated "no signal" static (D31), shared by every TV showing it. Render thread. */
    static ResourceLocation staticTexture() {
        if (staticTexture == null) {
            staticTexture = new ScreenTexture();
            Minecraft.getInstance().getTextureManager().register(STATIC_ID, staticTexture);
            staticPixels = MemoryUtil.memAlloc(STATIC_WIDTH * STATIC_HEIGHT * 4);
        }
        if (staticWanted || !staticTexture.hasImage()) {
            staticWanted = false; // at most one new image per tick
            ThreadLocalRandom random = ThreadLocalRandom.current();
            for (int i = 0; i < STATIC_WIDTH * STATIC_HEIGHT; i++) {
                int v = random.nextInt(256);
                staticPixels.putInt(i * 4, v << 24 | v << 16 | v << 8 | 0xFF);
            }
            staticTexture.upload(staticPixels, STATIC_WIDTH, STATIC_HEIGHT);
        }
        return STATIC_ID;
    }

    /** A plain white texture, for solid shapes drawn with the TV's text render type (the progress bar, D70). */
    public static ResourceLocation whiteTexture() {
        if (whiteTexture == null) {
            whiteTexture = new ScreenTexture();
            Minecraft.getInstance().getTextureManager().register(WHITE_ID, whiteTexture);
            ByteBuffer white = MemoryUtil.memAlloc(4 * 4 * 4);
            for (int i = 0; i < 16; i++) white.putInt(i * 4, 0xFFFFFFFF);
            whiteTexture.upload(white, 4, 4);
            MemoryUtil.memFree(white);
        }
        return WHITE_ID;
    }

    /** Whether this player plays at a console nearby: they hold its pad in both hands (D104, the player mixins). */
    public static boolean holdsPad(UUID player) {
        return HOLDERS.contains(player);
    }

    /** Leaving the world: stop everything. */
    static void clear() {
        VIEWS.values().forEach(ConsoleView::dispose);
        VIEWS.clear();
        HOLDERS.clear();
        level = null;
    }
}
