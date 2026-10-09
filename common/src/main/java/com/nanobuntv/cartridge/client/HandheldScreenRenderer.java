package com.nanobuntv.cartridge.client;

// From 1.21.4 only: item models are data, and this draws a handheld's screen as a special model (up to 1.21.3,
// ItemRendererMixin calls HandheldRenderer).
//? if >=1.21.4 {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import java.util.function.Consumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/^*
 * The game on a handheld's screen (D158), as the special model {@code cartridge_mod:handheld_screen} that each
 * handheld's model definition ({@code assets/cartridge_mod/items/}) layers over its 3D model.
 ^/
public final class HandheldScreenRenderer implements SpecialModelRenderer<HandheldRenderer.Screen> {
    @Override
    public HandheldRenderer.@Nullable Screen extractArgument(ItemStack stack) {
        return HandheldRenderer.screen(stack);
    }

    // Only the 3D model's layer has this (the model definition shows the flat icon in inventories).
    //? if >=26.1 {
    /^@Override
    public void submit(HandheldRenderer.@Nullable Screen screen, PoseStack pose, SubmitNodeCollector collector, int light,
            int overlay, boolean foil, int outline) {
    ^///?} else {
    @Override
    public void submit(HandheldRenderer.@Nullable Screen screen, ItemDisplayContext context, PoseStack pose,
            SubmitNodeCollector collector, int light, int overlay, boolean foil, int outline) {
    //?}
        if (screen == null) return;
        // The pose is the model's own, 0..1 across the block, as HandheldRenderer draws in.
        collector.submitCustomGeometry(pose, CartridgeRenderTypes.text(screen.picture()),
                (last, consumer) -> HandheldRenderer.quad(last.pose(), consumer, screen));
    }

    @Override
    public void getExtents(Consumer<Vector3fc> output) {
        output.accept(new Vector3f(0, 0, 0));
        output.accept(new Vector3f(1, 1, 1));
    }

    public record Unbaked() implements SpecialModelRenderer.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public SpecialModelRenderer<?> bake(SpecialModelRenderer.BakingContext context) {
            return new HandheldScreenRenderer();
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
*///?}
