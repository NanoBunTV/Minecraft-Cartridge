package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.nanobuntv.cartridge.block.ConsoleBlock;
import com.nanobuntv.cartridge.block.DisplayBlock;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.item.PocketronAdapterItem;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Everything drawn around a console that isn't its block model:
 * <ul>
 *   <li>the AV Cable to each linked TV as three sagging cables, yellow, white and red (D18, D103), from the console's
 *   back to the TV's;</li>
 *   <li>each plugged pad (or the Blaster, D99) lying on the floor in front of the console with a cord to its port, or,
 *   once someone plays with it, in their hands with the cord stretched to them (D19, D104; their arms and the hidden
 *   held item are the PlayerRenderer and ItemInHandLayer mixins');</li>
 *   <li>the glow on the button or port under the crosshair (D28, D62): a block's outline can't follow where on the
 *   block the player points, so the tiny clickable parts get a soft glow and their own outline instead;</li>
 *   <li>a top-loader's cartridge, standing up out of its slot (the Super Pixelon's, D142, and the Vortex Drive's,
 *   D153).</li>
 * </ul>
 * Positions are each console's north-facing model's (models/block/console_8bit.json, console_16bit.json,
 * console_vortex.json, console_64.json).
 * Cables reach past the block, so this draws even when the console itself is off screen.
 */
public class ConsoleRenderer extends CartridgeBlockEntityRenderer<ConsoleBlockEntity> {
    private static final float GROW = 0.2F; // px around the part
    /** Composite video and stereo audio (D103): their jacks, in north-model pixels, on the console's back and the TV's. */
    public static final int[] AV_COLORS = {0xE8C020, 0xF0F0F0, 0xC82828};
    private static final float[] AV_X = {9, 8, 7};
    private static final float CONSOLE_JACK_Y = 0.8F, CONSOLE_JACK_Z = 13; // each display has its own (DisplayBlock.jack)
    public static final float CABLE_RADIUS = 0.012F, CORD_RADIUS = 0.009F;
    private static final int CORD_COLOR = 0x2A2A2A, SEGMENTS = 20;
    /**
     * Per console, in north-model px: where a free pad lies, per port (just in front of the console), each port's
     * socket, and for a top-loader its slot.
     */
    private record Look(float[][] floor, float[][] ports, @Nullable Slot slot) {}

    /** A top-loading slot: the cart's middle, the slot's top, how far the cart sinks in, its model's lowest pixel. */
    private record Slot(float x, float z, float top, float sink, float cartBottom) {}

    private static final float CART_SCALE = 0.65F;
    /**
     * The Pixelon's cart, lying flat in its bay with the door open (D188): its middle across, the bay's floor, where its
     * top end sits (north-model px), its scale.
     */
    private static final float FLAT_X = 9.5F, FLAT_FLOOR = 1.5F, FLAT_FRONT = 3.4F, FLAT_SCALE = 0.7F;
    /** The Pocketron Adapter's slot (its socket's top, less how deep the cart sits) and the Game Boy cart in it (D166). */
    private static final float ADAPTER_SLOT = 12.25F, GAME_BOY_CART_BOTTOM = 3, INNER_SCALE = 0.75F;
    private static final Map<ConsoleKind, Look> LOOKS = Map.of(
            ConsoleKind.PIXELON, new Look(new float[][] {{6, 0.3F, -2.5F}, {0.5F, 0.3F, -2.5F}},
                    new float[][] {{4.25F, 0.75F, 2.8F}, {2.75F, 0.75F, 2.8F}}, null),
            ConsoleKind.SUPER_PIXELON, new Look(new float[][] {{13.5F, 0.3F, -2.5F}, {8, 0.3F, -2.5F}},
                    new float[][] {{12.25F, 1.1F, 2.8F}, {10.25F, 1.1F, 2.8F}}, new Slot(8, 9, 4.25F, 2.5F, 3.5F)),
            ConsoleKind.VORTEX_DRIVE, new Look(new float[][] {{13.5F, 0.3F, -2.5F}, {8, 0.3F, -2.5F}},
                    new float[][] {{11.9F, 1.1F, 2.8F}, {9.9F, 1.1F, 2.8F}}, new Slot(8, 8.75F, 4, 2.5F, 2.5F)),
            ConsoleKind.PIXELON_64, new Look(
                    new float[][] {{15, 0.3F, -2.5F}, {10.5F, 0.3F, -2.5F}, {6, 0.3F, -2.5F}, {1.5F, 0.3F, -2.5F}},
                    new float[][] {{11, 1.1F, 2.8F}, {9, 1.1F, 2.8F}, {7, 1.1F, 2.8F}, {5, 1.1F, 2.8F}},
                    new Slot(8, 9.25F, 4.25F, 2.5F, 4)));


