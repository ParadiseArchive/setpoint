package io.hookdev.setpoint.block;

import java.util.Map;
import java.util.function.Function;

import io.hookdev.setpoint.hydraulics.ForeignVessel;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
 * A pipe segment. Connects to other pipes, vessels, valves along their axis, and pumps on their suction/discharge side.
 * Pipes carry no fluid themselves: a pump traces the pipe run to find the vessels at both ends.
 * The pipe wrench can cut a side, so two lines can run next to each other without joining.
 */
public class FluidPipeBlock extends Block {
    public static final Map<Direction, BooleanProperty> CONNECTIONS = PipeBlock.PROPERTY_BY_DIRECTION;
    /** Sides cut with the wrench: they never connect. */
    public static final Map<Direction, BooleanProperty> CUT = java.util.Arrays.stream(Direction.values())
            .collect(java.util.stream.Collectors.toUnmodifiableMap(d -> d, d -> BooleanProperty.create(d.getSerializedName() + "_cut")));

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
        for (Direction direction : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(direction), false).setValue(CUT.get(direction), false);
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

    /** Whether a pipe in {@code state} at {@code pos} should connect towards {@code direction}. */
    public static boolean connectsTo(BlockGetter level, BlockPos pos, BlockState state, Direction direction) {
        if (state.getBlock() instanceof FluidPipeBlock && state.getValue(CUT.get(direction))) return false;
        BlockState neighbour = level.getBlockState(pos.relative(direction));
        if (neighbour.getBlock() instanceof FluidPipeBlock) {
            return !neighbour.getValue(CUT.get(direction.getOpposite()));
        }
        if (neighbour.getBlock() instanceof VesselBlock) {
            return true;
        }
        // another mod's tank (anything with fluid storage on that side)
        if (level instanceof Level world && ForeignVessel.isStorage(world, pos.relative(direction), direction.getOpposite())) {
            return true;
        }
        if (neighbour.getBlock() instanceof PumpBlock) {
            return neighbour.getValue(PumpBlock.FACING).getAxis() == direction.getAxis();
        }
        if (neighbour.getBlock() instanceof ControlValveBlock) {
            return neighbour.getValue(ControlValveBlock.AXIS) == direction.getAxis();
        }
        if (neighbour.getBlock() instanceof FlowTransmitterBlock) {
            return neighbour.getValue(FlowTransmitterBlock.AXIS) == direction.getAxis();
        }
        if (neighbour.getBlock() instanceof HandValveBlock) {
            return neighbour.getValue(HandValveBlock.AXIS) == direction.getAxis();
        }
        if (neighbour.getBlock() instanceof CheckValveBlock) {
            return neighbour.getValue(CheckValveBlock.FACING).getAxis() == direction.getAxis();
        }
        return false;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState();
        for (Direction direction : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(direction), connectsTo(context.getLevel(), context.getClickedPos(), state, direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return state.setValue(CONNECTIONS.get(directionToNeighbour), connectsTo(level, pos, state, directionToNeighbour));
    }

    /** All six connections worked out again, for when a side is cut or a neighbour turned. */
    public static BlockState refresh(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(direction), connectsTo(level, pos, state, direction));
        }
        return state;
    }

    /** Pipes placed by commands or structures work out their connections too. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BlockState connected = refresh(state, level, pos);
        if (connected != state) level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapes.apply(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        CUT.values().forEach(builder::add);
    }
}
