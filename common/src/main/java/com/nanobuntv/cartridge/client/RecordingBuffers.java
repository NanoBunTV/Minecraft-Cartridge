package com.nanobuntv.cartridge.client;

// From 1.21.9 block entity renderers submit to a SubmitNodeCollector instead of drawing into a MultiBufferSource.
//? if >=1.21.9 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}

/^*
 * The {@link Draw} of block entity renderers from 1.21.9: it records the geometry they draw (positions already
 * transformed, as a buffer gets them) and hands it to the frame's {@code SubmitNodeCollector} as custom geometry, one
 * batch per render type; items and (from 26.2) text go to the collector directly. Up to 26.1 it is a buffer source
 * too, for {@code Font.drawInBatch}.
 ^/
//? if >=26.2 {
/^public final class RecordingBuffers implements Draw {
^///?} else {
public final class RecordingBuffers implements Draw, MultiBufferSource {
//?}
    final SubmitNodeCollector collector;
    private final Map<RenderType, Recorder> recorders = new LinkedHashMap<>();

    public RecordingBuffers(SubmitNodeCollector collector) {
        this.collector = collector;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return recorders.computeIfAbsent(type, t -> new Recorder());
    }

    @Override
    public void text(Font font, FormattedCharSequence text, float x, float y, int color, boolean shadow, Matrix4f pose,
            Font.DisplayMode mode, int light) {
        //? if >=26.2 {
        /^PoseStack stack = new PoseStack();
        stack.last().pose().set(pose);
        collector.submitText(stack, x, y, text, shadow, mode, light, color, 0, 0);
        ^///?} else {
        font.drawInBatch(text, x, y, color, shadow, pose, this, mode, 0, light);
        //?}
    }

    @Override
    public void item(ItemStack stack, ItemDisplayContext context, int light, int overlay, PoseStack pose, Level level, int seed) {
        ItemStackRenderState state = new ItemStackRenderState();
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(state, stack, context, level, null, seed);
        state.submit(pose, collector, light, overlay, 0);
    }

    /^* Submits everything recorded. ^/
    public void submit() {
        PoseStack identity = new PoseStack(); // the vertices are already where they go
        recorders.forEach((type, recorder) -> {
            if (recorder.count > 0) collector.submitCustomGeometry(identity, type, (pose, consumer) -> recorder.replay(consumer));
        });
    }

    /^* Vertices as a buffer receives them, with whichever elements were set. ^/
    private static final class Recorder implements VertexConsumer {
        private static final int F = 11, I = 5; // floats: x y z u v nx ny nz width u3 v3; ints: color uv1 uv2 set-flags
        private static final int COLOR = 1, UV = 2, UV1 = 4, UV2 = 8, NORMAL = 16, WIDTH = 32, UV3 = 64;
        private float[] floats = new float[F * 64];
        private int[] ints = new int[I * 64];
        private int count;

        private int last() {
            if (count == 0) throw new IllegalStateException("No vertex yet");
            return count - 1;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            if (count * F >= floats.length) {
                floats = Arrays.copyOf(floats, floats.length * 2);
                ints = Arrays.copyOf(ints, ints.length * 2);
            }
            int f = count * F, i = count * I;
            floats[f] = x;
            floats[f + 1] = y;
            floats[f + 2] = z;
            ints[i + 4] = 0;
            count++;
            return this;
        }

        private void set(int flag) {
            ints[last() * I + 4] |= flag;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return setColor(alpha << 24 | red << 16 | green << 8 | blue);
        }

        @Override
        public VertexConsumer setColor(int argb) {
            ints[last() * I] = argb;
            set(COLOR);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            int f = last() * F;
            floats[f + 3] = u;
            floats[f + 4] = v;
            set(UV);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            ints[last() * I + 1] = u & 0xFFFF | v << 16;
            set(UV1);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            ints[last() * I + 2] = u & 0xFFFF | v << 16;
            set(UV2);
            return this;
        }

        //? if >=26.3 {
        /^@Override
        public VertexConsumer setUv3(float u, float v) {
            int f = last() * F;
            floats[f + 9] = u;
            floats[f + 10] = v;
            set(UV3);
            return this;
        }

        ^///?}
        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            int f = last() * F;
            floats[f + 5] = x;
            floats[f + 6] = y;
            floats[f + 7] = z;
            set(NORMAL);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            floats[last() * F + 8] = width;
            set(WIDTH);
            return this;
        }

        void replay(VertexConsumer out) {
            for (int n = 0; n < count; n++) {
                int f = n * F, i = n * I, set = ints[i + 4];
                out.addVertex(floats[f], floats[f + 1], floats[f + 2]);
                if ((set & COLOR) != 0) out.setColor(ints[i]);
                if ((set & UV) != 0) out.setUv(floats[f + 3], floats[f + 4]);
                if ((set & UV1) != 0) out.setUv1(ints[i + 1] & 0xFFFF, ints[i + 1] >>> 16);
                if ((set & UV2) != 0) out.setUv2(ints[i + 2] & 0xFFFF, ints[i + 2] >>> 16);
                if ((set & NORMAL) != 0) out.setNormal(floats[f + 5], floats[f + 6], floats[f + 7]);
                if ((set & WIDTH) != 0) out.setLineWidth(floats[f + 8]);
                //? if >=26.3 {
                /^if ((set & UV3) != 0) out.setUv3(floats[f + 9], floats[f + 10]);
                ^///?}
            }
        }
    }
}
*///?}
