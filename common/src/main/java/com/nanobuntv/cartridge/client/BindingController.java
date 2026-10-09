package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.nanobuntv.cartridge.config.ControlSet;
import com.nanobuntv.cartridge.config.GamepadInput;
import dev.isxander.yacl3.api.Controller;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.utils.Dimension;
import dev.isxander.yacl3.gui.AbstractWidget;
import dev.isxander.yacl3.gui.YACLScreen;
import dev.isxander.yacl3.gui.controllers.ControllerWidget;
import java.util.EnumSet;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
//? if >=26.1 {
/*import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
*///?}

/**
 * A config row for one console button (D191): its key and its gamepad input side by side. Click the key's box, then
 * press a key or click a mouse button; click the gamepad's box, then press the input on the pad (D121: inputs held
 * now light up, so the pad can be tried out here). Delete or Backspace clears the box being set. A box can be fixed
 * (Esc to stop playing, the Pixelon 64's left stick): it shows its text and can't be changed.
 */
final class BindingController implements Controller<BindingController.KeyAndPad> {
    /** One button's key (a Minecraft key name) and gamepad input. */
    record KeyAndPad(String key, GamepadInput pad) {}

    /** Each box's width: up to 86, less on a narrow screen so the button's name keeps room. */
    private static final int BOX_MAX = 86, BOX_MIN = 60, NAME_ROOM = 100, BOX_GAP = 4;
    private static final int COLOR_HELD = 0x55FF55;

    private final Option<KeyAndPad> option;
    @Nullable
    private final Component fixedKey, fixedPad;

    /** {@code fixedKey}, {@code fixedPad}: the text of a box that can't be set, or null. */
    BindingController(Option<KeyAndPad> option, @Nullable Component fixedKey, @Nullable Component fixedPad) {
        this.option = option;
        this.fixedKey = fixedKey;
        this.fixedPad = fixedPad;
    }

    @Override
    public Option<KeyAndPad> option() {
        return option;
    }

    @Override
    public Component formatValue() {
        KeyAndPad value = option.pendingValue();
        return keyName(value.key()).copy().append(" · ").append(padName(value.pad()));
    }

