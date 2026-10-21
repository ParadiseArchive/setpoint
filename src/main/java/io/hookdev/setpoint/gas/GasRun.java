package io.hookdev.setpoint.gas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.GasPipeBlock;
import io.hookdev.setpoint.hydraulics.PipeRun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Follows the gas pipes from a port to every other gas port they reach: a gas line is a header, and gas can go to
 * anything on it.
 */
public final class GasRun {
    /**
     * @param pos    the block at the other end
     * @param face   its side the pipe comes in at
     * @param length gas pipes in between (1 block = 1 m)
     */
    public record End(BlockPos pos, Direction face, int length) {
        public @Nullable GasVessel vessel(Level level) {
            return GasPortBlock.vesselAt(level.getBlockEntity(pos), face);
        }
    }

    private GasRun() {}

    /** Every gas port reached from {@code from}, going out through {@code side}, nearest first (not {@code from} itself). */
    public static List<End> traceAll(Level level, BlockPos from, Direction side) {
        List<End> ends = new ArrayList<>();
        BlockPos first = from.relative(side);
        if (!level.isLoaded(first)) return ends;
        BlockState firstState = level.getBlockState(first);
        if (firstState.getBlock() instanceof GasPortBlock port && port.gasPort(firstState, side.getOpposite())) {
            ends.add(new End(first, side.getOpposite(), 0));
            return ends;
        }
        if (!(firstState.getBlock() instanceof GasPipeBlock) || !firstState.getValue(FluidPipeBlock.CONNECTIONS.get(side.getOpposite()))) return ends;
        Map<BlockPos, Integer> distance = new HashMap<>();
        distance.put(from, 0);
        distance.put(first, 1);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(first);
        while (!queue.isEmpty() && distance.size() <= PipeRun.MAX_PIPES) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);
            for (Direction direction : Direction.values()) {
                if (!currentState.getValue(FluidPipeBlock.CONNECTIONS.get(direction))) continue;
                BlockPos next = current.relative(direction);
                if (distance.containsKey(next) || !level.isLoaded(next)) continue;
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof GasPortBlock port && port.gasPort(state, direction.getOpposite())) {
                    ends.add(new End(next, direction.getOpposite(), distance.get(current)));
                } else if (state.getBlock() instanceof GasPipeBlock && state.getValue(FluidPipeBlock.CONNECTIONS.get(direction.getOpposite()))) {
                    distance.put(next, distance.get(current) + 1);
                    queue.add(next);
                }
            }
        }
        return ends;
    }
}
