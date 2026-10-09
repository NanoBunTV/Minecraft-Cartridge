package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.config.CartridgeConfig;
import com.nanobuntv.cartridge.config.ControlProfile;
import com.nanobuntv.cartridge.config.ControlSet;
import com.nanobuntv.cartridge.emu.N64Pad;
import com.nanobuntv.cartridge.emu.LightGun;
import com.nanobuntv.cartridge.emu.Mouse;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.CartridgeComponents;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.HandheldItem;
import com.nanobuntv.cartridge.item.HandheldLink;
import com.nanobuntv.cartridge.item.HandheldState;
import com.nanobuntv.cartridge.net.LeavePlayModePayload;
import com.nanobuntv.cartridge.netplay.GameKey;
import com.nanobuntv.cartridge.platform.Services;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Play mode (D19, D20, D22): entered when the server hands this player a pad. Keyboard and mouse buttons go to the
 * console instead of the game (the mixins in mixin/client ask {@link #onKey} and {@link #onMouseButton}), so the
 * player can't move or use items; the mouse still turns the head (D66). F toggles the framed big view (D69), Esc
 * leaves. Function keys, chat and the player list keep working. Render thread only.
 * <p>
 * With the Pixelon Blaster in the port (D99) the input is the light gun's: the crosshair's aim at a TV within range
 * (D100, {@link LightGunAim}), or in the big view a mouse cursor over the picture (D106), and the Fire key or either
 * mouse button as the trigger. With the Super Pixelon Mouse (D144) the mouse drives it, but only while the big view is
 * open (D147): the cursor is hidden and kept in the middle, and its movement goes to the game. A gamepad
 * ({@link Gamepads}, D118) adds its buttons.
 * <p>
 * A handheld (D158) is played the same way, while it stays in hand; with a gamepad connected, the keyboard and mouse
 * stay Minecraft's, so the player walks while the pad plays (D168). Its big view keeps the Game Boy's own shape.
 * <p>
 * The keys and gamepad inputs are the console family's own (D191, {@link ControlSet}): the console's block, or the
 * handheld in hand.
 */
public final class PlayMode {
    /** The big view: the picture's share of the window height, border included (D69). */
    private static final float VIEW_HEIGHT = 0.85F;
    private static final int BORDER = 7;
    /** Ticks to wait for the console's sync before trusting "you don't hold this pad any more". */
    private static final int GRACE_TICKS = 20;
    /** A handheld put away ends play at once, after this many ticks from the start (its id can arrive late). */
    private static final int HANDHELD_GRACE_TICKS = 5;

    @Nullable
    private static GameKey console;
    private static int port;
    private static boolean bigView;
    private static int ticks;
    private static int buttons;
    private static final Set<Long> PRESSED = new HashSet<>();
    /** This port holds the light gun (D99), and where it points: picture pixel, or null off every TV. */
    private static boolean gun;
    /** This port holds the Super Pixelon Mouse (D144); the cursor's middle while it drives it, and leftover motion. */
    private static boolean mouse, mouseCaptured;
    /** The controls of what's played (D191); the Pixelon 64's (D189) have an analog stick ({@link N64Pad}). */
    private static ControlSet controls = ControlSet.PIXELON;
    private static double mouseRestX, mouseRestY;
    @Nullable
    private static int[] aim;
    /** The big view's picture on screen, in GUI pixels, as last drawn: x, y, width, height (D106). */
    private static final int[] VIEW = new int[4];

    private PlayMode() {}

    public static void enter(GameKey game, int portIndex) {
        console = game;
        port = portIndex;
        bigView = false;
        ticks = 0;
        PRESSED.clear();
        buttons = 0;
        aim = null;
        gun = holdsGun();
        mouse = holdsMouse();
        controls = controlSet();
        KeyMapping.releaseAll(); // stop walking if a movement key was held
        Gamepads.reset();
    }

    /** Whether this player plays now (the server gave them a pad), for muting Controlify (D118). */
    public static boolean active() {
        return capturing();
    }

    /** Playing a linked pair's game (D177): its picture is both Game Boys side by side. */
    private static boolean linkedView() {
        ConsoleView view = console == null ? null : ClientConsoles.view(console);
        return view != null && view.linkedCarts != null;
    }

    /** Whether this player walks while playing: a handheld, with a gamepad to play it (D168). */
    public static boolean walking() {
        return console != null && console.isHandheld() && Gamepads.connected();
    }

    @Nullable
    private static ConsoleBlockEntity consoleEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (console == null || console.console() == null || mc.level == null) return null;
        return mc.level.getBlockEntity(console.console()) instanceof ConsoleBlockEntity be ? be : null;
    }

    /** The console's family (the Pocketron Adapter plays on the Super Pixelon's), or the handheld's in hand. */
    private static ControlSet controlSet() {
        ConsoleBlockEntity be = consoleEntity();
        if (be != null) return ControlSet.of(be.blockKind());
        Minecraft mc = Minecraft.getInstance();
        if (console != null && console.isHandheld() && mc.player != null) {
            for (InteractionHand hand : InteractionHand.values()) {
                if (mc.player.getItemInHand(hand).getItem() instanceof HandheldItem item) return ControlSet.of(item.kind());
            }
        }
        ConsoleView view = console == null ? null : ClientConsoles.view(console);
        return view != null ? ControlSet.of(view.kind) : controls;
    }

    private static boolean holdsGun() {
        ConsoleBlockEntity be = consoleEntity();
        return be != null && be.padItem(port).is(CartridgeItems.LIGHT_GUN.get());
    }

    private static boolean holdsMouse() {
        ConsoleBlockEntity be = consoleEntity();
        return be != null && be.hasMouse(port);
    }

    /** Still playing: the console lists this player at the port, or the handheld is still in one of their hands. */
    private static boolean stillHolding() {
        Minecraft mc = Minecraft.getInstance();
        if (console.isHandheld()) {
            for (InteractionHand hand : InteractionHand.values()) {
                ItemStack stack = mc.player.getItemInHand(hand);
                HandheldState state = HandheldItem.state(stack);
                HandheldLink link = CartridgeComponents.HANDHELD_LINK.get(stack);
                if (state != null && state.id().equals(console.handheld())) return true;
                if (link != null && link.linkId().equals(console.handheld())) return true; // linked (D177)
            }
            return false;
        }
        ConsoleBlockEntity be = consoleEntity();
        return be != null && mc.player.getUUID().equals(be.holder(port));
    }

    private static void setBigView(boolean on) {
        bigView = on;
        // D106: with the light gun, the big view shows the mouse cursor to aim with. D147: with the mouse, the cursor
        // is let go of (so the head stays still) but hidden and held in the middle.
        Minecraft mc = Minecraft.getInstance();
        boolean capture = on && mouse && mc.screen == null;
        if (!capture) captureMouse(false); // shows the cursor: before grabbing, which hides it again
        if (on && (gun || mouse)) mc.mouseHandler.releaseMouse();
        else if (mc.screen == null) mc.mouseHandler.grabMouse();
        if (capture) captureMouse(true);
    }

    /** Hides the cursor and parks it in the window's middle while the mouse drives the game (D147). */
    private static void captureMouse(boolean on) {
        if (on == mouseCaptured) return;
        mouseCaptured = on;
        var window = Minecraft.getInstance().getWindow();
        ClientCompat.hideCursor(on);
        if (on) {
            ClientCompat.setCursor(window.getScreenWidth() / 2, window.getScreenHeight() / 2);
            mouseRestX = mouseRestY = 0;
        }
    }

    /** Each frame while the mouse drives the game: the cursor's movement since the last, in picture pixels. */
    private static void moveMouse() {
        Minecraft mc = Minecraft.getInstance();
        var window = mc.getWindow();
        // A whole pixel: the OS keeps the cursor on one, so a half-pixel middle (an odd window height) would read as
        // half a pixel's move every frame, and the game's pointer would drift.
        double cx = window.getScreenWidth() / 2, cy = window.getScreenHeight() / 2;
        double[] cursor = ClientCompat.cursor();
        double pictureWidth = VIEW[2] * window.getGuiScale(); // screen pixels
        if (pictureWidth <= 0) return;
        double scale = 256 / pictureWidth;
        mouseRestX += (cursor[0] - cx) * scale;
        mouseRestY += (cursor[1] - cy) * scale;
        int dx = (int) mouseRestX, dy = (int) mouseRestY;
        mouseRestX -= dx;
        mouseRestY -= dy;
        ClientCompat.setCursor(cx, cy);
        ConsoleView view = ClientConsoles.view(console);
        if ((dx != 0 || dy != 0) && view != null && view.instance() != null) view.instance().addMouseMotion(port, dx, dy);
    }

    /** True while the big view covers the middle of the screen (the crosshair is hidden then). */
    public static boolean bigViewShown() {
        return console != null && bigView;
    }

    static boolean isPlaying(GameKey game) {
        return game.equals(console);
    }

    public static boolean isPlaying(BlockPos consolePos) {
        return isPlaying(GameKey.console(consolePos));
    }

    private static boolean capturing() {
        return console != null && Minecraft.getInstance().screen == null;
    }

    static void leave(boolean tellServer) {
        if (console == null) return;
        if (tellServer && Minecraft.getInstance().getConnection() != null) {
            Services.NETWORK.sendToServer(new LeavePlayModePayload(console));
        }
        PRESSED.clear();
        buttons = 0;
        pushInput();
        console = null;
        Minecraft mc = Minecraft.getInstance();
        captureMouse(false); // before grabbing, as in setBigView
        if (bigView && (gun || mouse) && mc.screen == null) mc.mouseHandler.grabMouse();
        bigView = false;
    }

    /** End of each client tick. */
    static void tick() {
        if (console == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            console = null;
            return;
        }
        ticks++;
        // A handheld's id or new boot can reach this client a tick or two after the play message: a short grace.
        if (ticks > (console.isHandheld() ? HANDHELD_GRACE_TICKS : GRACE_TICKS)) {
            if (!stillHolding()) {
                leave(false); // the server let go for us (walked away, unplugged, broken)
                return;
            }
        }
        if (mc.screen != null && !PRESSED.isEmpty()) {
            PRESSED.clear(); // a screen (chat, pause) took the keyboard: let go of every button
        }
        controls = controlSet();
        boolean nowGun = holdsGun(), nowMouse = holdsMouse();
        if (nowGun != gun || nowMouse != mouse || bigView && (gun || mouse) && mc.screen == null && mc.mouseHandler.isMouseGrabbed()
                || mouseCaptured && mc.screen != null) {
            gun = nowGun; // (closing a screen grabs the mouse again: the aiming cursor comes back)
            mouse = nowMouse;
            if (bigView) setBigView(true);
        }
        if (capturing()) {
            Gamepads.poll();
            if (Gamepads.leavePressed()) {
                leave(true);
                return;
            }
            if (Gamepads.bigViewPressed()) setBigView(!bigView);
        }
        recompute();
        pushInput();
    }

    /** From KeyboardHandler.keyPress; true means the game must not see this key. */
    public static boolean onKey(int key, int scancode, int action) {
        if (!capturing()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (key == InputConstants.KEY_ESCAPE) {
            if (action == InputConstants.PRESS) leave(true);
            return true;
        }
        if (walking()) { // D168: the keyboard walks; only the big view's key is the game's
            if (isBigViewKey(key, scancode)) {
                if (action == InputConstants.PRESS) setBigView(!bigView);
                return true;
            }
            return false;
        }
        if (ClientCompat.isFunctionKey(key)) return false;
        if (ClientCompat.matches(mc.options.keyChat, key, scancode) || ClientCompat.matches(mc.options.keyCommand, key, scancode)
                || ClientCompat.matches(mc.options.keyPlayerList, key, scancode)) {
            return false;
        }
        if (isBigViewKey(key, scancode)) {
            if (action == InputConstants.PRESS) setBigView(!bigView);
            return true;
        }
        press(ClientCompat.key(key, scancode), action != InputConstants.RELEASE);
        return true;
    }

    private static boolean isBigViewKey(int key, int scancode) {
        InputConstants.Key bound = CartridgeKeys.bigView();
        return bound != InputConstants.UNKNOWN && bound.equals(ClientCompat.key(key, scancode));
    }

    /** From MouseHandler.onPress; true means the game must not see this click. */
    public static boolean onMouseButton(int button, int action) {
        if (!capturing()) return false;
        if (!walking()) press(InputConstants.Type.MOUSE.getOrCreate(button), action != InputConstants.RELEASE);
        return true;
    }

    /** From MouseHandler.onScroll: no hotbar scrolling while playing. */
    public static boolean onScroll() {
        return capturing();
    }

    private static long id(InputConstants.Key key) {
        return (long) key.getType().ordinal() << 32 | (key.getValue() & 0xFFFFFFFFL);
    }

    private static void press(InputConstants.Key key, boolean down) {
        if (down) PRESSED.add(id(key));
        else PRESSED.remove(id(key));
        recompute();
        pushInput();
    }

    private static void recompute() {
        boolean pad = capturing();
        if (gun) {
            updateAim();
            InputConstants.Key fire = CartridgeKeys.fire();
            // The Fire key, or either main mouse button (whichever a player reaches for, in the world or the big view).
            boolean trigger = fire != InputConstants.UNKNOWN && PRESSED.contains(id(fire))
                    || PRESSED.contains(id(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT)))
                    || PRESSED.contains(id(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT)))
                    || pad && Gamepads.fire();
            buttons = aim != null ? LightGun.encode(aim[0], aim[1], trigger) : LightGun.offscreen(trigger);
            return;
        }
        if (mouse) { // D147: its buttons count while it drives the game, in the big view
            boolean drives = bigView && pad;
            buttons = Mouse.encode(0, 0,
                    drives && PRESSED.contains(id(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_LEFT))),
                    drives && PRESSED.contains(id(InputConstants.Type.MOUSE.getOrCreate(InputConstants.MOUSE_BUTTON_RIGHT))));
            return;
        }
        if (controls == ControlSet.PIXELON_64) {
            buttons = n64Input(pad);
            return;
        }
        ControlProfile profile = CartridgeConfig.client().controls(controls);
        int bits = pad ? Gamepads.buttons(controls, profile) : 0;
        for (ControlSet.Button b : controls.buttons()) {
            if (pressed(profile.key(b.name()))) bits |= b.bits();
        }
        buttons = bits;
    }

    /**
     * The Pixelon 64 Pad (D189, D191): the gamepad's buttons and analog stick (always the left stick), and the keys;
     * the stick's keys tilt it all the way.
     */
    private static int n64Input(boolean pad) {
        ControlProfile profile = CartridgeConfig.client().controls(ControlSet.PIXELON_64);
        int bits = pad ? Gamepads.buttons(ControlSet.PIXELON_64, profile) : 0;
        float[] stick = pad ? Gamepads.leftStick() : new float[2];
        int kx = 0, ky = 0;
        for (ControlSet.Button b : ControlSet.PIXELON_64.buttons()) {
            if (!pressed(profile.key(b.name()))) continue;
            switch (b.name()) {
                case ControlSet.STICK_UP -> ky--;
                case ControlSet.STICK_DOWN -> ky++;
                case ControlSet.STICK_LEFT -> kx--;
                case ControlSet.STICK_RIGHT -> kx++;
                default -> bits |= b.bits();
            }
        }
        if (kx != 0 || ky != 0) {
            float len = (float) Math.sqrt(kx * kx + ky * ky); // diagonals reach the rim, not past it
            stick = new float[] {kx / len, ky / len};
        }
        return N64Pad.encode(bits, stick[0], stick[1]);
    }

    private static boolean pressed(String keyName) {
        InputConstants.Key bound = CartridgeKeys.key(keyName);
        return bound != InputConstants.UNKNOWN && PRESSED.contains(id(bound));
    }

    /** Where the light gun points now: the cursor over the big view (D106), else the crosshair at a TV (D100). */
    private static void updateAim() {
        Minecraft mc = Minecraft.getInstance();
        ConsoleView view = ClientConsoles.view(console);
        if (bigView) {
            double x = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
            double y = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
            double u = (x - VIEW[0]) / VIEW[2], v = (y - VIEW[1]) / VIEW[3];
            aim = VIEW[2] > 0 && u >= 0 && u < 1 && v >= 0 && v < 1
                    ? new int[] {(int) (u * LightGun.WIDTH), (int) (v * LightGun.HEIGHT)} : null;
        } else {
            aim = view == null ? null : LightGunAim.aim(view.tvs, range());
        }
    }

    private static double range() {
        return CartridgeConfig.synced().lightGunRange; // the connected server's (D123)
    }

    private static void pushInput() {
        if (console == null) return;
        ConsoleView view = ClientConsoles.view(console);
        if (view != null && view.instance() != null) view.instance().setInput(port, buttons);
    }

    /** After the HUD: the framed big view (D69) and the controls hint. */
    static void renderHud(GuiGraphics g) {
        if (console == null) return;
        Minecraft mc = Minecraft.getInstance();
        int width = g.guiWidth(), height = g.guiHeight();
        if (bigView) {
            ConsoleView view = ClientConsoles.view(console);
            ResourceLocation picture = view == null ? null : view.picture();
            int outerHeight = Math.round(height * VIEW_HEIGHT);
            int pictureHeight = outerHeight - 2 * BORDER;
            // D65: 4:3 as a TV shows it (3:4 for an arcade game that stands upright, D198); a handheld's own screen is 10:9 (160 x 144), an Advance's 3:2 (240 x 160).
            int pictureWidth = !console.isHandheld() ? (int) Math.round(pictureHeight * (view != null ? view.pictureAspect() : 4.0 / 3.0))
                    : view != null && view.kind == ConsoleKind.POCKETRON_ADVANCE ? pictureHeight * 3 / 2 : pictureHeight * 10 / 9;
            int x = (width - pictureWidth) / 2, y = (height - pictureHeight) / 2;
            VIEW[0] = x;
            VIEW[1] = y;
            VIEW[2] = pictureWidth;
            VIEW[3] = pictureHeight;
            frame(g, x - BORDER, y - BORDER, pictureWidth + 2 * BORDER, pictureHeight + 2 * BORDER);
            g.fill(x, y, x + pictureWidth, y + pictureHeight, 0xFF000000);
            if (picture != null && linkedView()) { // its own Game Boy's half of a linked game (D177)
                GuiDraw.blit(g, picture, x, y, pictureWidth, pictureHeight, port, 0, 1, 1, 2, 1);
            } else if (picture != null) {
                GuiDraw.blit(g, picture, x, y, pictureWidth, pictureHeight, 0, 0, 1, 1, 1, 1);
            }
            if (view != null && view.paused()) {
                GuiDraw.text(g, mc.font, Component.translatable("cartridge_mod.tv.paused"), x + 4, y + 4, 0xFFFFFFFF, true);
            }
            Component message = view == null ? null : view.message();
            if (message != null) {
                int cx = x + pictureWidth / 2, cy = y + pictureHeight / 2;
                if (picture != null) g.fill(x, cy - 16, x + pictureWidth, cy - 2, 0xA0000000);
                GuiDraw.centered(g, mc.font, message, cx, cy - 12, 0xFFFFFFFF);
                float progress = view.state() == ConsoleView.State.LOADING ? view.progress() : -1;
                if (progress >= 0) { // D70
                    int barWidth = pictureWidth / 2;
                    g.fill(cx - barWidth / 2, cy + 2, cx + barWidth / 2, cy + 6, 0xFF404040);
                    g.fill(cx - barWidth / 2, cy + 2, cx - barWidth / 2 + Math.round(barWidth * progress), cy + 6, 0xFFFFFFFF);
                }
            }
        }
        if (gun) { // the aim follows the mouse every frame, not only every tick
            recompute();
            pushInput();
        }
        if (mouseCaptured) moveMouse();
        if (controls == ControlSet.PIXELON_64 && capturing()) { // the stick moves between ticks too (D189)
            Gamepads.refresh();
            recompute();
            pushInput();
        }
        Component hint = gun
                ? Component.translatable("cartridge_mod.play.hint_gun", CartridgeKeys.bigViewName())
                : mouse
                ? Component.translatable("cartridge_mod.play.hint_mouse", CartridgeKeys.bigViewName())
                : walking()
                ? Component.translatable("cartridge_mod.play.hint_walk", mc.player.getMainHandItem().getHoverName(),
                        CartridgeKeys.bigViewName())
                : console.isHandheld()
                ? Component.translatable("cartridge_mod.play.hint_handheld", mc.player.getMainHandItem().getHoverName(),
                        CartridgeKeys.bigViewName())
                : Component.translatable("cartridge_mod.play.hint", port + 1, CartridgeKeys.bigViewName());
        int textWidth = mc.font.width(hint);
        // Top centre: clear of the hotbar, the action bar and the big view's frame.
        int textY = Math.max(2, (height - Math.round(height * VIEW_HEIGHT)) / 4 - 4);
        GuiDraw.text(g, mc.font, hint, (width - textWidth) / 2, textY, 0xFFFFFFFF, true);
    }

    /** A raised panel like vanilla's inventory backgrounds, with a sunken well for the picture. */
    private static void frame(GuiGraphics g, int x, int y, int w, int h) {
        int x2 = x + w, y2 = y + h;
        g.fill(x, y, x2, y2, 0xFF000000);                         // outline
        g.fill(x + 1, y + 1, x2 - 1, y2 - 1, 0xFFC6C6C6);         // body
        g.fill(x + 1, y + 1, x2 - 2, y + 3, 0xFFFFFFFF);          // top highlight
        g.fill(x + 1, y + 1, x + 3, y2 - 2, 0xFFFFFFFF);          // left highlight
        g.fill(x + 2, y2 - 3, x2 - 1, y2 - 1, 0xFF555555);        // bottom shadow
        g.fill(x2 - 3, y + 2, x2 - 1, y2 - 1, 0xFF555555);        // right shadow
        int ix = x + BORDER, iy = y + BORDER, ix2 = x2 - BORDER, iy2 = y2 - BORDER;
        g.fill(ix - 1, iy - 1, ix2, iy, 0xFF373737);              // well: dark top and left
        g.fill(ix - 1, iy - 1, ix, iy2, 0xFF373737);
        g.fill(ix, iy2, ix2 + 1, iy2 + 1, 0xFFFFFFFF);            // light bottom and right
        g.fill(ix2, iy, ix2 + 1, iy2 + 1, 0xFFFFFFFF);
    }
}
