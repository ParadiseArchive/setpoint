package io.hookdev.setpoint.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A transformer: an oil-filled tank with cooling fins and three bushings on top. Power goes through it like through a
 * cable, so it makes a fine junction where several cables meet.
 */
public class TransformerBlock extends PowerCableBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 15, 15);

    public TransformerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
