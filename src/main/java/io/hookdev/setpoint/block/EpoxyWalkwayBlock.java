package io.hookdev.setpoint.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * An epoxy walkway: green floor with a yellow line along each side. Walkways side by side that run the same way make
 * one wider walkway: the lines between them go, only the outer ones stay.
 */
public class EpoxyWalkwayBlock extends FurnitureBlock {
    /** Whether the line on that side shows (looking along the walkway the way it faces: left is counter-clockwise). */
    public static final BooleanProperty LEFT = BooleanProperty.create("left"), RIGHT = BooleanProperty.create("right");

    public EpoxyWalkwayBlock(BlockBehaviour.Properties properties) {
        super(properties, Block.box(0, 0, 0, 16, 16, 16));
        this.registerDefaultState(this.defaultBlockState().setValue(LEFT, true).setValue(RIGHT, true));
    }

    /** A line shows on a side unless another walkway running the same way lies there. */
    private static BlockState lines(BlockState state, BlockGetter level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return state.setValue(LEFT, !sameWay(level.getBlockState(pos.relative(facing.getCounterClockWise())), facing))
                .setValue(RIGHT, !sameWay(level.getBlockState(pos.relative(facing.getClockWise())), facing));
    }

    private static boolean sameWay(BlockState neighbour, Direction facing) {
        return neighbour.getBlock() instanceof EpoxyWalkwayBlock && neighbour.getValue(FACING).getAxis() == facing.getAxis();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return lines(super.getStateForPlacement(context), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return directionToNeighbour.getAxis().isHorizontal() ? lines(state, level, pos) : state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LEFT, RIGHT);
    }
}
