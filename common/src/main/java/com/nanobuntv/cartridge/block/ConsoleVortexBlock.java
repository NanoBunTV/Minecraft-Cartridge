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
 * The Vortex Drive (D151, D153), the Model 1 Sega Genesis: a wide, low glossy black console with a round raised ring
 * round the top-loading cartridge slot (the cart stands up out of it), the POWER slider on top at the front on the
 * viewer's left beside its red light, RESET on the right, a volume slider behind POWER (decoration), and the two
 * ports low on the front at the viewer's left beside the headphone jack. Only the Vortex Pad plugs in (D155).
 * Model: models/console_vortex.bbmodel.
 */
public class ConsoleVortexBlock extends ConsoleBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<ConsoleVortexBlock> CODEC = simpleCodec(ConsoleVortexBlock::new);
    //?}

    private static final ConsoleLayout LAYOUT = new ConsoleLayout(List.of(
            new ConsoleLayout.Zone(ConsolePart.POWER, true, 11, 13.25, 3.5, 5.75, new float[] {11.5F, 2.75F, 4, 12.75F, 3.1F, 5}),
            new ConsoleLayout.Zone(ConsolePart.RESET, true, 2.75, 5, 3.5, 5.75, new float[] {3.25F, 2.75F, 4, 4.5F, 3.1F, 5}),
            new ConsoleLayout.Zone(ConsolePart.PORT_1, false, 10.85, 12.9, 0, 2.2, new float[] {11, 0.5F, 2.8F, 12.75F, 1.75F, 3}),
            new ConsoleLayout.Zone(ConsolePart.PORT_2, false, 8.85, 10.85, 0, 2.2, new float[] {9, 0.5F, 2.8F, 10.75F, 1.75F, 3})));

    private static final Map<Direction, VoxelShape> SHAPES = shapesByFacing(Shapes.or(
            box(1.5, 0, 3, 14.5, 2.25, 13),      // body
            box(2, 2.25, 3.5, 14, 2.75, 12.5),   // top deck
            box(4, 2.75, 4.75, 12, 3.25, 12.25), // the stepped ring round the slot
            box(4.75, 3.25, 5.5, 11.25, 3.75, 11.5),
            box(5, 3.75, 7.5, 11, 4, 9.5)));     // the slot's lips

    public ConsoleVortexBlock(Properties properties) {
        super(properties);
    }

    //? if >=1.20.3 && <26.3 {
    @Override
    protected MapCodec<ConsoleVortexBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    public ConsoleKind kind() {
        return ConsoleKind.VORTEX_DRIVE;
    }

    @Override
    public ConsoleLayout layout() {
        return LAYOUT;
    }

    @Override
    protected Plug plug(ItemStack stack, int port) {
        if (stack.is(CartridgeItems.CONTROLLER_VORTEX.get())) return Plug.YES;
        if (stack.is(CartridgeItems.CONTROLLER_8BIT.get()) || stack.is(CartridgeItems.LIGHT_GUN.get())
                || stack.is(CartridgeItems.CONTROLLER_16BIT.get()) || stack.is(CartridgeItems.MOUSE_16BIT.get())
                || stack.is(CartridgeItems.CONTROLLER_64.get())) {
            return Plug.OTHER_CONSOLE;
        }
        return Plug.NO;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
