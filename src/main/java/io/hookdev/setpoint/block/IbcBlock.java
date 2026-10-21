package io.hookdev.setpoint.block;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * An IBC (intermediate bulk container): a white plastic tote in a steel cage on a pallet. A real one holds
 * 1000 kg, here 20 buckets (20 mB a kilogram). Two by two by two blocks: this base block is the front corner on
 * your right as you place it; the IBC reaches to your left and away from you, and {@link TankPartBlock}s fill that
 * space. You see the liquid through the tote; the outlet valve faces you.
 */
public class IbcBlock extends PortableTankBlock {
    /** 1000 kg of water at 20 mB a kilogram. */
    public static final int CAPACITY = 20_000;
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** How full it looks through the tote, in eighths. */
    public static final IntegerProperty LEVEL = TankBlock.LEVEL;

    public IbcBlock(BlockBehaviour.Properties properties) {
        super(properties, CAPACITY, "IBC");
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEVEL, 0));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return fitting(context, this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()));
    }

    @Override
    public double liquidHeight() { return 2.0; }

    /** The model reaches one block clockwise from the front and one block back, and one block up. */
    @Override
    public List<BlockPos> parts(BlockPos base, BlockState state) {
        Direction front = state.getValue(FACING), side = front.getClockWise(), back = front.getOpposite();
        List<BlockPos> parts = new ArrayList<>();
        for (int x = 0; x < 2; x++) for (int y = 0; y < 2; y++) for (int z = 0; z < 2; z++) {
            if (x + y + z > 0) parts.add(base.relative(side, x).above(y).relative(back, z));
        }
        return parts;
    }

    @Override
    protected int partKind() { return TankPartBlock.IBC; }

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
        builder.add(FACING, LEVEL);
    }
}
