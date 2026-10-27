package io.hookdev.setpoint.gas;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A piece that sits in a gas line, like a gas valve: gas pipes join it on both ends of its axis, and gas goes through
 * it as far as it is open. Liquid pipes never join it.
 */
public interface GasLine {
    /** The axis gas goes through it along. */
    Direction.Axis gasAxis(BlockState state);

    /** How far it is open: 0 lets nothing through, 1 is wide open. */
    double opening(Level level, BlockPos pos, BlockState state);

    /** Does a gas pipe join this piece on this side? */
    default boolean joinsGas(BlockState state, Direction side) {
        return gasAxis(state) == side.getAxis();
    }
}