    public ConsoleRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected boolean drawsOffScreen(@Nullable ConsoleBlockEntity console) {
        return true; // the cables run to TVs up to 16 blocks away
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    /** NeoForge's culling box (an override there, a plain method on Fabric): the console, its TVs and its players. */
    public AABB getRenderBoundingBox(ConsoleBlockEntity console) {
        AABB box = new AABB(console.getBlockPos()).inflate(9);
        for (BlockPos tv : console.tvs()) box = box.minmax(new AABB(tv));
        return box;
    }

    @Override
    public void draw(ConsoleBlockEntity console, float partialTick, PoseStack pose, Draw buffers, int light,
            int overlay) {
        Level level = console.getLevel();
        if (level == null) return;
        BlockPos pos = console.getBlockPos();
        Direction facing = console.getBlockState().getValue(HorizontalDirectionalBlock.FACING);

        for (BlockPos tv : console.tvs()) {
            BlockState state = level.getBlockState(tv);
            DisplayBlock display = DisplayBlock.of(state);
            if (display == null) continue;
            for (int i = 0; i < AV_COLORS.length; i++) {
                Vec3 from = model(pos, facing, AV_X[i], CONSOLE_JACK_Y, CONSOLE_JACK_Z);
                Vec3 to = display.jack(level, tv, state, i, from);
                cable(buffers, pose, level, pos, from, to, AV_COLORS[i], CABLE_RADIUS);
            }
        }

        Minecraft mc = Minecraft.getInstance();
        Look look = LOOKS.get(console.blockKind());
        float[][] ports = look.ports(), floor = look.floor();
        Slot slot = look.slot();
        if (console.blockKind() == ConsoleKind.PIXELON && !console.cartridge().isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
            pose.translate(-0.5, 0, -0.5);
            // The cart model is 10 x 11 x 2.1 px (z 7-9.1, label up), centred on the origin: lay it on its back, label
            // up, connector end in first; its top end at the front of the bay, its back on the floor.
            pose.translate(FLAT_X / 16, FLAT_FLOOR / 16 + (1 / 16F) * FLAT_SCALE,
                    FLAT_FRONT / 16 + (5.5F / 16) * FLAT_SCALE);
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            pose.scale(FLAT_SCALE, FLAT_SCALE, FLAT_SCALE);
            buffers.item(console.cartridge(), ItemDisplayContext.NONE, light, overlay, pose, level, 0);
            pose.popPose();
        }
        if (slot != null && !console.cartridge().isEmpty()) {
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
            pose.translate(-0.5, 0, -0.5);
            // Item models are drawn centred on the origin (px / 16 - 0.5): bring the cart's bottom edge into the slot.
            pose.translate(slot.x() / 16, (slot.top() - slot.sink()) / 16 + (0.5F - slot.cartBottom() / 16) * CART_SCALE,
                    slot.z() / 16);
            pose.mulPose(Axis.YP.rotationDegrees(180)); // the label faces the front
            pose.scale(CART_SCALE, CART_SCALE, CART_SCALE);
            buffers.item(console.cartridge(), ItemDisplayContext.NONE, light, overlay, pose, level, 0);
            ItemStack inner = PocketronAdapterItem.isAdapter(console.cartridge())
                    ? PocketronAdapterItem.cartridge(console.cartridge()) : ItemStack.EMPTY;
            if (!inner.isEmpty()) { // D166: the Game Boy cart stands in the adapter's own slot, a pixel deep
                pose.translate(0, ADAPTER_SLOT / 16 - 0.5F, 0);
                pose.scale(INNER_SCALE, INNER_SCALE, INNER_SCALE);
                pose.translate(0, 0.5F - GAME_BOY_CART_BOTTOM / 16, 0);
                buffers.item(inner, ItemDisplayContext.NONE, light, overlay, pose, level, 0);
            }
            pose.popPose();
        }
        for (int port = 0; port < console.ports(); port++) {
            ItemStack pad = console.padItem(port);
            if (pad.isEmpty()) continue;
            Vec3 jack = model(pos, facing, ports[port][0], ports[port][1], ports[port][2]);
            UUID holder = console.holder(port);
            Player player = holder == null ? null : level.getPlayerByUUID(holder);
            if (player == null) {
                Vec3 at = model(pos, facing, floor[port][0], floor[port][1], floor[port][2]);
                cable(buffers, pose, level, pos, jack, at, CORD_COLOR, CORD_RADIUS);
                pose.pushPose();
                pose.translate(at.x - pos.getX(), at.y - pos.getY(), at.z - pos.getZ());
                pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
                pose.mulPose(Axis.XP.rotationDegrees(90)); // lying on its back
                pose.scale(0.45F, 0.45F, 0.45F);
                buffers.item(pad, ItemDisplayContext.FIXED, ClientCompat.lightAt(level, BlockPos.containing(at)),
                        OverlayTexture.NO_OVERLAY, pose, level, port);
                pose.popPose();
                continue;
            }
            // In the player's hands (D104), held out in front of their body.
            float yaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
            Vec3 body = player.getPosition(partialTick);
            Vec3 forward = new Vec3(-Mth.sin(yaw * Mth.DEG_TO_RAD), 0, Mth.cos(yaw * Mth.DEG_TO_RAD));
            boolean gun = pad.is(CartridgeItems.LIGHT_GUN.get());
            Vec3 hands = body.add(0, player.isCrouching() ? 0.85 : 1.1, 0).add(forward.scale(gun ? 0.55 : 0.42));
            cable(buffers, pose, level, pos, jack, hands, CORD_COLOR, CORD_RADIUS);
            boolean firstPerson = player == mc.player && mc.options.getCameraType().isFirstPerson();
            if (firstPerson) continue;
            pose.pushPose();
            pose.translate(hands.x - pos.getX(), hands.y - pos.getY(), hands.z - pos.getZ());
            pose.mulPose(Axis.YP.rotationDegrees(180 - yaw));
            if (gun) {
                pose.mulPose(Axis.YP.rotationDegrees(90)); // pointing ahead
            } else {
                pose.mulPose(Axis.YP.rotationDegrees(180)); // its face towards the player, the D-pad at their left
                pose.mulPose(Axis.XP.rotationDegrees(55)); // tilted up towards the face
            }
            pose.scale(0.4F, 0.4F, 0.4F);
            buffers.item(pad, ItemDisplayContext.FIXED, ClientCompat.lightAt(level, BlockPos.containing(hands)),
                    OverlayTexture.NO_OVERLAY, pose, level, port);
            pose.popPose();
        }

        highlight(console, facing, pose, buffers);
    }

    /** A point in a block's north-facing model (pixels), in world coordinates, turned as its blockstate turns it. */
    public static Vec3 model(BlockPos pos, Direction facing, float xPx, float yPx, float zPx) {
        return DisplayBlock.point(pos, facing, xPx, yPx, zPx);
    }

    /**
     * A round-ish cable from {@code a} to {@code b} (world coordinates) that sags in the middle: two crossed ribbons,
     * lit along their length.
     */
    public static void cable(Draw buffers, PoseStack pose, Level level, BlockPos origin, Vec3 a, Vec3 b, int rgb,
            float radius) {
        // Fetched per cable: drawing an item in between may close another render type's buffer.
        VertexConsumer v = buffers.getBuffer(CartridgeRenderTypes.entityCutoutNoCull(ClientConsoles.whiteTexture()));
        Vec3 span = b.subtract(a);
        double length = span.length();
        if (length < 1e-3) return;
        double sag = Math.min(1.0, 0.04 + 0.07 * length);
        Vec3 dir = span.scale(1 / length);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(radius);
        Vec3 up = side.cross(dir).normalize().scale(radius);
        int lightA = ClientCompat.lightAt(level, BlockPos.containing(a));
        int lightB = ClientCompat.lightAt(level, BlockPos.containing(b));
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, bl = rgb & 0xFF;
        PoseStack.Pose last = pose.last();
        Vec3 prev = a;
        for (int i = 1; i <= SEGMENTS; i++) {
            double t = i / (double) SEGMENTS;
            Vec3 p = a.add(span.scale(t)).add(0, -sag * 4 * t * (1 - t), 0);
            float t0 = (i - 1) / (float) SEGMENTS, t1 = (float) t;
            int l0 = lerpLight(lightA, lightB, t0), l1 = lerpLight(lightA, lightB, t1);
            for (Vec3 w : new Vec3[] {side, up}) {
                vertex(v, last, origin, prev.add(w), r, g, bl, l0);
                vertex(v, last, origin, prev.subtract(w), r, g, bl, l0);
                vertex(v, last, origin, p.subtract(w), r, g, bl, l1);
                vertex(v, last, origin, p.add(w), r, g, bl, l1);
            }
            prev = p;
        }
    }

    private static void vertex(VertexConsumer v, PoseStack.Pose pose, BlockPos origin, Vec3 p, int r, int g, int b, int light) {
        v.addVertex(pose.pose(), (float) (p.x - origin.getX()), (float) (p.y - origin.getY()), (float) (p.z - origin.getZ()))
                .setColor(r, g, b, 255).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 1, 0);
    }

