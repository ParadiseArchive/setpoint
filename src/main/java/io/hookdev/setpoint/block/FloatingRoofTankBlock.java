package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.FloatingRoofTankBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A floating roof tank: the big storage tank of a tank farm, made of Hookonium steel. Round, eight blocks across and
 * six high, with an open top: the roof is a steel deck on a ring of pontoons that floats on the liquid, so it rises
 * and falls with the level. You can walk on it. A caged ladder in the front-right corner takes you up to the rim, and
 * a rolling ladder from the rim down to the roof.
 * <p>
 * This block is its base: the middle of the front row, where you placed it. The tank reaches four blocks to your
 * left, three to your right and eight away from you; {@link RoofTankPartBlock}s fill it and pass everything on to
 * this block. Everything you see is drawn by its renderer.
 */
public class FloatingRoofTankBlock extends PortableTankBlock {
    /** 8000 buckets: Hookonium steel holds more than its size. */
    public static final int CAPACITY = 8_000_000;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final int SIZE = 8, HIGH = 6;
    /** The shape, in pixels from the middle of the tank and up from its floor (tools/gen-assets.mjs draws the same). */
    public static final int RADIUS = 64, WALL = 59, PONTOON = 44, SHELL = 96, DECK_LOW = 14, DECK_HIGH = 88, PONTOON_UP = 4;
    /** Pixels per column of a part's shape. */
    private static final int COLUMN = 4;
    private static final Map<Long, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public FloatingRoofTankBlock(BlockBehaviour.Properties properties) {
        super(properties, CAPACITY, "TK");
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return fitting(context, this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()));
    }

    /** The liquid stands up to five and a half blocks; the roof floats on it. */
    @Override
    public double liquidHeight() { return 5.5; }

    /** Column i (0 = three to your right, 7 = four to your left) and row j (0 = the front) of the tank, at height y. */
    public static BlockPos cell(BlockPos base, BlockState state, int i, int j, int y) {
        Direction front = state.getValue(FACING);
        return base.relative(front.getClockWise(), i - 3).relative(front.getOpposite(), j).above(y);
    }

    /** The corners stay free, except the front-right one: the caged ladder stands there. */
    private static boolean inTank(int i, int j) {
        boolean corner = (i == 0 || i == SIZE - 1) && (j == 0 || j == SIZE - 1);
        return !corner || isLadder(i, j);
    }

    private static boolean isLadder(int i, int j) {
        return i == 0 && j == 0;
    }

    @Override
    public List<BlockPos> parts(BlockPos base, BlockState state) {
        List<BlockPos> parts = new ArrayList<>();
        for (int i = 0; i < SIZE; i++) for (int j = 0; j < SIZE; j++) {
            if (!inTank(i, j)) continue;
            for (int y = 0; y < HIGH; y++) if (i != 3 || j != 0 || y != 0) parts.add(cell(base, state, i, j, y));
        }
        return parts;
    }

    @Override
    protected int partKind() { return TankPartBlock.IBC; }

    @Override
    protected BlockState partState(BlockPos base, BlockState state, BlockPos part) {
        BlockPos ladder = cell(base, state, 0, 0, 0);
        return SetpointContent.ROOF_TANK_PART.get().defaultBlockState()
                .setValue(RoofTankPartBlock.LADDER, part.getX() == ladder.getX() && part.getZ() == ladder.getZ());
    }

    /** The middle of the tank, on its floor: a corner between blocks. */
    public static Vec3 centre(BlockPos base, BlockState state) {
        return Vec3.atCenterOf(cell(base, state, 3, 3, 0)).add(Vec3.atCenterOf(cell(base, state, 4, 4, 0))).scale(0.5).subtract(0, 0.5, 0);
    }

    /**
     * The shape of one block of the tank: the steel wall stands full height, the inside is solid up to the roof (the
     * deck, or the pontoons around its edge, a little higher), outside the wall is open. Built from columns of four
     * pixels; dx and dz are the block's corner from the middle of the tank, in blocks.
     */
    public static VoxelShape shape(int dx, int dz, int layer, int deck) {
        long key = ((long) (dx + 8) << 24) | ((long) (dz + 8) << 16) | ((long) layer << 8) | Math.clamp(deck, 0, 255);
        return SHAPES.computeIfAbsent(key, k -> {
            VoxelShape shape = Shapes.empty();
            for (int cx = 0; cx < 16; cx += COLUMN) for (int cz = 0; cz < 16; cz += COLUMN) {
                double r = Math.hypot(dx * 16 + cx + COLUMN / 2.0, dz * 16 + cz + COLUMN / 2.0);
                if (r > RADIUS) continue;
                int top = r >= WALL ? SHELL : r >= PONTOON ? deck + PONTOON_UP : deck;
                int height = Math.clamp(top - layer * 16, 0, 16);
                if (height > 0) shape = Shapes.or(shape, Block.box(cx, 0, cz, cx + COLUMN, height, cz + COLUMN));
            }
            return shape.optimize();
        });
    }

    /** The shape of the tank's block at pos, with the roof where it floats now. */
    public static VoxelShape shapeAt(@Nullable FloatingRoofTankBlockEntity tank, BlockPos pos) {
        if (tank == null) return Shapes.block();
        Vec3 centre = tank.centre();
        int dx = (int) Math.floor(pos.getX() - centre.x + 1e-6), dz = (int) Math.floor(pos.getZ() - centre.z + 1e-6);
        return shape(dx, dz, pos.getY() - tank.getBlockPos().getY(), tank.deck());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeAt(level.getBlockEntity(pos) instanceof FloatingRoofTankBlockEntity tank ? tank : null, pos);
    }

    /** Its renderer draws all of it. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FloatingRoofTankBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide()
                ? createTickerHelper(type, SetpointContent.FLOATING_ROOF_TANK_ENTITY.get(), FloatingRoofTankBlockEntity::clientTick)
                : createTickerHelper(type, SetpointContent.FLOATING_ROOF_TANK_ENTITY.get(), FloatingRoofTankBlockEntity::tick);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
}
