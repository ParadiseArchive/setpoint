package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.SetpointContent;
import io.hookdev.setpoint.block.entity.FlareBlockEntity;
import io.hookdev.setpoint.block.entity.MultiblockPartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rest of a flare: its shape is the piece of stack, leg, platform or drum in this block. The ladder's blocks let
 * you climb; the tip's blocks light up while the flare burns.
 */
public class FlarePartBlock extends MultiblockPartBlock {
    public static final BooleanProperty LADDER = BooleanProperty.create("ladder");
    public static final BooleanProperty TIP = BooleanProperty.create("tip");
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public FlarePartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LADDER, false).setValue(TIP, false).setValue(LIT, false));
    }

    private static @Nullable FlareBlockEntity flare(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof MultiblockPartBlockEntity part) || part.base() == null) return null;
        return level.getBlockEntity(part.base()) instanceof FlareBlockEntity flare ? flare : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(LADDER)) return Shapes.block();
        VoxelShape shape = FlareBlock.shapeAt(flare(level, pos), pos);
        return shape.isEmpty() ? Shapes.empty() : shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(LADDER)) return getShape(state, level, pos, context);
        FlareBlockEntity flare = flare(level, pos);
        return flare == null ? Shapes.empty() : Shapes.or(FlareBlock.rungs(flare), FlareBlock.shapeAt(flare, pos));
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return state.getValue(LADDER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartBlockEntity(SetpointContent.FLARE_PART_ENTITY.get(), pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LADDER, TIP, LIT);
    }
}
