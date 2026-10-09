package com.nanobuntv.cartridge.block;

import com.mojang.serialization.MapCodec;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Pixelon Entertainment System (D11, D41, D141): a small box centred in its block, about 12 x 4 x 10 px (D42).
 * A Pixelon Pad plugs into either port, the Pixelon Blaster into port 2 only (D99). The parts are on the front, low
 * down (models/block/console_8bit.json: power_button x 12-13.25, reset_button x 10.25-11.5, port_1 x 3.75-4.75, port_2
 * x 2.25-3.25, all y 0.25-1.25), widened a little so they're easy to hit. With a cartridge in, its door stands open
 * and the cart lies flat in the bay ({@link #LOADED}: models/block/console_8bit_open.json; the cart is drawn by
 * ConsoleRenderer, D188).
 */
public class Console8BitBlock extends ConsoleBlock {
    //? if >=1.20.3 && <26.3 {
    public static final MapCodec<Console8BitBlock> CODEC = simpleCodec(Console8BitBlock::new);
    //?}
    /** A cartridge is in: the door is open (D188). Kept in step with the slot by ConsoleBlockEntity's server tick. */
    public static final BooleanProperty LOADED = BooleanProperty.create("loaded");

    private static final ConsoleLayout LAYOUT = new ConsoleLayout(List.of(
            new ConsoleLayout.Zone(ConsolePart.POWER, false, 11.75, 14.0, 0, 1.6, new float[] {12, 0.35F, 2.75F, 13.25F, 1.1F, 3}),
            new ConsoleLayout.Zone(ConsolePart.RESET, false, 9.75, 11.75, 0, 1.6, new float[] {10.25F, 0.35F, 2.75F, 11.5F, 1.1F, 3}),
            new ConsoleLayout.Zone(ConsolePart.PORT_1, false, 3.5, 5.5, 0, 1.6, new float[] {3.75F, 0.25F, 2.8F, 4.75F, 1.25F, 3}),
            new ConsoleLayout.Zone(ConsolePart.PORT_2, false, 2.0, 3.5, 0, 1.6, new float[] {2.25F, 0.25F, 2.8F, 3.25F, 1.25F, 3})));

    // 12 px wide across the front, 10 px deep. The box is symmetric, so only the facing axis matters.
    private static final VoxelShape SHAPE_NORTH_SOUTH = box(2, 0, 3, 14, 4, 13);
    private static final VoxelShape SHAPE_EAST_WEST = box(3, 0, 2, 13, 4, 14);

    public Console8BitBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LOADED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LOADED);
    }

    //? if >=1.20.3 && <26.3 {
    @Override
    protected MapCodec<Console8BitBlock> codec() {
        return CODEC;
    }
    //?}

    @Override
    public ConsoleKind kind() {
        return ConsoleKind.PIXELON;
    }

    @Override
    public ConsoleLayout layout() {
        return LAYOUT;
    }

    @Override
    protected Plug plug(ItemStack stack, int port) {
        if (stack.is(CartridgeItems.CONTROLLER_8BIT.get())) return Plug.YES;
        if (stack.is(CartridgeItems.LIGHT_GUN.get())) return port == 1 ? Plug.YES : Plug.WRONG_PORT;
        if (stack.is(CartridgeItems.CONTROLLER_16BIT.get()) || stack.is(CartridgeItems.MOUSE_16BIT.get())
                || stack.is(CartridgeItems.CONTROLLER_VORTEX.get()) || stack.is(CartridgeItems.CONTROLLER_64.get())) {
            return Plug.OTHER_CONSOLE;
        }
        return Plug.NO;
    }

    @Override
    protected Component wrongPort(ItemStack stack) {
        return Component.translatable("cartridge_mod.console.gun_port_two");
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.X ? SHAPE_EAST_WEST : SHAPE_NORTH_SOUTH;
    }
}
