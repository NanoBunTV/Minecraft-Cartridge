package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.Cartridge;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.emu.SharedMemory;
import com.nanobuntv.cartridge.item.CartridgeContents;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.rom.RomFormats;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

/**
 * What this client shows for one console or handheld (D158): the current {@link ConsoleInstance} (if any), the screen
 * texture all its TVs (or its own screen) share, and the boot/reset/cartridge bookkeeping that decides when to start,
 * reset or stop the emulator. Render thread only.
 */
final class ConsoleView {
    enum State { OFF, BLACK, STARTING, LOADING, PLAYING, GLITCH, ERROR }

    private static final long GLITCH_NANOS = 700_000_000L;
    private static final int MAX_FRAME_BYTES = 640 * 480 * 4;
    private static int nextTextureId;

    final GameKey key;
    /** The block entity this view follows (a console's). */
    @Nullable
    ConsoleBlockEntity entity;
    /** Who carries it (a handheld's). */
    @Nullable
    Player carrier;
    /** The console it is, for the next boot (a Super Pixelon is a Super Game Boy with the adapter in, D167). */
    ConsoleKind kind = ConsoleKind.PIXELON;
    /** Core options of this player's own (D165), and a linked game's models (D177). */
    Map<String, String> playerOptions = Map.of();
    /** A linked game's two cartridges (D177), and the second handheld's carrier; null for one game. */
    @Nullable
    List<CartridgeContents> linkedCarts;
    @Nullable
    Player carrier2;
    long lastObservedTick;
    List<BlockPos> tvs = List.of();
    /** The console's own speakers (an arcade cabinet's, D205), or null: sound only from its TVs. */
    @Nullable
    Vec3 speaker;

    private ConsoleInstance instance;
    private UUID cartId;
    private boolean off = true;
    private int seenBoot;

    private ResourceLocation textureId;
    private ScreenTexture texture;
    private ByteBuffer frame, glitch;
    private int frameWidth, frameHeight;

    /** The picture's shape on a TV or the big view (D65): the console's, from the last frame's size (D198). */
    double pictureAspect() {
        return frameWidth > 0 ? kind.pictureAspect(frameWidth, frameHeight) : 4.0 / 3.0;
    }
    private long lastSequence = -1;
    private long glitchEnd, nextGlitchFrame;
    /** When the console was last switched on and off here, for the TV's warm-up and collapse (D31). */
    private long onAt, offAt;

    ConsoleView(GameKey key) {
        this.key = key;
    }

    /** Where it is: the console, or the player carrying the handheld (at chest height). */
    Vec3 where() {
        if (carrier != null && carrier2 != null) { // a linked game: between the two players
            return carrier.position().add(carrier2.position()).scale(0.5).add(0, carrier.getBbHeight() * 0.6, 0);
        }
        if (carrier != null) return carrier.position().add(0, carrier.getBbHeight() * 0.6, 0);
        return key.console() != null ? Vec3.atCenterOf(key.console()) : Vec3.ZERO;
    }

    // --- Lifecycle (driven by ClientConsoles.observe) -------------------------------------------------------------


    /**
     * {@code inRange}: within the spectator radius (D13). Leaving it stops the emulator but tells it to leave the server
     * a savestate if nobody else is watching (D90). RESET and disk swaps arrive as frames of the networked game.
     */
    void observe(boolean powered, boolean inRange, int bootId, CartridgeContents cart, Path gameDir) {
        if (!powered) {
            if (!off) offAt = System.nanoTime(); // the picture collapses to a dot (D31)
            powerOff();
            return;
        }
        if (!inRange) {
            if (instance != null) instance.leaveRange();
            instance = null;
            powerOff();
            return;
        }
        if (off || bootId != seenBoot) {
            seenBoot = bootId;
            boot(cart, gameDir, bootId);
        } else if (running() && (cart == null || !cart.cartId().equals(cartId))) {
            // D30: pulled (or swapped) while running. A real console stays black until RESET or POWER.
            instance.pull();
            instance = null;
            cartId = null;
            glitchEnd = System.nanoTime() + GLITCH_NANOS;
        }
    }

    private void boot(CartridgeContents cart, Path gameDir, int bootId) {
        if (off) onAt = System.nanoTime(); // the tube warms up (D31)
        stopInstance();
        off = false;
        lastSequence = -1;
        glitchEnd = 0;
        if (cart == null) {
            cartId = null; // powered with no cartridge: black screen
            return;
        }
        cartId = cart.cartId();
        UUID me = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : new UUID(0, 0);
        instance = new ConsoleInstance(kind, cart, gameDir, key, bootId, me, playerOptions, linkedCarts);
        instance.start();
    }

    void powerOff() {
        stopInstance();
        off = true;
        glitchEnd = 0;
    }

    /** A handheld put away (D164): stop, sending the server where the game got to. */
    void leave() {
        if (instance != null) instance.leaveRange();
        instance = null;
        powerOff();
    }

    void dispose() {
        powerOff();
        if (textureId != null) Minecraft.getInstance().getTextureManager().release(textureId);
        textureId = null;
        texture = null;
        if (frame != null) MemoryUtil.memFree(frame);
        if (glitch != null) MemoryUtil.memFree(glitch);
        frame = glitch = null;
    }

    private void stopInstance() {
        if (instance != null) instance.stop();
        instance = null;
    }

    private boolean running() {
        return instance != null && instance.status() != ConsoleInstance.Status.ERROR
                && instance.status() != ConsoleInstance.Status.STOPPED;
    }

