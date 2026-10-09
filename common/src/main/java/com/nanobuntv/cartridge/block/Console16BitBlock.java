package com.nanobuntv.cartridge.block;

import com.mojang.serialization.MapCodec;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeItems;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Super Pixelon (D136, D141, D142), the North American SNES: a boxy grey console about the Pixelon Entertainment
 * System's size, a top-loading cartridge slot in the middle of the top with the cart standing up out of it, the purple
 * POWER slider on the viewer's left of the slot, RESET on its right and the EJECT button in front of it (decoration:
 * the cart comes out through the GUI, D149), and the two ports low on the front at the viewer's left. A Super Pixelon
 * Pad plugs into either port, the Super Pixelon Mouse (D144) into port 1 only (D214: mouse games such as Mario Paint
 * read only port 1). Model: models/console_16bit.bbmodel.
 */
public class Console16BitBlock extends ConsoleBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<Console16BitBlock> CODEC = simpleCodec(Console16BitBlock::new);
    //?}

    private static final ConsoleLayout LAYOUT = new ConsoleLayout(List.of(
            new ConsoleLayout.Zone(ConsolePart.POWER, true, 9.5, 12, 4, 6.5, new float[] {10, 3.5F, 4.75F, 11.5F, 4, 5.75F}),
            new ConsoleLayout.Zone(ConsolePart.RESET, true, 4, 6.5, 4, 6.5, new float[] {4.5F, 3.5F, 4.75F, 6, 4, 5.75F}),
            new ConsoleLayout.Zone(ConsolePart.PORT_1, false, 11.25, 13.25, 0, 2.2, new float[] {11.5F, 0.5F, 2.8F, 13, 1.75F, 3}),
            new ConsoleLayout.Zone(ConsolePart.PORT_2, false, 9.25, 11.25, 0, 2.2, new float[] {9.5F, 0.5F, 2.8F, 11, 1.75F, 3})));

    private static final Map<Direction, VoxelShape> SHAPES = shapesByFacing(Shapes.or(
            box(2, 0, 3, 14, 2, 13),         // lower shell
            box(2.5, 2, 4, 13.5, 3.5, 13),   // upper shell
            box(5, 3.5, 6.5, 11, 4.25, 11.5))); // the slot's housing

    public Console16BitBlock(Properties properties) {
        super(properties);
    }

    //? if >=1.20.3 && <26.3 {
    @Override
    protected MapCodec<Console16BitBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    public ConsoleKind kind() {
        return ConsoleKind.SUPER_PIXELON;
    }

    @Override
    public ConsoleLayout layout() {
        return LAYOUT;
    }

    @Override
    protected Plug plug(ItemStack stack, int port) {
        if (stack.is(CartridgeItems.CONTROLLER_16BIT.get())) return Plug.YES;
        if (stack.is(CartridgeItems.MOUSE_16BIT.get())) return port == 0 ? Plug.YES : Plug.WRONG_PORT;
        if (stack.is(CartridgeItems.CONTROLLER_8BIT.get()) || stack.is(CartridgeItems.LIGHT_GUN.get())
                || stack.is(CartridgeItems.CONTROLLER_VORTEX.get()) || stack.is(CartridgeItems.CONTROLLER_64.get())) {
            return Plug.OTHER_CONSOLE;
        }
        return Plug.NO;
    }

    @Override
    protected Component wrongPort(ItemStack stack) {
        return Component.translatable("cartridge_mod.console.mouse_port_one");
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }
}
