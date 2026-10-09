package com.nanobuntv.cartridge.arcade.block;

import com.nanobuntv.cartridge.util.Compat;
import com.mojang.serialization.MapCodec;
import com.nanobuntv.cartridge.arcade.ArcadeContent;
import com.nanobuntv.cartridge.arcade.CartridgeArcade;
import com.nanobuntv.cartridge.block.ConsoleBlock;
import com.nanobuntv.cartridge.block.ConsoleLayout;
import com.nanobuntv.cartridge.block.DisplayBlock;
import com.nanobuntv.cartridge.block.Screen;
import com.nanobuntv.cartridge.blockentity.ConsoleBlockEntity;
import com.nanobuntv.cartridge.emu.ConsoleKind;
import com.nanobuntv.cartridge.item.CartridgeItems;
import com.nanobuntv.cartridge.util.Use;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * An Arcade Cabinet (D198): upright, two blocks tall, one wide for one player or two wide for two (D202), with its own
 * square screen (D205), a lit marquee, a coin door and speaker grilles. {@link #COLUMN} counts from the viewer's
 * left, {@link #ROW} from the bottom; the bottom left space is the anchor: it holds the block entity, draws the whole
 * model (the others have an empty one) and is where the AV Cable links (D198). Breaking any space breaks the cabinet.
 * <p>
 * Right-clicking the control panel takes that player's controls (the 2-player cabinet's left half is Player 1, its
 * right half Player 2); with an Arcade Board in hand anywhere, the board slides in; anywhere else opens the GUI, where
 * POWER is (D203). The body takes its dye from the item (D204), kept in the block entity.
 * Models: models/arcade_cabinet.bbmodel and arcade_cabinet_2p.bbmodel.
 */
public class ArcadeCabinetBlock extends ConsoleBlock {
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 1);
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 1);
    /** The body's colour before any dye (D204): a dark charcoal. */
    public static final int DEFAULT_COLOR = 0x3A3A42;

    /**
     * Where things are in the anchor's north-facing model, in pixels (the 2-player cabinet reaches to x = -16, its
     * second column; the viewer, at the front, sees high x on their left):
     *
     * @param screen    the glass: x1, y1, x2, y2, and its depth z
     * @param marquee   the marquee's lit face: x1, y1, x2, y2, z
     * @param panelY    the control panel's height range (with the sticks), {from, to}
     * @param panelZ    how far back from the front the panel reaches
     * @param speaker   where the sound comes from
     * @param jack      the AV jack, at the back
     * @param boxes     the collision boxes, x1 y1 z1 x2 y2 z2 each
     */
    public record Geometry(int players, double[] screen, double[] marquee, double[] panelY, double panelZ,
                           double[] speaker, double[] jack, double[][] boxes) {}

    public static final Geometry ONE_PLAYER = new Geometry(1,
            new double[] {2.5, 15, 13.5, 26, 7},
            new double[] {1, 28.5, 15, 31.5, 4},
            new double[] {10.5, 17.5}, 6.5,
            new double[] {8, 27.75, 6}, new double[] {8, 2, 16},
            new double[][] {{0, 0, 3, 16, 11, 16}, {0.5, 11, 0, 15.5, 13.5, 16}, {0, 13.5, 6, 16, 28.5, 16},
                    {0, 28.5, 4, 16, 32, 16}});
    public static final Geometry TWO_PLAYERS = new Geometry(2,
            new double[] {-7, 14.5, 7, 28.5, 7},
            new double[] {-15, 29, 15, 31.5, 4},
            new double[] {10.5, 17.5}, 6.5,
            new double[] {0, 23, 6}, new double[] {0, 2, 16},
            new double[][] {{-16, 0, 3, 16, 11, 16}, {-15.5, 11, 0, 15.5, 13.5, 16}, {-16, 13.5, 6, 16, 29, 16},
                    {-16, 29, 4, 16, 32, 16}});

    private final Geometry geometry;
    /** [column][row] -> shape per facing. */
    private final Map<?, ?>[][] shapes = new Map<?, ?>[2][2];

    public ArcadeCabinetBlock(Properties properties, Geometry geometry) {
        super(properties);
        this.geometry = geometry;
        registerDefaultState(defaultBlockState().setValue(COLUMN, 0).setValue(ROW, 0));
        for (int column = 0; column < 2; column++) {
            for (int row = 0; row < 2; row++) shapes[column][row] = shapesByFacing(partShape(column, row));
        }
    }

    public Geometry geometry() {
        return geometry;
    }

    /** Columns: one, or two for the 2-player cabinet. */
    public int columns() {
        return geometry.players();
    }

    //? if >=1.20.3 && <26.3 {
    @Override
    protected MapCodec<? extends ArcadeCabinetBlock> codec() {
        return simpleCodec(p -> new ArcadeCabinetBlock(p, geometry));
    }
    //?}

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(COLUMN, ROW);
    }

    @Override
    public ConsoleKind kind() {
        return CartridgeArcade.ARCADE;
    }

    /** The cabinet has no clickable parts of the console kind: the panel is found by {@link #player}. */
    @Override
    public ConsoleLayout layout() {
        return new ConsoleLayout(List.of());
    }

    @Override
    protected Plug plug(ItemStack stack, int port) {
        return Plug.NO;
    }

    @Override
    public int builtInPlayers() {
        return geometry.players();
    }

    @Override
    public boolean powerInGui() {
        return true;
    }

    @Override
    public BlockEntityType<? extends ConsoleBlockEntity> blockEntityType() {
        return ArcadeContent.CABINET_ENTITY.get();
    }

    /** The viewer's right, from the front: where column 1 is. */
    private static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    @Override
    public BlockPos anchor(BlockState state, BlockPos pos) {
        return pos.below(state.getValue(ROW)).relative(right(state.getValue(FACING)), -state.getValue(COLUMN));
    }

    public static boolean isAnchor(BlockState state) {
        return state.getValue(COLUMN) == 0 && state.getValue(ROW) == 0;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isAnchor(state) ? new ArcadeCabinetBlockEntity(pos, state) : null;
    }

    @Override
    @Nullable
    public Vec3 speaker(BlockState state, BlockPos anchor) {
        double[] s = geometry.speaker();
        return DisplayBlock.point(anchor, state.getValue(FACING), s[0], s[1], s[2]);
    }

    /** The cabinet's own glass (D205), for the renderer: a CRT's, square. */
    public Screen screen(BlockPos anchor, Direction facing) {
        double[] g = geometry.screen();
        Vec3 origin = DisplayBlock.point(anchor, facing, g[2], g[3], g[4]);
        return new Screen(origin, DisplayBlock.point(anchor, facing, g[0], g[3], g[4]).subtract(origin),
                DisplayBlock.point(anchor, facing, g[2], g[1], g[4]).subtract(origin), Screen.Style.CRT, null);
    }

    // --- Placing and breaking ------------------------------------------------------------------------------------

    /** Every space of a cabinet whose anchor is {@code anchor}. */
    private List<BlockPos> spaces(BlockPos anchor, Direction facing) {
        Direction right = right(facing);
        return columns() == 1 ? List.of(anchor, anchor.above())
                : List.of(anchor, anchor.above(), anchor.relative(right), anchor.relative(right).above());
    }

    private BlockState part(BlockState anchorState, BlockPos anchor, BlockPos space) {
        int column = space.getX() == anchor.getX() && space.getZ() == anchor.getZ() ? 0 : 1;
        int row = space.getY() - anchor.getY();
        return anchorState.setValue(COLUMN, column).setValue(ROW, row);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        if (state == null) return null;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (level.isOutsideBuildHeight(pos.above())) return null;
        for (BlockPos space : spaces(pos, state.getValue(FACING))) {
            if (!space.equals(pos) && !level.getBlockState(space).canBeReplaced(context)) return null;
        }
        return state.setValue(COLUMN, 0).setValue(ROW, 0);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide) return;
        for (BlockPos space : spaces(pos, state.getValue(FACING))) {
            if (!space.equals(pos)) level.setBlock(space, part(state, pos, space), Block.UPDATE_ALL);
        }
        if (level.getBlockEntity(pos) instanceof ArcadeCabinetBlockEntity cabinet) {
            cabinet.setColor(Compat.dye(stack, DEFAULT_COLOR)); // D204
        }
    }

    //? if >=1.20.3 {
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        breakAnchor(level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }
    //?} else {
    /*@Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        breakAnchor(level, pos, state, player);
        super.playerWillDestroy(level, pos, state, player);
    }
    *///?}

    /** Breaking an upper or right space breaks the anchor, which drops the cabinet and its board. */
    private void breakAnchor(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && !isAnchor(state)) {
            BlockPos anchor = anchor(state, pos);
            if (level.getBlockState(anchor).is(this)) level.destroyBlock(anchor, !player.isCreative(), player);
        }
    }

    @Override
    protected void removed(BlockState state, Level level, BlockPos pos) {
        BlockPos anchor = anchor(state, pos);
        for (BlockPos space : spaces(anchor, state.getValue(FACING))) {
            if (!space.equals(pos) && level.getBlockState(space).is(this)) {
                level.setBlock(space, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return true;
    }

    /** The cabinet item, in its colour; only the anchor drops it. */
    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (!isAnchor(state)) return List.of();
        ItemStack item = new ItemStack(asItem());
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof ArcadeCabinetBlockEntity cabinet
                && cabinet.color() != DEFAULT_COLOR) {
            Compat.setDye(item, cabinet.color());
        }
        return List.of(item);
    }

    //? if >=1.21.5 {
    /*@Override
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        return cloneItem(level, pos, state);
    }
    *///?} else if >=1.20.5 {
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return cloneItem(level, pos, state);
    }
    //?} else {
    /*@Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return cloneItem(level, pos, state);
    }
    *///?}

    /** The cabinet's item in its colour (middle click). */
    private ItemStack cloneItem(BlockGetter level, BlockPos pos, BlockState state) {
        ItemStack item = new ItemStack(asItem());
        if (level.getBlockEntity(anchor(state, pos)) instanceof ArcadeCabinetBlockEntity cabinet && cabinet.color() != DEFAULT_COLOR) {
            Compat.setDye(item, cabinet.color());
        }
        return item;
    }

    // --- Using it ---------------------------------------------------------------------------------------------------

    /**
     * The player whose half of the control panel this hit is on (0 or 1), or -1 off the panel (D202). Hits are
     * turned into the anchor's north-facing model's pixels.
     */
    public int player(BlockHitResult hit, BlockState state, BlockPos anchor) {
        Direction facing = state.getValue(FACING);
        Vec3 local = hit.getLocation().subtract(anchor.getX(), anchor.getY(), anchor.getZ());
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
        double y = local.y * 16;
        double[] panel = geometry.panelY();
        if (y < panel[0] || y > panel[1] || z > geometry.panelZ()) return -1;
        return columns() == 2 && x < 0 ? 1 : 0;
    }

    @Override
    protected Use itemUse(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hit) {
        if (stack.is(CartridgeItems.AV_CABLE.get())) return Use.TO_ITEM;
        BlockPos anchor = anchor(state, pos);
        if (!(level.getBlockEntity(anchor) instanceof ConsoleBlockEntity console)) {
            return Use.EMPTY_HAND;
        }
        if (ConsoleBlockEntity.isCartridge(stack) && console.cartridge().isEmpty()) {
            if (!level.isClientSide) {
                if (!takes(stack)) {
                    Compat.overlay(player, Component.translatable("cartridge_mod.console.wrong_cart", getName()));
                } else {
                    console.setItem(ConsoleBlockEntity.CART_SLOT, Compat.takeOne(stack, player)); // D203
                }
            }
            return Use.SIDED;
        }
        return Use.EMPTY_HAND;
    }

    @Override
    protected Use emptyUse(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos anchor = anchor(state, pos);
        if (!(level.getBlockEntity(anchor) instanceof ConsoleBlockEntity console)) return Use.PASS;
        if (level.isClientSide) return Use.SUCCESS;
        int port = player(hit, state, anchor);
        if (port >= 0) usePort(console, port, level, anchor, (ServerPlayer) player); // D202
        else player.openMenu(console); // D203: POWER and the board slot
        return Use.CONSUME;
    }

    // --- Shape ------------------------------------------------------------------------------------------------------

    /** The part of the cabinet's boxes inside one space; column 1 is at -x in the anchor's north-facing model. */
    private VoxelShape partShape(int column, int row) {
        double dx = -column * 16, dy = row * 16;
        VoxelShape shape = Shapes.empty();
        for (double[] b : geometry.boxes()) {
            double x1 = Math.max(0, b[0] - dx), x2 = Math.min(16, b[3] - dx);
            double y1 = Math.max(0, b[1] - dy), y2 = Math.min(16, b[4] - dy);
            if (x1 < x2 && y1 < y2) shape = Shapes.or(shape, box(x1, y1, b[2], x2, y2, b[5]));
        }
        return shape.isEmpty() ? Shapes.block() : shape;
    }

    @Override
    @SuppressWarnings("unchecked")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return ((Map<Direction, VoxelShape>) shapes[state.getValue(COLUMN)][state.getValue(ROW)]).get(state.getValue(FACING));
    }
}
