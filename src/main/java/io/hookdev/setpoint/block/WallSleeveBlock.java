package io.hookdev.setpoint.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A wall sleeve: a piece of concrete wall with a pipe going straight through it, a flange on either face. It is a
 * liquid pipe that is cut on its four other sides, so it only joins along its axis, the way you placed it.
 */
public class WallSleeveBlock extends FluidPipeBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;

    public WallSleeveBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.defaultBlockState().setValue(AXIS, Direction.Axis.Z));
    }

    /** Through the wall the way you look: every side off its axis is cut, so no pipe beside it joins in. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction.Axis axis = context.getNearestLookingDirection().getAxis();
        BlockState state = this.defaultBlockState().setValue(AXIS, axis);
        for (Direction direction : Direction.values()) state = state.setValue(CUT.get(direction), direction.getAxis() != axis);
        return refresh(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AXIS);
    }
}
