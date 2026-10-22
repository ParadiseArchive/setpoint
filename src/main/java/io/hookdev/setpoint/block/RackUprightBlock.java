package io.hookdev.setpoint.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** An upright of pallet racking: a blue steel post with rows of holes for the beams. Stack them as high as the rack. */
public class RackUprightBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(5, 0, 5, 11, 16, 11);

    public RackUprightBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