    ConsoleInstance instance() {
        return instance;
    }

    // --- What the TVs show ----------------------------------------------------------------------------------------

    State state() {
        if (off) return State.OFF;
        if (System.nanoTime() < glitchEnd) return State.GLITCH;
        if (instance == null) return State.BLACK;
        return switch (instance.status()) {
            case STARTING, SYNCING -> State.STARTING;
            case DOWNLOADING_ROM -> State.LOADING;
            case RUNNING -> lastSequence >= 0 ? State.PLAYING : State.STARTING;
            case ERROR -> State.ERROR;
            case STOPPED -> State.BLACK;
        };
    }

    /** The text the TV shows over static or black, if any. */
    Component message() {
        if (instance == null) return null;
        return switch (instance.status()) {
            case DOWNLOADING_ROM -> RomFormats.FDS_BIOS.sha1().equals(instance.downloading())
                    ? Component.translatable("cartridge_mod.tv.loading_bios")
                    : Component.translatable("cartridge_mod.tv.loading", instance.cart().title()); // D70
            case ERROR -> instance.error();
            case RUNNING -> {
                UUID waiting = instance.waitingFor(); // D84
                yield waiting == null ? null : Component.translatable("cartridge_mod.tv.waiting", playerName(waiting));
            }
            default -> null;
        };
    }

    /** Nanoseconds since this client saw the console switched on, or off (Long.MAX_VALUE if it never did). */
    long sinceOn(long now) {
        return onAt == 0 ? Long.MAX_VALUE : now - onAt;
    }

    long sinceOff(long now) {
        return offAt == 0 ? Long.MAX_VALUE : now - offAt;
    }

    /** The last picture shown, whatever the state now (for the power-off collapse), or null. */
    ResourceLocation lastPicture() {
        return texture != null && texture.hasImage() ? textureId : null;
    }

    /** Nobody holds a pad: the frozen picture gets a small "PAUSED" (D88). */
    boolean paused() {
        return instance != null && instance.sessionPaused() && state() == State.PLAYING;
    }

    private static Component playerName(UUID id) {
        var connection = Minecraft.getInstance().getConnection();
        var info = connection != null ? connection.getPlayerInfo(id) : null;
        return info != null ? Component.literal(info.getProfile().getName())
                : Component.translatable("cartridge_mod.console.someone");
    }

    /** The download's progress (0..1) while {@link State#LOADING}, else -1 (D70). */
    float progress() {
        String sha1 = instance == null ? null : instance.downloading();
        return sha1 == null ? -1 : Math.max(0, RomDownloads.progress(sha1));
    }

    /**
     * Brings the texture up to date (a new emulator frame, or the next glitch frame) and returns it, or null if there
     * is no picture yet. Cheap to call from every TV every frame: it only uploads when something changed.
     */
    ResourceLocation picture() {
        State state = state();
        if (state == State.PLAYING || state == State.STARTING) {
            SharedMemory shm = instance == null ? null : instance.sharedMemory();
            if (shm != null && shm.frameSequence() != lastSequence) {
                ensureTexture();
                SharedMemory.Frame f = shm.readFrame(frame);
                if (f != null && f.width() * f.height() * 4 <= MAX_FRAME_BYTES) {
                    frameWidth = f.width();
                    frameHeight = f.height();
                    lastSequence = f.sequence();
                    texture.upload(frame, frameWidth, frameHeight);
                }
            }
        } else if (state == State.GLITCH && frameWidth > 0) {
            long now = System.nanoTime();
            if (now >= nextGlitchFrame) {
                nextGlitchFrame = now + 50_000_000L;
                texture.upload(glitchFrame(), frameWidth, frameHeight);
            }
        }
        return texture != null && texture.hasImage() && (state == State.PLAYING || state == State.GLITCH) ? textureId : null;
    }

    private void ensureTexture() {
        if (texture != null) return;
        texture = new ScreenTexture();
        textureId = Cartridge.id("screen/" + nextTextureId++);
        Minecraft.getInstance().getTextureManager().register(textureId, texture);
        frame = MemoryUtil.memAlloc(MAX_FRAME_BYTES);
        glitch = MemoryUtil.memAlloc(MAX_FRAME_BYTES);
    }

    /** The last frame torn up: rows slid sideways and repeated, bands of garbage colour (D30). */
    private ByteBuffer glitchFrame() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int rowBytes = frameWidth * 4;
        int y = 0;
        while (y < frameHeight) {
            int band = 2 + random.nextInt(24);
            int sourceRow = random.nextInt(frameHeight);
            int shift = (random.nextInt(frameWidth) - frameWidth / 2) / 8 * 8;
            boolean garbage = random.nextInt(6) == 0;
            int colour = random.nextInt() << 8 | 0xFF; // R, G, B, then opaque alpha (big-endian putInt)
            for (int r = y; r < Math.min(frameHeight, y + band); r++) {
                int src = ((sourceRow + r - y) % frameHeight) * rowBytes;
                for (int x = 0; x < frameWidth; x++) {
                    int out = r * rowBytes + x * 4;
                    if (garbage && ((x >> 3) + r) % 3 == 0) {
                        glitch.putInt(out, colour);
                    } else {
                        int sx = Math.floorMod(x + shift, frameWidth);
                        glitch.putInt(out, frame.getInt(src + sx * 4));
                    }
                }
            }
            y += band;
        }
        return glitch;
    }
}