    @Override
    public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dimension) {
        return new Widget(this, screen, dimension);
    }

    static Component keyName(String key) {
        InputConstants.Key bound = CartridgeKeys.key(key);
        return bound == InputConstants.UNKNOWN ? Component.translatable("cartridge_mod.controls.none")
                : bound.getDisplayName();
    }

    static Component padName(GamepadInput input) {
        return Component.translatable("cartridge_mod.gamepad." + input.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static final class Widget extends ControllerWidget<BindingController> {
        private enum Listening { NONE, KEY, PAD }

        private Listening listening = Listening.NONE;
        /** Inputs already held when listening started: they count only once released and pressed again. */
        private EnumSet<GamepadInput> ignored = EnumSet.noneOf(GamepadInput.class);

        Widget(BindingController control, YACLScreen screen, Dimension<Integer> dimension) {
            super(control, screen, dimension);
        }

        private int boxWidth() {
            return Math.clamp((getDimension().width() - NAME_ROOM) / 2 - BOX_GAP, BOX_MIN, BOX_MAX);
        }

        private int padBoxX() {
            return getDimension().xLimit() - getXPadding() - boxWidth();
        }

        private int keyBoxX() {
            return padBoxX() - BOX_GAP - boxWidth();
        }

        private boolean inBox(double mouseX, double mouseY, int boxX) {
            return mouseX >= boxX && mouseX < boxX + boxWidth() && mouseY >= getDimension().y()
                    && mouseY < getDimension().yLimit();
        }

        @Override
        protected int getHoveredControlWidth() {
            return boxWidth() * 2 + BOX_GAP;
        }

        @Override
        protected int getUnhoveredControlWidth() {
            return boxWidth() * 2 + BOX_GAP;
        }

        //? if >=26.1 {
        /*@Override
        protected void extractValueText(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            drawBoxes(graphics, mouseX, mouseY);
        }
        *///?} else if >=1.20 {
        @Override
        protected void drawValueText(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
            drawBoxes(graphics, mouseX, mouseY);
        }
        //?} else {
        /*@Override
        protected void drawValueText(com.mojang.blaze3d.vertex.PoseStack pose, int mouseX, int mouseY, float delta) {
            drawBoxes(new GuiGraphics(pose), mouseX, mouseY);
        }
        *///?}

        /** The key and pad boxes, and listening for a pad input. */
        private void drawBoxes(GuiGraphics graphics, int mouseX, int mouseY) {
            KeyAndPad value = control.option().pendingValue();
            EnumSet<GamepadInput> held = HeldInputs.now();
            if (listening == Listening.PAD) {
                ignored.retainAll(held); // released: counts if pressed again
                for (GamepadInput input : held) {
                    if (!ignored.contains(input)) {
                        set(new KeyAndPad(value.key(), input));
                        value = control.option().pendingValue();
                        break;
                    }
                }
            }
            Component keyText = control.fixedKey != null ? control.fixedKey.copy().withStyle(ChatFormatting.GRAY)
                    : listening == Listening.KEY ? press()
                    : CartridgeKeys.key(value.key()) == InputConstants.UNKNOWN ? keyName(value.key()).copy().withStyle(ChatFormatting.GRAY)
                    : keyName(value.key());
            Component padText = control.fixedPad != null ? control.fixedPad.copy().withStyle(ChatFormatting.GRAY)
                    : listening == Listening.PAD ? press()
                    : value.pad() == GamepadInput.NONE ? padName(value.pad()).copy().withStyle(ChatFormatting.GRAY)
                    : held.contains(value.pad()) ? padName(value.pad()).copy().withStyle(style -> style.withColor(COLOR_HELD))
                    : padName(value.pad());
            box(graphics, keyBoxX(), keyText, control.fixedKey == null && inBox(mouseX, mouseY, keyBoxX()));
            box(graphics, padBoxX(), padText, control.fixedPad == null && inBox(mouseX, mouseY, padBoxX()));
        }

        private static Component press() {
            return Component.translatable("cartridge_mod.controls.press").withStyle(ChatFormatting.YELLOW);
        }

        private void box(GuiGraphics graphics, int x, Component text, boolean hovered) {
            int y1 = getDimension().y() + 2, y2 = getDimension().yLimit() - 2;
            graphics.fill(x, y1, x + boxWidth(), y2, hovered ? 0xFFA0A0A0 : 0xFF606060);
            graphics.fill(x + 1, y1 + 1, x + boxWidth() - 1, y2 - 1, 0xFF101010);
            String shown = textRenderer.plainSubstrByWidth(text.getString(), boxWidth() - 6);
            Component line = shown.length() < text.getString().length() ? Component.literal(shown).withStyle(text.getStyle()) : text;
            GuiDraw.centered(graphics, textRenderer, line, x + boxWidth() / 2, getTextY(), 0xFFFFFFFF);
        }

        //? if >=26.1 {
        /*// YACL 26.1 widgets take vanilla's input events.
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            return onClick(event.x(), event.y(), event.button());
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            return onKey(event.key(), event.scancode());
        }
        *///?} else if >=1.21 {
        @Override
        public boolean onMouseClicked(double mouseX, double mouseY, int button) {
            return onClick(mouseX, mouseY, button);
        }

        @Override
        public boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
            return onKey(keyCode, scanCode);
        }
        //?} else {
        /*// Older YACL widgets are plain GuiEventListeners.
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return onClick(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return onKey(keyCode, scanCode);
        }
        *///?}

        private boolean onClick(double mouseX, double mouseY, int button) {
            if (!isAvailable()) return false;
            if (listening == Listening.KEY) {
                set(new KeyAndPad(InputConstants.Type.MOUSE.getOrCreate(button).getName(), control.option().pendingValue().pad()));
                return true;
            }
            if (!isMouseOver(mouseX, mouseY)) return false;
            if (control.fixedKey == null && inBox(mouseX, mouseY, keyBoxX())) {
                listening = Listening.KEY;
            } else if (control.fixedPad == null && inBox(mouseX, mouseY, padBoxX())) {
                listening = Listening.PAD;
                ignored = HeldInputs.now().clone();
            } else {
                return false;
            }
            playDownSound();
            return true;
        }

        private boolean onKey(int keyCode, int scanCode) {
            if (listening == Listening.NONE) return false;
            KeyAndPad value = control.option().pendingValue();
            boolean clear = keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE;
            if (listening == Listening.KEY) {
                set(new KeyAndPad(clear ? ControlSet.NO_KEY : ClientCompat.key(keyCode, scanCode).getName(), value.pad()));
            } else if (clear) {
                set(new KeyAndPad(value.key(), GamepadInput.NONE));
            } else {
                listening = Listening.NONE; // any other key: never mind
            }
            return true;
        }

        private void set(KeyAndPad value) {
            control.option().requestSet(value);
            listening = Listening.NONE;
        }

        @Override
        public void setFocused(boolean focused) {
            super.setFocused(focused);
            if (!focused) listening = Listening.NONE;
        }

        @Override
        public void unfocus() {
            super.unfocus();
            listening = Listening.NONE;
        }
    }

    /** What the gamepad holds now, read at most once a frame however many rows ask. */
    private static final class HeldInputs {
        private static EnumSet<GamepadInput> held = EnumSet.noneOf(GamepadInput.class);
        private static long readAt;

        static EnumSet<GamepadInput> now() {
            long time = System.nanoTime();
            if (time - readAt > 5_000_000L) {
                held = Gamepads.heldInputs();
                readAt = time;
            }
            return held;
        }
    }
}
