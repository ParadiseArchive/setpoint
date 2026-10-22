package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.DistillationColumnBlockEntity;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A crude oil distillation column with its reboiler and condenser, three blocks wide, four deep and eleven high, placed
 * with one block: that block is the feed nozzle (crude oil in), in the middle of the front row. On the front row are the
 * reboiler's steam inlet (left) and the residue outlet (right); up the front of the column the side draws for diesel,
 * kerosene and naphtha, and at the top the cooling water inlet (left) and the LPG outlet (right). Every nozzle faces
 * the front. {@link ColumnPartBlock}s fill the rest; its renderer draws all of it.
 */
public class DistillationColumnBlock extends BaseEntityBlock implements MultiblockBase, VesselBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** In pixels from the bottom middle of the feed block (z towards the front): the column's middle, its radius and height. */
    public static final int COLUMN_Z = -32, RADIUS = 20, HEIGHT = 10, TOP = 10;
    private static final VoxelShape FEED = Block.box(2, 0, 2, 14, 14, 16);
    private static final Map<Long, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    public DistillationColumnBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        for (BlockPos part : parts(context.getClickedPos(), state)) {
            if (!context.getLevel().getBlockState(part).canBeReplaced(context)) return null;
        }
        return state;
    }

    /** The block x to your right (-1..1), back behind the front row (0..3) and up from the ground. */
    public static BlockPos cell(BlockPos base, BlockState state, int x, int back, int up) {
        Direction front = state.getValue(FACING);
        return base.relative(front.getCounterClockWise(), x).relative(front.getOpposite(), back).above(up);
    }

    /** The front row beside the feed, the column (3 x 3, ten high) and the condenser on top of it. */
    @Override
    public List<BlockPos> parts(BlockPos base, BlockState state) {
        List<BlockPos> parts = new ArrayList<>();
        parts.add(cell(base, state, -1, 0, 0));
        parts.add(cell(base, state, 1, 0, 0));
        for (int x = -1; x <= 1; x++) for (int back = 1; back <= 3; back++) for (int up = 0; up < HEIGHT; up++) parts.add(cell(base, state, x, back, up));
        for (int x = -1; x <= 1; x++) parts.add(cell(base, state, x, 2, TOP));
        return parts;
    }

    /** What nozzle, if any, a part at this place is. */
    public static int port(int x, int back, int up) {
        if (back == 0 && up == 0) return x < 0 ? ColumnPartBlock.STEAM : ColumnPartBlock.RESIDUE;
        if (back == 1 && x == 0 && up == 3) return ColumnPartBlock.DIESEL;
        if (back == 1 && x == 0 && up == 5) return ColumnPartBlock.KEROSENE;
        if (back == 1 && x == 0 && up == 7) return ColumnPartBlock.NAPHTHA;
        if (back == 1 && up == 9 && x != 0) return x < 0 ? ColumnPartBlock.WATER : ColumnPartBlock.LPG;
        return ColumnPartBlock.NONE;
    }

    /**
     * The shape of a part: the round column (columns of four pixels), the reboiler on the front row, the condenser on
     * top. x runs to your right (-1..1), back away from you, front is the way the column faces.
     */
    public static VoxelShape shape(Direction front, int x, int back, int up) {
        long key = ((long) front.get2DDataValue() << 24) | ((long) (x + 2) << 16) | ((long) back << 8) | up;
        return SHAPES.computeIfAbsent(key, k -> {
            if (back == 0) return Block.box(0, 0, 0, 16, 14, 16);       // the reboiler
            if (up == TOP) return Block.box(0, 0, 0, 16, 12, 16);       // the condenser
            Direction right = front.getCounterClockWise();
            VoxelShape shape = Shapes.empty();
            for (int cx = 0; cx < 16; cx += 4) for (int cz = 0; cz < 16; cz += 4) {
                double wx = cx + 2 - 8, wz = cz + 2 - 8; // from the middle of this block, in the world's x and z
                double lx = x * 16 + wx * right.getStepX() + wz * right.getStepZ(), lz = -back * 16 + wx * front.getStepX() + wz * front.getStepZ();
                if (Math.hypot(lx, lz - COLUMN_Z) <= RADIUS) shape = Shapes.or(shape, Block.box(cx, 0, cz, cx + 4, 16, cz + 4));
            }
            return shape.optimize();
        });
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack itemStack) {
        super.setPlacedBy(level, pos, state, by, itemStack);
        if (level.isClientSide()) return;
        Direction front = state.getValue(FACING);
        for (int x = -1; x <= 1; x++) for (int back = 0; back <= 3; back++) for (int up = 0; up <= TOP; up++) {
            BlockPos part = cell(pos, state, x, back, up);
            if (part.equals(pos) || !parts(pos, state).contains(part)) continue;
            level.setBlock(part, SetpointContent.COLUMN_PART.get().defaultBlockState().setValue(ColumnPartBlock.FACING, front)
                    .setValue(ColumnPartBlock.PORT, port(x, back, up)).setValue(ColumnPartBlock.X, x + 1).setValue(ColumnPartBlock.BACK, back)
                    .setValue(ColumnPartBlock.UP, up), Block.UPDATE_ALL);
            if (level.getBlockEntity(part) instanceof MultiblockPartBlockEntity be) be.setBase(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        for (BlockPos part : parts(pos, state)) {
            if (level.getBlockEntity(part) instanceof MultiblockPartBlockEntity be && pos.equals(be.base())) level.setBlock(part, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Crude oil comes in through the front of the feed nozzle. */
    @Override
    public boolean takesPipe(BlockState state, Direction side) {
        return side == state.getValue(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FEED;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof DistillationColumnBlockEntity column) column.describe(player);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DistillationColumnBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, SetpointContent.DISTILLATION_COLUMN_ENTITY.get(), DistillationColumnBlockEntity::serverTick);
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
