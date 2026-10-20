package io.hookdev.setpoint.block;

import java.util.Map;
import java.util.function.Function;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A pipe segment. Connects to other pipes, tanks, and pumps on their suction/discharge side.
 * Pipes carry no fluid themselves: a pump traces the pipe run to find the tanks at both ends.
 */
public class FluidPipeBlock extends Block {
    public static final Map<Direction, BooleanProperty> CONNECTIONS = PipeBlock.PROPERTY_BY_DIRECTION;

    private static final VoxelShape CORE = Block.box(5, 5, 5, 11, 11, 11);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, Block.box(5, 5, 0, 11, 11, 5),
            Direction.SOUTH, Block.box(5, 5, 11, 11, 11, 16),
            Direction.WEST, Block.box(0, 5, 5, 5, 11, 11),
            Direction.EAST, Block.box(11, 5, 5, 16, 11, 11),
            Direction.DOWN, Block.box(5, 0, 5, 11, 5, 11),
            Direction.UP, Block.box(5, 11, 5, 11, 16, 11));

    private final Function<BlockState, VoxelShape> shapes;

    public FluidPipeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any();
        for (BooleanProperty connection : CONNECTIONS.values()) {
            state = state.setValue(connection, false);
        }
        this.registerDefaultState(state);
        this.shapes = this.getShapeForEachState(FluidPipeBlock::makeShape);
    }

    private static VoxelShape makeShape(BlockState state) {
        VoxelShape shape = CORE;
        for (Direction direction : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(direction))) {
                shape = Shapes.or(shape, ARMS.get(direction));
            }
        }
        return shape;
    }

    /** Whether a pipe at {@code pos} should connect towards {@code direction}. */
    public static boolean connectsTo(BlockGetter level, BlockPos pos, Direction direction) {
        BlockState neighbour = level.getBlockState(pos.relative(direction));
        if (neighbour.getBlock() instanceof FluidPipeBlock || neighbour.getBlock() instanceof TankBlock) {
            return true;
        }
        if (neighbour.getBlock() instanceof PumpBlock) {
            return neighbour.getValue(PumpBlock.FACING).getAxis() == direction.getAxis();
        }
        if (neighbour.getBlock() instanceof ControlValveBlock) {
            return neighbour.getValue(ControlValveBlock.AXIS) == direction.getAxis();
        }
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(direction), connectsTo(context.getLevel(), context.getClickedPos(), direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(CONNECTIONS.get(directionToNeighbour), connectsTo(level, pos, directionToNeighbour));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
    }
}
