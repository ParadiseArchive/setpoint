package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.entity.GasSphereBlockEntity;
import io.hookdev.setpoint.block.entity.GasSpherePartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rest of a gas sphere: its shape is the piece of sphere, column, pipe or platform in this block. A pressure
 * transmitter or safety valve against any of it works on the whole sphere. The blocks of the corner ladder let you
 * climb.
 */
public class GasSpherePartBlock extends MultiblockPartBlock {
    public static final BooleanProperty LADDER = BooleanProperty.create("ladder");

    public GasSpherePartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(LADDER, false));
    }

    private static @Nullable GasSphereBlockEntity sphere(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GasSpherePartBlockEntity part ? part.sphere() : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(LADDER)) return Shapes.block();
        VoxelShape shape = GasSphereBlock.shapeAt(sphere(level, pos), pos);
        return shape.isEmpty() ? Shapes.empty() : shape;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(LADDER)) return getShape(state, level, pos, context);
        GasSphereBlockEntity sphere = sphere(level, pos);
        return sphere == null ? Shapes.empty() : Shapes.or(FloatingRoofTankBlock.ladderShape(sphere.centre(), pos), GasSphereBlock.shapeAt(sphere, pos));
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        return state.getValue(LADDER);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GasSpherePartBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LADDER);
    }
}
