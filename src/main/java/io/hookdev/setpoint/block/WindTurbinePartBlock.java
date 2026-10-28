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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The rest of a wind turbine: the tower and the nacelle on top. */
public class WindTurbinePartBlock extends MultiblockPartBlock {
    public static final IntegerProperty KIND = IntegerProperty.create("kind", 0, 1);
    public static final int TOWER = 0, NACELLE = 1;
    private static final VoxelShape TOWER_SHAPE = Block.box(3, 0, 3, 13, 16, 13);

    public WindTurbinePartBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(KIND, TOWER));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(KIND) == TOWER ? TOWER_SHAPE : Shapes.block();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MultiblockPartBlockEntity(io.hookdev.setpoint.SetpointContent.WIND_TURBINE_PART_ENTITY.get(), pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(KIND);
    }
}
