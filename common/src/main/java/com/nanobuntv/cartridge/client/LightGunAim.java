package com.nanobuntv.cartridge.client;

import com.nanobuntv.cartridge.block.DisplayBlock;
import com.nanobuntv.cartridge.block.Screen;
import com.nanobuntv.cartridge.emu.LightGun;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

/**
 * Where the Pixelon Blaster points (D25, D99, D100): the crosshair's ray against the picture of each display linked
 * to the console (every kind, D134), within range and in plain sight, as a pixel of the game's 256 x 240 picture. The
 * picture's place on the glass matches {@link TvRenderer}: 4:3, centred (D65, D130).
 */
final class LightGunAim {
    private LightGunAim() {}

    /** The picture pixel the crosshair is on, as {x, y}, or null if it's on no display in reach. */
    static int[] aim(List<BlockPos> tvs, double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Vec3 origin = ClientCompat.cameraPosition();
        Vector3fc look = ClientCompat.cameraForward();
        Vec3 dir = new Vec3(look.x(), look.y(), look.z());
        double best = Double.MAX_VALUE;
        int[] result = null;
        for (BlockPos tv : tvs) {
            Screen screen = DisplayBlock.screenAt(mc.level, tv);
            if (screen == null || screen.problem() != null) continue;
            if (mc.player.position().distanceToSqr(screen.center()) > range * range) continue;
            double[] fit = screen.picture();
            Vec3 p0 = screen.origin().add(screen.right().scale(fit[0])).add(screen.down().scale(fit[1])); // picture top-left
            Vec3 pu = screen.right().scale(fit[2]);  // to its right edge
            Vec3 pv = screen.down().scale(fit[3]);   // to its bottom edge
            Vec3 normal = screen.normal();
            double facingDot = normal.dot(dir);
            if (Math.abs(facingDot) < 1e-9) continue;
            double t = normal.dot(p0.subtract(origin)) / facingDot;
            if (t <= 0 || t > range || t >= best) continue;
            if (normal.dot(origin.subtract(p0)) <= 0) continue; // only from in front of the glass
            Vec3 hit = origin.add(dir.scale(t));
            double a = hit.subtract(p0).dot(pu) / pu.lengthSqr(), b = hit.subtract(p0).dot(pv) / pv.lengthSqr();
            if (a < 0 || a >= 1 || b < 0 || b >= 1) continue;
            if (blocked(origin, hit)) continue;
            best = t;
            result = new int[] {(int) (a * LightGun.WIDTH), (int) (b * LightGun.HEIGHT)};
        }
        return result;
    }

    /** A block between the eye and the glass (displays themselves don't count: a wall is many blocks). */
    private static boolean blocked(Vec3 from, Vec3 to) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 stop = from.add(to.subtract(from).scale(0.98));
        BlockHitResult hit = mc.level.clip(new ClipContext(from, stop, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK && DisplayBlock.of(mc.level.getBlockState(hit.getBlockPos())) == null;
    }
}
