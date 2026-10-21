package io.hookdev.setpoint.block;

import java.util.List;

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

/**
 * A steel drum: a real one holds 200 kg, here 4 buckets (20 mB a kilogram). One and a half blocks high: its top
 * half block is a {@link TankPartBlock}. Its label turns to face you when you place it.
 */
public class DrumBlock extends PortableTankBlock {
    /** 200 kg of water at 20 mB a kilogram. */
    public static final int CAPACITY = 4_000;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 16, 15);

    public DrumBlock(BlockBehaviour.Properties properties) {
        super(properties, CAPACITY, "D");
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return fitting(context, this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()));
    }

    @Override
    public double liquidHeight() { return 1.5; }

    @Override
    public List<BlockPos> parts(BlockPos base, BlockState state) {
        return List.of(base.above());
    }

    @Override
    protected int partKind() { return TankPartBlock.DRUM_TOP; }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
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