    private static int lerpLight(int a, int b, float t) {
        int block = Math.round(Mth.lerp(t, LightTexture.block(a), LightTexture.block(b)));
        int sky = Math.round(Mth.lerp(t, LightTexture.sky(a), LightTexture.sky(b)));
        return LightTexture.pack(block, sky);
    }

    private static void highlight(ConsoleBlockEntity console, Direction facing, PoseStack pose, Draw buffers) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(console.getBlockPos()) || PlayMode.isPlaying(console.getBlockPos())) {
            return;
        }
        if (!(console.getBlockState().getBlock() instanceof ConsoleBlock block)) return;
        float[] box = block.layout().modelBox(block.layout().hit(hit, facing));
        if (box == null) return;

        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        float px = 1 / 16F;
        AABB aabb = new AABB((box[0] - GROW) * px, (box[1] - GROW) * px, (box[2] - GROW) * px,
                (box[3] + GROW) * px, (box[4] + GROW) * px, (box[5] + GROW) * px);

        // A gentle pulse so it reads as "clickable" rather than as part of the texture.
        float pulse = 0.5F + 0.5F * (float) Math.sin(Util.getMillis() / 180.0);
        CartridgeRenderTypes.filledBox(pose, buffers, aabb, 1.0F, 0.95F, 0.6F, 0.25F + 0.2F * pulse);
        CartridgeRenderTypes.outlineBox(pose, buffers, aabb, 1.0F, 1.0F, 1.0F, 0.9F);
        pose.popPose();
    }
}
