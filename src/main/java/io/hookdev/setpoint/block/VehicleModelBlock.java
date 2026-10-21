package io.hookdev.setpoint.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Never placed: holds the models of something a renderer puts together (a vehicle's moving parts, a big tank), one per PART. */
public class VehicleModelBlock extends Block {
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 15);

    public VehicleModelBlock(Block.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART);
    }
}
