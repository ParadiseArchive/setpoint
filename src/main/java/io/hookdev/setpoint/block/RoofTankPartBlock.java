package io.hookdev.setpoint.block;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.entity.FloatingRoofTankBlockEntity;
import io.hookdev.setpoint.block.entity.TankPartBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The rest of a floating roof tank. Its shape follows the tank: the wall, the roof where it floats now, open outside
 * the wall. The blocks of the corner ladder hold nothing up but let you climb, and so does the rolling ladder inside.
 */
public class RoofTankPartBlock extends TankPartBlock {
    /** A block of the caged ladder in the corner. */
    public static final BooleanProperty LADDER = BooleanProperty.create("ladder");

    public RoofTankPartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(KIND, IBC).setValue(LADDER, false));
    }

    private static @Nullable FloatingRoofTankBlockEntity tank(BlockGetter level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof TankPartBlockEntity part) || part.base() == null) return null;
        return level.getBlockEntity(part.base()) instanceof FloatingRoofTankBlockEntity tank ? tank : null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(LADDER) ? Shapes.block() : FloatingRoofTankBlock.shapeAt(tank(level, pos), pos);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(LADDER) ? Shapes.empty() : getShape(state, level, pos, context);
    }

    @Override
    public boolean isLadder(BlockState state, LevelReader level, BlockPos pos, LivingEntity entity) {
        if (state.getValue(LADDER)) return true;
        FloatingRoofTankBlockEntity tank = tank(level, pos);
        return tank != null && tank.onRollingLadder(entity.position());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LADDER);
    }
}
