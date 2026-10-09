package com.nanobuntv.cartridge.block;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Where a console's clickable parts are (D28, D62), in pixels of its north-facing model: each {@link Zone} is a
 * rectangle on the front face (x by y) or on the top (x by z), a little larger than the part so it's easy to hit, and
 * the part's own element for the glow that shows it's clickable (client/ConsoleRenderer).
 */
public record ConsoleLayout(List<Zone> zones) {
    /**
     * @param top      on the top face (x by z) rather than the front (x by y)
     * @param modelBox the part's element (x1, y1, z1, x2, y2, z2)
     */
    public record Zone(ConsolePart part, boolean top, double x1, double x2, double from, double to, float[] modelBox) {}

    public ConsolePart hit(BlockHitResult hit, Direction facing) {
        boolean front = hit.getDirection() == facing, top = hit.getDirection() == Direction.UP;
        if (!front && !top) return ConsolePart.BODY;
        BlockPos pos = hit.getBlockPos();
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        // Undo the blockstate's y rotation (DeviceBlock.shapesByFacing) to get north-model x and z.
        double x = switch (facing) {
            case EAST -> local.z;
            case SOUTH -> 1 - local.x;
            case WEST -> 1 - local.z;
            default -> local.x;
        } * 16;
        double z = switch (facing) {
            case EAST -> 1 - local.x;
            case SOUTH -> 1 - local.z;
            case WEST -> local.x;
            default -> local.z;
        } * 16;
        double along = top ? z : local.y * 16;
        for (Zone zone : zones) {
            if (zone.top() == top && x >= zone.x1() && x < zone.x2() && along >= zone.from() && along < zone.to()) {
                return zone.part();
            }
        }
        return ConsolePart.BODY;
    }

    /** The part's element in the north-facing model, or null (the body, or a part this console doesn't have). */
    @Nullable
    public float[] modelBox(ConsolePart part) {
        for (Zone zone : zones) {
            if (zone.part() == part) return zone.modelBox().clone();
        }
        return null;
    }
}
