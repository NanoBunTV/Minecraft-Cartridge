package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
//? if >=1.21.9 {
/*import net.minecraft.client.input.KeyEvent;
*///?} else {
import net.minecraft.client.gui.screens.Screen;
//?}

/** Small client-side differences between Minecraft versions, each in one place (Porting.md; util/Compat on both sides). */
public final class ClientCompat {
    private ClientCompat() {}

    /** Whether Shift is held (1.21.9 moved the check from Screen to Minecraft). */
    public static boolean shiftDown() {
        //? if >=1.21.9 {
        /*return Minecraft.getInstance().hasShiftDown();
        *///?} else {
        return Screen.hasShiftDown();
        //?}
    }

    /** Where the camera is. */
    public static net.minecraft.world.phys.Vec3 cameraPosition() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gameRenderer.mainCamera().position();
        *///?} else if >=1.21.11 {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera().position();
        *///?} else {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        //?}
    }

    /** Where the camera looks, a unit vector. */
    public static org.joml.Vector3fc cameraForward() {
        //? if >=26.2 {
        /*return Minecraft.getInstance().gameRenderer.mainCamera().forwardVector();
        *///?} else if >=1.21.11 {
        /*return Minecraft.getInstance().gameRenderer.getMainCamera().forwardVector();
        *///?} else {
        return Minecraft.getInstance().gameRenderer.getMainCamera().getLookVector();
        //?}
    }

    /** The packed light at a position, as a block entity renderer gets its own. */
    public static int lightAt(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        //? if >=26.2 {
        /*return net.minecraft.util.LightCoordsUtil.getLightCoords(level, pos);
        *///?} else if >=26.1 {
        /*return net.minecraft.client.renderer.LevelRenderer.getLightCoords(level, pos);
        *///?} else {
        return net.minecraft.client.renderer.LevelRenderer.getLightColor(level, pos);
        //?}
    }

    /** Whether a key is a function key (F1...): 26.3's SDL scancodes have them in two runs. */
    public static boolean isFunctionKey(int key) {
        //? if >=26.3 {
        /*return key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12 || key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24;
        *///?} else {
        return key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F25;
        //?}
    }

    /** Hides or shows the cursor over the game window (GLFW until 26.3 moved to SDL). */
    public static void hideCursor(boolean hidden) {
        //? if >=26.3 {
        /*if (hidden) org.lwjgl.sdl.SDLMouse.SDL_HideCursor();
        else org.lwjgl.sdl.SDLMouse.SDL_ShowCursor();
        *///?} else {
        org.lwjgl.glfw.GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                hidden ? org.lwjgl.glfw.GLFW.GLFW_CURSOR_HIDDEN : org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
        //?}
    }

    /** Moves the cursor, in window coordinates. */
    public static void setCursor(double x, double y) {
        //? if >=26.3 {
        /*org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(Minecraft.getInstance().getWindow().handle(), (float) x, (float) y);
        *///?} else {
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(), x, y);
        //?}
    }

    /** Where the cursor is, {x, y} in window coordinates. */
    public static double[] cursor() {
        //? if >=26.3 {
        /*try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            java.nio.FloatBuffer x = stack.mallocFloat(1), y = stack.mallocFloat(1);
            org.lwjgl.sdl.SDLMouse.SDL_GetMouseState(x, y);
            return new double[] {x.get(0), y.get(0)};
        }
        *///?} else {
        double[] x = new double[1], y = new double[1];
        org.lwjgl.glfw.GLFW.glfwGetCursorPos(Minecraft.getInstance().getWindow().getWindow(), x, y);
        return new double[] {x[0], y[0]};
        //?}
    }

    /** A key by its GLFW key code and scancode (1.21.9 wrapped them in a KeyEvent). */
    public static InputConstants.Key key(int key, int scancode) {
        //? if >=1.21.9 {
        /*return InputConstants.getKey(new KeyEvent(key, scancode, 0));
        *///?} else {
        return InputConstants.getKey(key, scancode);
        //?}
    }

    /** Whether a key mapping is bound to this key. */
    public static boolean matches(KeyMapping mapping, int key, int scancode) {
        //? if >=1.21.9 {
        /*return mapping.matches(new KeyEvent(key, scancode, 0));
        *///?} else {
        return mapping.matches(key, scancode);
        //?}
    }

    /** A texture from an image, for the texture manager. */
    public static DynamicTexture texture(String label, NativeImage image) {
        //? if >=1.21.5 {
        /*return new DynamicTexture(() -> label, image);
        *///?} else {
        return new DynamicTexture(image);
        //?}
    }

    /** Sets a pixel from {@code 0xAABBGGRR} (the image's own byte order before 1.21.2 renamed the method). */
    public static void setPixelAbgr(NativeImage image, int x, int y, int abgr) {
        //? if >=1.21.2 {
        /*image.setPixelABGR(x, y, abgr);
        *///?} else {
        image.setPixelRGBA(x, y, abgr);
        //?}
    }

    //? if >=26.1 {
    /*/^* A block tint as 26.1's tint sources: one, for tint index 0. ^/
    public static java.util.List<net.minecraft.client.color.block.BlockTintSource> tintSources(
            com.nanobuntv.cartridge.platform.ClientEvents.BlockTint tint) {
        return java.util.List.of(new net.minecraft.client.color.block.BlockTintSource() {
            @Override
            public int color(net.minecraft.world.level.block.state.BlockState state) {
                return tint.color(state, null, null, 0);
            }

            @Override
            public int colorInWorld(net.minecraft.world.level.block.state.BlockState state,
                    net.minecraft.client.renderer.block.BlockAndTintGetter level, net.minecraft.core.BlockPos pos) {
                return tint.color(state, level, pos, 0);
            }
        });
    }
    *///?}
}
