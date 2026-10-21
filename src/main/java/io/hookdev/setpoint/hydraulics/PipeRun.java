package io.hookdev.setpoint.hydraulics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.hookdev.setpoint.block.ControlValveBlock;
import io.hookdev.setpoint.block.FlowTransmitterBlock;
import io.hookdev.setpoint.block.entity.ControlValveBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Traces a pipe run from one side of a pump to the nearest vessel (tank, boiler), through pipes and control valves. */
public final class PipeRun {
    /** Longer runs than this are not followed (keeps big networks cheap). */
    public static final int MAX_PIPES = 512;

    /**
     * @param tankPos where the run ends
     * @param length  number of pipe and valve blocks between the pump and the tank (1 block = 1 m)
     * @param valves  control valves on the way
     * @param meters  flow transmitters on the way
     * @param face    the side of the vessel the pipe comes in at (for other mods' tanks)
     */
    public record End(BlockPos tankPos, int length, List<BlockPos> valves, List<BlockPos> meters, Direction face) {
        /** Summed resistance of the valves on this run, infinite if one is shut. */
        public double valveResistance(Level level) {
            double k = 0;
            for (BlockPos pos : valves) {
                if (level.getBlockEntity(pos) instanceof ControlValveBlockEntity valve) k += valve.resistance();
            }
            return k;
        }
    }

    private PipeRun() {}

    /** Nearest vessel (ours or another mod's tank) reached from {@code pumpPos} going out through {@code side}, or null if there is none. */
    public static @Nullable End trace(Level level, BlockPos pumpPos, Direction side) {
        BlockPos first = pumpPos.relative(side);
        if (!level.isLoaded(first)) return null;
        BlockState firstState = level.getBlockState(first);
        if (firstState.getBlock() instanceof VesselBlock || ForeignVessel.isStorage(level, first, side.getOpposite())) {
            return new End(first, 0, List.of(), List.of(), side.getOpposite());
        }
        if (!Piping.enters(firstState, side)) return null;

        // Breadth first, so the first tank found is the closest one. Parents rebuild the path for the valves.
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        parent.put(first, pumpPos);
        parent.put(pumpPos, pumpPos);
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(first);
        while (!queue.isEmpty() && parent.size() <= MAX_PIPES) {
            BlockPos current = queue.poll();
            BlockState currentState = level.getBlockState(current);
            for (Direction direction : Direction.values()) {
                if (!Piping.leaves(currentState, direction)) continue;
                BlockPos next = current.relative(direction);
                if (parent.containsKey(next) || !level.isLoaded(next)) continue;
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof VesselBlock || ForeignVessel.isStorage(level, next, direction.getOpposite())) {
                    return finish(level, next, current, parent, direction.getOpposite());
                }
                if (Piping.enters(state, direction)) {
                    parent.put(next, current);
                    queue.add(next);
                }
            }
        }
        return null;
    }

    private static End finish(Level level, BlockPos tank, BlockPos last, Map<BlockPos, BlockPos> parent, Direction face) {
        List<BlockPos> valves = new ArrayList<>(), meters = new ArrayList<>();
        int length = 0;
        for (BlockPos pos = last; !parent.get(pos).equals(pos); pos = parent.get(pos)) {
            length++;
            if (level.getBlockState(pos).getBlock() instanceof ControlValveBlock) valves.add(pos);
            if (level.getBlockState(pos).getBlock() instanceof FlowTransmitterBlock) meters.add(pos);
        }
        return new End(tank, length, valves, meters, face);
    }
}
