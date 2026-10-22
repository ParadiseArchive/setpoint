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
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Steel floor grating, the floor of the gas sphere's platform: a thin see-through floor for walkways and platforms.
 * Like a slab it lies in the bottom or the top of the block, by where you click; a grating in the top of the block
 * makes a platform at the height of the blocks around it, with room for a handrail on top.
 */
public class GratingBlock extends Block {
    public static final EnumProperty<Half> HALF = BlockStateProperties.HALF;
    private static final VoxelShape BOTTOM = Block.box(0, 0, 0, 16, 2, 16), TOP = Block.box(0, 14, 0, 16, 16, 16);

    public GratingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(HALF, Half.BOTTOM));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        if (here.getBlock() instanceof HandrailBlock) return here.setValue(HandrailBlock.FLOOR, true); // the floor under a handrail
        Direction face = context.getClickedFace();
        BlockPos pos = context.getClickedPos();
        boolean top = face == Direction.DOWN || (face != Direction.UP && context.getClickLocation().y - pos.getY() > 0.5);
        return this.defaultBlockState().setValue(HALF, top ? Half.TOP : Half.BOTTOM);
    }

    /** A handrail goes into the same block as a grating in its bottom half: the walkway's floor and its rail. */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return state.getValue(HALF) == Half.BOTTOM && context.getItemInHand().getItem() instanceof net.minecraft.world.item.BlockItem item
                && item.getBlock() instanceof HandrailBlock;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(HALF) == Half.TOP ? TOP : BOTTOM;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }
}
