package io.hookdev.setpoint.block;

import io.hookdev.setpoint.block.entity.FlowTransmitterBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Flow transmitter: an inline meter in a pipe line, with a little display on top. Liquid passes along its axis. */
public class FlowTransmitterBlock extends InstrumentBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    private static final VoxelShape SHAPE_Z = Shapes.or(Block.box(5, 5, 0, 11, 11, 16), Block.box(4, 4, 3, 12, 12, 13), Block.box(5, 12, 5, 11, 15, 11));
    private static final VoxelShape SHAPE_X = Shapes.or(Block.box(0, 5, 5, 16, 11, 11), Block.box(3, 4, 4, 13, 12, 12), Block.box(5, 12, 5, 11, 15, 11));
    private static final VoxelShape SHAPE_Y = Shapes.or(Block.box(5, 0, 5, 11, 16, 11), Block.box(4, 3, 4, 12, 13, 12), Block.box(5, 5, 12, 11, 11, 15));

    public FlowTransmitterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.Z));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Line up with the pipe you click on, otherwise with the way you look.
        return this.defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis() == Direction.Axis.Y
                ? context.getHorizontalDirection().getAxis()
                : context.getClickedFace().getAxis());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlowTransmitterBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Y -> SHAPE_Y;
            case Z -> SHAPE_Z;
        };
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        if (rotation == Rotation.NONE || rotation == Rotation.CLOCKWISE_180) return state;
        return switch (state.getValue(AXIS)) {
            case X -> state.setValue(AXIS, Direction.Axis.Z);
            case Z -> state.setValue(AXIS, Direction.Axis.X);
            case Y -> state;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS);
    }
}
