package io.hookdev.setpoint.block;

import io.hookdev.setpoint.gas.GasLine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A ball valve for a gas line: the hand valve's gas brother, with a yellow handle. Right-click to open or shut it; a
 * redstone signal holds it shut. Shut, it splits the gas line in two.
 */
public class GasValveBlock extends HandValveBlock implements GasLine {
    public GasValveBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public Direction.Axis gasAxis(BlockState state) {
        return state.getValue(AXIS);
    }

    @Override
    public double opening(Level level, BlockPos pos, BlockState state) {
        return isOpen(state) ? 1 : 0;
    }
}
