package io.hookdev.setpoint.hydraulics;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.CheckValveBlock;
import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.FlowTransmitterBlock;
import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.HandValveBlock;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which way liquid can go through a piping block. Pipes follow their arms (so a pipe cut with the wrench
 * really stops flow), valves pass along their axis, and a closed hand valve passes nothing. A check valve passes
 * only the way its arrow points: when the way the liquid goes is known, ask with {@code downstream}.
 */
public final class Piping {
    private Piping() {}

    /** Can liquid leave this block going {@code direction}? (Either way through a check valve: for finding things.) */
    public static boolean leaves(BlockState state, Direction direction) {
        return leaves(state, direction, null);
    }

    /** Can liquid travelling {@code direction} come into this block? */
    public static boolean enters(BlockState state, Direction direction) {
        return enters(state, direction, null);
    }

    /**
     * @param downstream true when we follow the line the way the liquid flows, false when we follow it back
     *                   against the flow (a pump's suction), null when it does not matter
     */
    public static boolean leaves(BlockState state, Direction direction, @Nullable Boolean downstream) {
        if (state.getBlock() instanceof FluidPipeBlock) return state.getValue(FluidPipeBlock.CONNECTIONS.get(direction));
        return passesValve(state, direction, downstream);
    }

    public static boolean enters(BlockState state, Direction direction, @Nullable Boolean downstream) {
        if (state.getBlock() instanceof FluidPipeBlock) return state.getValue(FluidPipeBlock.CONNECTIONS.get(direction.getOpposite()));
        return passesValve(state, direction, downstream);
    }

    private static boolean passesValve(BlockState state, Direction direction, @Nullable Boolean downstream) {
        if (state.getBlock() instanceof io.hookdev.setpoint.gas.GasLine) return false; // a gas valve is no part of a liquid line
        if (state.getBlock() instanceof CheckValveBlock) {
            Direction arrow = state.getValue(CheckValveBlock.FACING);
            if (arrow.getAxis() != direction.getAxis()) return false;
            return downstream == null || (downstream ? direction == arrow : direction == arrow.getOpposite());
        }
        if (state.getBlock() instanceof ControlValveBlock) return state.getValue(ControlValveBlock.AXIS) == direction.getAxis();
        if (state.getBlock() instanceof FlowTransmitterBlock) return state.getValue(FlowTransmitterBlock.AXIS) == direction.getAxis();
        if (state.getBlock() instanceof HandValveBlock) {
            return state.getValue(HandValveBlock.AXIS) == direction.getAxis() && HandValveBlock.isOpen(state);
        }
        return false;
    }
}
