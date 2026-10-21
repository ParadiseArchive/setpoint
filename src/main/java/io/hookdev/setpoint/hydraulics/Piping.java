package io.hookdev.setpoint.hydraulics;

import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.HandValveBlock;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which way liquid can go through a piping block. Pipes follow their arms (so a pipe cut with the wrench
 * really stops flow), valves pass along their axis, and a closed hand valve passes nothing.
 */
public final class Piping {
    private Piping() {}

    /** Can liquid leave this block going {@code direction}? */
    public static boolean leaves(BlockState state, Direction direction) {
        if (state.getBlock() instanceof FluidPipeBlock) return state.getValue(FluidPipeBlock.CONNECTIONS.get(direction));
        return passesValve(state, direction);
    }

    /** Can liquid travelling {@code direction} come into this block? */
    public static boolean enters(BlockState state, Direction direction) {
        if (state.getBlock() instanceof FluidPipeBlock) return state.getValue(FluidPipeBlock.CONNECTIONS.get(direction.getOpposite()));
        return passesValve(state, direction);
    }

    private static boolean passesValve(BlockState state, Direction direction) {
        if (state.getBlock() instanceof ControlValveBlock) return state.getValue(ControlValveBlock.AXIS) == direction.getAxis();
        if (state.getBlock() instanceof HandValveBlock) {
            return state.getValue(HandValveBlock.AXIS) == direction.getAxis() && HandValveBlock.isOpen(state);
        }
        return false;
    }
}
