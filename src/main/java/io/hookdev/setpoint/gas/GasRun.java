package io.hookdev.setpoint.gas;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.FluidPipeBlock;
import io.hookdev.setpoint.block.GasControlValveBlock;
import io.hookdev.setpoint.block.GasPipeBlock;
import io.hookdev.setpoint.hydraulics.PipeRun;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Follows the gas pipes from a port to every other gas port they reach: a gas line is a header, and gas can go to
 * anything on it. Gas valves in the line pass gas as far as they are open; a shut one splits the line.
 */
public final class GasRun {
    /**
     * @param pos    the block at the other end
     * @param face   its side the pipe comes in at
     * @param length gas pipes in between (1 block = 1 m)
     * @param valves the gas valves on the way
     */
    public record End(BlockPos pos, Direction face, int length, List<BlockPos> valves) {
        public @Nullable GasVessel vessel(Level level) {
            return GasPortBlock.vesselAt(level.getBlockEntity(pos), face);
        }

        /**
         * How much the valves on the way let gas through right now: 1 with none or all wide open, 0 with one shut. A
         * valve passes the square of its opening (like a real equal-percentage trim), so a little open is little.
         */
        public double open(Level level) {
            double open = 1;
            for (BlockPos valve : valves) {
                BlockState state = level.getBlockState(valve);
                double opening = state.getBlock() instanceof GasLine line ? line.opening(level, valve, state) : 0;
                open = Math.min(open, opening * opening);
            }
            return open;
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
            ends.add(new End(first, side.getOpposite(), 0, List.of()));
            return ends;
        }
        if (!enters(firstState, side)) return ends;
        walk(level, from, first, false, ends, null);
        return ends;
    }

    /**
     * Every gas port on the line a pipe (or gas valve) at {@code start} is part of, without going through a pressure
     * control valve: what a pressure transmitter on that pipe sees.
     */
    public static List<End> lineAt(Level level, BlockPos start) {
        List<End> ends = new ArrayList<>();
        if (isGasPiece(level.getBlockState(start))) walk(level, start, start, true, ends, null);
        ends.removeIf(end -> end.open(level) <= 0);
        return ends;
    }

    /** The pressure control valve nearest to {@code start} along its gas line, if any. */
    public static @Nullable BlockPos controlValveNear(Level level, BlockPos start) {
        BlockPos[] found = new BlockPos[1];
        if (isGasPiece(level.getBlockState(start))) walk(level, start, start, true, new ArrayList<>(), found);
        return found[0];
    }

    /** A gas pipe or something that sits in a gas line. */
    public static boolean isGasPiece(BlockState state) {
        return state.getBlock() instanceof GasPipeBlock || state.getBlock() instanceof GasLine;
    }

    /** Does a gas line going {@code direction} go on into this block? (Valves count, open or shut: they are looked at when gas flows.) */
    private static boolean enters(BlockState state, Direction direction) {
        if (state.getBlock() instanceof GasPipeBlock) return state.getValue(FluidPipeBlock.CONNECTIONS.get(direction.getOpposite()));
        return state.getBlock() instanceof GasLine line && line.joinsGas(state, direction.getOpposite());
    }

    private static boolean leaves(BlockState state, Direction direction) {
        if (state.getBlock() instanceof GasPipeBlock) return state.getValue(FluidPipeBlock.CONNECTIONS.get(direction));
        return state.getBlock() instanceof GasLine line && line.joinsGas(state, direction);
    }

    /**
     * Breadth first through the pipes from {@code first}. With {@code stopAtControl} a pressure control valve ends
     * the walk (and is put in {@code controlValve} when that is asked for).
     */
    private static void walk(Level level, BlockPos from, BlockPos first, boolean stopAtControl, List<End> ends, BlockPos @Nullable [] controlValve) {
        Map<BlockPos, Integer> distance = new HashMap<>();
        Map<BlockPos, List<BlockPos>> valves = new HashMap<>();
        distance.put(from, 0);
        distance.put(first, 1);
        valves.put(first, level.getBlockState(first).getBlock() instanceof GasLine && !first.equals(from) ? List.of(first) : List.of());
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(first);
        while (!queue.isEmpty() && distance.size() <= PipeRun.MAX_PIPES) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);
            for (Direction direction : Direction.values()) {
                if (!leaves(currentState, direction)) continue;
                BlockPos next = current.relative(direction);
                if (distance.containsKey(next) || !level.isLoaded(next)) continue;
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof GasPortBlock port && port.gasPort(state, direction.getOpposite())) {
                    ends.add(new End(next, direction.getOpposite(), distance.get(current), valves.get(current)));
                    continue;
                }
                if (stopAtControl && state.getBlock() instanceof GasControlValveBlock) {
                    if (controlValve != null && controlValve[0] == null) controlValve[0] = next;
                    distance.put(next, distance.get(current) + 1);
                    continue;
                }
                if (enters(state, direction)) {
                    distance.put(next, distance.get(current) + 1);
                    List<BlockPos> on = valves.get(current);
                    if (state.getBlock() instanceof GasLine) {
                        on = new ArrayList<>(on);
                        on.add(next);
                    }
                    valves.put(next, on);
                    queue.add(next);
                }
            }
        }
    }
}
