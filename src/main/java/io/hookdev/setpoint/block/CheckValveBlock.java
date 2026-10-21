package io.hookdev.setpoint.block;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A check valve (non-return valve) in a pipe line: liquid passes only the way the arrow points. It stops a line
 * from draining back when a pump stops, and keeps a tank from flowing back into a lower one.
 * Placed pointing the way you look.
 */
public class CheckValveBlock extends Block {
    /** The arrow: the only way liquid can go through. */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private static final VoxelShape SHAPE_X = Shapes.or(Block.box(0, 5, 5, 16, 11, 11), Block.box(4, 4, 4, 12, 12, 12));
    private static final VoxelShape SHAPE_Y = Shapes.or(Block.box(5, 0, 5, 11, 16, 11), Block.box(4, 4, 4, 12, 12, 12));
    private static final VoxelShape SHAPE_Z = Shapes.or(Block.box(5, 5, 0, 11, 11, 16), Block.box(4, 4, 4, 12, 12, 12));
    private static final Map<Direction.Axis, VoxelShape> SHAPES = Map.of(Direction.Axis.X, SHAPE_X, Direction.Axis.Y, SHAPE_Y, Direction.Axis.Z, SHAPE_Z);

    public CheckValveBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getNearestLookingDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING).getAxis());
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
