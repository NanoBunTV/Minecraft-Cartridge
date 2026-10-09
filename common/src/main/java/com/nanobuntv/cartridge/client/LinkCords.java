package com.nanobuntv.cartridge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The link cable between two linked Pocketrons (D180): a thin grey cord from one player's hands to the other's,
 * sagging like the AV Cable, while their linked game runs here (both in hand, D181). Render thread.
 */
final class LinkCords {
    private static final int GREY = 0x6E6E76;
    private static final float RADIUS = 0.01F;

    private LinkCords() {}

    static void render(PoseStack pose, Draw buffers, Vec3 camera, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos origin = BlockPos.containing(camera);
        pose.pushPose();
        pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        for (ConsoleView view : ClientConsoles.linkViews()) {
            if (view.carrier == null || view.carrier2 == null) continue;
            ConsoleRenderer.cable(buffers, pose, mc.level, origin, hands(view.carrier, partialTick),
                    hands(view.carrier2, partialTick), GREY, RADIUS);
        }
        pose.popPose();
    }

    /** Where a player holds a handheld: in front of their chest, a little to the right. */
    private static Vec3 hands(Player player, float partialTick) {
        float yaw = (float) Math.toRadians(Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        return player.getPosition(partialTick).add(0, player.getBbHeight() * 0.55, 0).add(forward.scale(0.35))
                .add(right.scale(0.15));
    }
}
