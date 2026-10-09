package com.nanobuntv.cartridge.block;

import com.mojang.serialization.MapCodec;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeItems;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Pixelon 64 (D189), the charcoal Nintendo 64: a wide, low console with a rounded top rising to the cartridge slot
 * at the back (the cart stands up out of it), the Expansion Pak door at the front of the top (decoration), the POWER
 * slider on the viewer's left and RESET on the right (D192), in front of the slot, and four controller ports along the front.
 * Only the Pixelon 64 Pad plugs in. Model: models/console_64.bbmodel.
 */
public class Console64Block extends ConsoleBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<Console64Block> CODEC = simpleCodec(Console64Block::new);
    //?}

    private static final ConsoleLayout LAYOUT = new ConsoleLayout(List.of(
            new ConsoleLayout.Zone(ConsolePart.POWER, true, 11, 13.5, 4, 6.5, new float[] {11.5F, 3.25F, 4.5F, 13, 3.6F, 6}),
            new ConsoleLayout.Zone(ConsolePart.RESET, true, 2.5, 5, 4, 6.5, new float[] {3, 3.25F, 4.5F, 4.5F, 3.6F, 6}),
            new ConsoleLayout.Zone(ConsolePart.PORT_1, false, 10, 12, 0, 2.2, new float[] {10.25F, 0.5F, 2.8F, 11.75F, 1.75F, 3}),
            new ConsoleLayout.Zone(ConsolePart.PORT_2, false, 8, 10, 0, 2.2, new float[] {8.25F, 0.5F, 2.8F, 9.75F, 1.75F, 3}),
            new ConsoleLayout.Zone(ConsolePart.PORT_3, false, 6, 8, 0, 2.2, new float[] {6.25F, 0.5F, 2.8F, 7.75F, 1.75F, 3}),
            new ConsoleLayout.Zone(ConsolePart.PORT_4, false, 4, 6, 0, 2.2, new float[] {4.25F, 0.5F, 2.8F, 5.75F, 1.75F, 3})));

    private static final Map<Direction, VoxelShape> SHAPES = shapesByFacing(Shapes.or(
            box(1.5, 0, 3, 14.5, 2.5, 13),      // body
            box(2, 2.5, 3.5, 14, 3.25, 12.5),   // the rounded top
            box(3, 3.25, 6.5, 13, 3.75, 12),    // the rise to the slot
            box(4, 3.75, 8, 12, 4.25, 10.5)));  // the slot's housing

    public Console64Block(Properties properties) {
        super(properties);
    }

    //? if >=1.20.3 && <26.3 {
    @Override
    protected MapCodec<Console64Block> codec() {
        return CODEC;
    }
    //?}

    @Override
    public ConsoleKind kind() {
        return ConsoleKind.PIXELON_64;
    }

    @Override
    public ConsoleLayout layout() {
        return LAYOUT;
    }

    @Override
    protected Plug plug(ItemStack stack, int port) {
        if (stack.is(CartridgeItems.CONTROLLER_64.get())) return Plug.YES;
        if (stack.is(CartridgeItems.CONTROLLER_8BIT.get()) || stack.is(CartridgeItems.LIGHT_GUN.get())
                || stack.is(CartridgeItems.CONTROLLER_16BIT.get()) || stack.is(CartridgeItems.MOUSE_16BIT.get())
                || stack.is(CartridgeItems.CONTROLLER_VORTEX.get())) {
            return Plug.OTHER_CONSOLE;
        }
        return Plug.NO;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
