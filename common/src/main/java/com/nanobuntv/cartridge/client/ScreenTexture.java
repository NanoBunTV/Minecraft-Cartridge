package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.texture.AbstractTexture;
//? if >=1.21.5 {
/*import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
//? if >=26.2 {
/^import com.mojang.blaze3d.GpuFormat;
^///?} else {
import com.mojang.blaze3d.textures.TextureFormat;
//?}
*///?} else {
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.TextureUtil;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
//?}

/**
 * A texture fed straight from RGBA8888 bytes (an emulator frame from shared memory), resized to whatever the frame
 * is. Nearest filtering keeps the pixels sharp. Render thread only. Before 1.21.5 it's a GL texture written directly;
 * since, a GPU device texture written through a command encoder.
 */
final class ScreenTexture extends AbstractTexture {
    private int width, height;

    void upload(ByteBuffer rgba, int width, int height) {
        RenderSystem.assertOnRenderThread();
        if (width <= 0 || height <= 0 || !rgba.isDirect() || rgba.capacity() < width * height * 4) return;
        //? if >=1.21.5 {
        /*if (texture == null || width != this.width || height != this.height) {
            close();
            GpuDevice device = RenderSystem.getDevice();
            //? if >=26.2 {
            /^texture = device.createTexture("Cartridge screen", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            ^///?} else {
            texture = device.createTexture("Cartridge screen", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    TextureFormat.RGBA8, width, height, 1, 1);
            //?}
            textureView = device.createTextureView(texture);
            sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            this.width = width;
            this.height = height;
        }
        //? if >=26.2 {
        /^RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, rgba.slice(0, width * height * 4),
                0, 0, 0, 0, width, height);
        ^///?} else {
        RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, rgba.slice(0, width * height * 4),
                NativeImage.Format.RGBA, 0, 0, 0, 0, width, height);
        //?}
        *///?} else {
        if (width != this.width || height != this.height) {
            TextureUtil.prepareImage(getId(), width, height);
            setFilter(false, false);
            this.width = width;
            this.height = height;
        }
        bind();
        GlStateManager._pixelStore(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
        GlStateManager._texSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE,
                MemoryUtil.memAddress(rgba));
        //?}
    }

    boolean hasImage() {
        return width > 0;
    }

    //? if <1.21.5 {
    @Override
    public void load(ResourceManager resourceManager) {
        // filled by upload()
    }
    //?}
}
