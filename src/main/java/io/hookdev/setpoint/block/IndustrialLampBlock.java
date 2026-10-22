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
import net.minecraft.world.phys.shapes.VoxelShape;

/** A bulkhead lamp with a wire guard, for walls and ceilings of a plant. It points out of the face you put it on. */
public class IndustrialLampBlock extends Block {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
            Direction.UP, Block.box(4, 0, 4, 12, 6, 12), Direction.DOWN, Block.box(4, 10, 4, 12, 16, 12),
            Direction.NORTH, Block.box(4, 4, 10, 12, 12, 16), Direction.SOUTH, Block.box(4, 4, 0, 12, 12, 6),
            Direction.WEST, Block.box(10, 4, 4, 16, 12, 12), Direction.EAST, Block.box(0, 4, 4, 6, 12, 12));

    public IndustrialLampBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
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
