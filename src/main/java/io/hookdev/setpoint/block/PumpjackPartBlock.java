package io.hookdev.setpoint.block;

import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The rest of a pumpjack: the frame on the ground, the samson post, the gearbox with its crank. Power goes in anywhere. */
public class PumpjackPartBlock extends MultiblockPartBlock {
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 2);
    public static final int FRAME = 0, POST = 1, GEARBOX = 2;
    private static final VoxelShape[] SHAPES = { Block.box(0, 0, 0, 16, 4, 16), Block.box(3, 0, 3, 13, 16, 13), Block.box(2, 0, 2, 14, 16, 14) };

    public PumpjackPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(KIND, FRAME));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(KIND)];
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartBlockEntity(io.hookdev.setpoint.SetpointContent.PUMPJACK_PART_ENTITY.get(), pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }
}
