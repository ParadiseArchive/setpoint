package io.hookdev.setpoint.block;

import java.util.Map;
import java.util.function.Function;

import io.hookdev.setpoint.power.PowerNetwork;

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
import net.neoforged.neoforge.capabilities.Capabilities;

/**
 * A power cable: black rubber round copper. It joins other cables and every block that takes or gives FE, from this mod
 * or another. Like a pipe it holds nothing itself: FE that a generator pushes into it goes straight on to the machines
 * along the cable, shared out evenly (see {@link PowerNetwork}).
 */
public class PowerCableBlock extends Block {
    public static final Map<Direction, BooleanProperty> CONNECTIONS = PipeBlock.PROPERTY_BY_DIRECTION;

    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6),
            Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16),
            Direction.WEST, Block.box(0, 6, 6, 6, 10, 10),
            Direction.EAST, Block.box(10, 6, 6, 16, 10, 10),
            Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10),
            Direction.UP, Block.box(6, 10, 6, 10, 16, 10));

    private final Function<BlockState, VoxelShape> shapes;

    public PowerCableBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = this.stateDefinition.any();
        for (Direction direction : Direction.values()) state = state.setValue(CONNECTIONS.get(direction), false);
        this.registerDefaultState(state);
        this.shapes = this.getShapeForEachState(PowerCableBlock::makeShape);
    }

    private static VoxelShape makeShape(BlockState state) {
        VoxelShape shape = CORE;
        for (Direction direction : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(direction))) shape = Shapes.or(shape, ARMS.get(direction));
        }
        return shape;
    }

    /** Does a cable at {@code pos} join its neighbour towards {@code direction}: another cable, or anything with FE on that side? */
    public static boolean connectsTo(BlockGetter level, BlockPos pos, Direction direction) {
        BlockPos target = pos.relative(direction);
        if (level.getBlockState(target).getBlock() instanceof PowerCableBlock) return true;
        return level instanceof Level world && world.getCapability(Capabilities.Energy.BLOCK, target, direction.getOpposite()) != null;
    }

    private static BlockState refresh(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(direction), connectsTo(level, pos, direction));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return refresh(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        PowerNetwork.forget(level);
        return state.setValue(CONNECTIONS.get(directionToNeighbour), connectsTo(level, pos, directionToNeighbour));
    }

    /** Cables placed by commands or structures work out their connections too. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        PowerNetwork.forget(level);
        BlockState connected = refresh(state, level, pos);
        if (connected != state) level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, net.minecraft.server.level.ServerLevel level, BlockPos pos, boolean movedByPiston) {
        PowerNetwork.forget(level);
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
    }

    /** Water flows round a cable instead of washing it away. */
    @Override
    protected boolean canBeReplaced(BlockState state, net.minecraft.world.level.material.Fluid fluid) {
        return false;
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
