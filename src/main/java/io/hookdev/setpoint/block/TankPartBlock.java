package io.hookdev.setpoint.block;

import io.hookdev.setpoint.block.entity.TankPartBlockEntity;
import io.hookdev.setpoint.hydraulics.VesselBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The rest of a drum or IBC: pipes connect to it as to the container itself. */
public class TankPartBlock extends MultiblockPartBlock implements VesselBlock {
    /** Which shape: the top of a drum, or a full block of an IBC. */
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 1);
    public static final int IBC = 0, DRUM_TOP = 1;
    private static final VoxelShape DRUM_TOP_SHAPE = Block.box(1, 0, 1, 15, 8, 15);

    public TankPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(KIND, IBC));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TankPartBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(KIND) == DRUM_TOP ? DRUM_TOP_SHAPE : Shapes.block();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }
}
